package sqsd

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{Behavior, DispatcherSelector, PreRestart, SupervisorStrategy}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{Found, OK}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream._
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import akka.stream.alpakka.sqs.{MessageAction, SqsSourceSettings}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, Sink, Source, Zip}
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Try

final class StreamWrapper(context: ActorContext[StreamWrapper.Command],
                          timers: TimerScheduler[StreamWrapper.Command]) {
  private implicit val mat: Materializer = Materializer(context)

  private implicit val system: ActorSystem = context.system.classicSystem

  private implicit val dispatcher: ExecutionContextExecutor =
    context.system.dispatchers.lookup(DispatcherSelector.fromConfig("http-dispatcher"))

  private implicit val client: SqsAsyncClient = SqsAsyncClient.builder()
    .credentialsProvider(DefaultCredentialsProvider.create())
    .region(Region.of(SqsSettings.region))
    .httpClient(AkkaHttpClient.builder().withActorSystem(context.system.classicSystem).build())
    .build()

  private val sqsSourceSettings = SqsSourceSettings()
    .withWaitTime(SqsSettings.waitTimeSeconds.seconds)
    .withMaxBufferSize(SqsSettings.workerConcurrency)
    .withMaxBatchSize(10)
  private val source = SqsSource(SqsSettings.queueURL, sqsSourceSettings)(client)
  private val sharedKillSwitch = KillSwitches.shared("x-sqsd-kill-switch")

  private val contentType = ContentType.parse(SqsSettings.workerHttpRequestContentType) match {
    case Right(content) => content
    case _ => ContentTypes.`application/json`
  }
  private val connectionSettings = ConnectionPoolSettings(SqsSettings.config).withIdleTimeout(SqsSettings.workerTimeout.seconds)

  private val host = SqsSettings.workerHttpHost
  private val protocol = SqsSettings.workerHttpProtocol
  private val port = SqsSettings.workerHttpPort

  private val httpFlow: Flow[(HttpRequest, String), (Try[HttpResponse], String), Http.HostConnectionPool] =
    if (protocol.startsWith("https")) {
      Http().cachedHostConnectionPoolHttps[String](host = host, port = port, settings = connectionSettings)
    } else {
      Http().cachedHostConnectionPool[String](host = host, port = port, settings = connectionSettings)
    }

  private val sqsMessageFlow: Flow[Message, (Message, MessageAction), NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val http = Flow[Message]
        .map { msg =>
          Source.single {
            HttpRequest(
              method = HttpMethods.POST,
              uri = SqsSettings.workerHttpPath,
              headers = List(`User-Agent`("x-sqsd/1.1"), RawHeader("X-Sqsd-Msgid", msg.messageId())),
              entity = HttpEntity(msg.body()).withContentType(contentType)) -> msg.body()
          }.via(httpFlow).runWith(Sink.head).map {
            case (util.Success(response), _) =>
              response.discardEntityBytes()
              response.status match {
                case OK => MessageAction.delete(msg)
                case _ => MessageAction.ignore(msg)
              }
            case (util.Failure(_), _) => MessageAction.ignore(msg)
          }
        }

      val balancer = builder.add(Balance[Message](SqsSettings.workerConcurrency))
      val merge = builder.add(Merge[(Message, MessageAction)](SqsSettings.workerConcurrency))

      for (i <- 0 until SqsSettings.workerConcurrency) {
        val broadcast = builder.add(Broadcast[Message](2))
        val zip = builder.add(Zip[Message, MessageAction]())

        balancer.out(i) ~> broadcast ~> zip.in0
        broadcast ~> http.mapAsync(1)(action => action) ~> zip.in1
        zip.out ~> merge
      }

      FlowShape(balancer.in, merge.out)
    })


  def prepare(): Behavior[StreamWrapper.Command] = {
    Behaviors.receiveMessage[StreamWrapper.Command] {
      case StreamWrapper.Start | StreamWrapper.HealthCheck =>
        context.log.info("Checking for application readiness")
        context.pipeToSelf(
          Http().singleRequest(HttpRequest(uri = Uri(SqsSettings.healthUrl)))
        ) {
          case util.Success(response) if response.status == OK || response.status == Found =>
            response.discardEntityBytes()
            timers.startSingleTimer(StreamWrapper.TimerKey, StreamWrapper.HealthCheck, SqsSettings.workerHealthWaitTime.seconds)
            StreamWrapper.Consume
          case util.Success(response) =>
            response.discardEntityBytes()
            context.log.info(s"Application on ${SqsSettings.workerHealthUrl} isn't ready.")
            StreamWrapper.HealthCheck
          case _ =>
            context.log.info(s"Application on ${SqsSettings.workerHealthUrl} isn't ready.")
            StreamWrapper.HealthCheck
        }
        Behaviors.same
      case StreamWrapper.Consume =>
        context.log.info("Application Ok. Starting consuming!!!")
        context.pipeToSelf(
          source
            .via(sharedKillSwitch.flow)
            .via(sqsMessageFlow)
            .map { case (_, action) => action }
            .runWith(SqsAckSink(SqsSettings.queueURL))
        ) {
          case util.Failure(e) =>
            context.log.error("Stream process failed. Shutting down service.", e)
            StreamWrapper.Stop
          case _ =>
            StreamWrapper.HealthCheck
        }
        Behaviors.same
      case StreamWrapper.Stop =>
        Behaviors.stopped
    }.receiveSignal {
      case (_, PreRestart) =>
        context.log.info(s"Starting actor stream for SQS ${SqsSettings.queueURL}")
        context.self ! StreamWrapper.Start
        Behaviors.same
    }
  }

}


object StreamWrapper {

  sealed trait Command

  case object Start extends Command

  case object Stop extends Command

  case object Consume extends Command

  case object HealthCheck extends Command

  case object TimerKey

  def apply(): Behavior[Command] = {

    Behaviors
      .supervise {
        Behaviors.setup[Command] { ctx =>
          Behaviors.withTimers[Command] { timers =>
            new StreamWrapper(ctx, timers).prepare()
          }
        }
      }
      .onFailure[Throwable](
        SupervisorStrategy.restartWithBackoff(3.seconds, 1.minute, 0.2)
      )
  }

}

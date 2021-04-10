import akka.actor.typed.ActorSystem
import sqsd.{SqsSettings, StreamWrapper}

object Boot extends App {

  require(Option(System.getenv("AWS_ACCESS_KEY_ID")).nonEmpty)
  require(Option(System.getenv("AWS_SECRET_ACCESS_KEY")).nonEmpty)
  require(SqsSettings.queueURL.nonEmpty)
  require(SqsSettings.httpUrl.nonEmpty)
  require(SqsSettings.healthUrl.nonEmpty)
  System.setProperty("aws.accessKeyId", System.getenv("AWS_ACCESS_KEY_ID"))
  System.setProperty("aws.secretAccessKey", System.getenv("AWS_SECRET_ACCESS_KEY"))
  ActorSystem(StreamWrapper(), "x-sqsd") ! StreamWrapper.Start

}

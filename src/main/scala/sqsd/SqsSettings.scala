package sqsd

import com.typesafe.config.ConfigFactory

import java.net.URL

object SqsSettings {
  val config = ConfigFactory.load()

  val region = config.getString("app.region")

  val waitTimeSeconds = config.getInt("app.waitTimeSeconds")
  val queueURL = config.getString("app.queueURL")

  val httpUrl = config.getString("app.workerHttpUrl")
  val workerHttpUrl = new URL(httpUrl)
  val workerHttpProtocol = workerHttpUrl.getProtocol
  val workerHttpHost = workerHttpUrl.getHost
  val workerHttpPort = workerHttpUrl.getPort
  val workerHttpPath = workerHttpUrl.getPath
  val workerTimeout = config.getInt("app.workerTimeout")
  val workerConcurrency = config.getInt("app.workerConcurrency")
  val workerHttpRequestContentType = config.getString("app.workerHttpRequestContentType")

  val healthUrl = config.getString("app.workerHealthUrl")
  val workerHealthUrl = new URL(healthUrl)
  val workerHealthProtocol = workerHealthUrl.getProtocol
  val workerHealthHost = workerHealthUrl.getHost
  val workerHealthPort = workerHealthUrl.getPort
  val workerHealthPath = workerHealthUrl.getPath
  val workerHealthWaitTime = config.getInt("app.workerHealthWaitTime")


}

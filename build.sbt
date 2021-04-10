
maintainer := "nlin@xtiva.com"

name := "x-sqsd"

version := "1.0"

scalaVersion := "2.13.4"
scalacOptions += "-deprecation"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

Compile / run / fork := true

enablePlugins(JavaAppPackaging)

lazy val common = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.16",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
)

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-actor",
  "com.typesafe.akka" %% "akka-actor-typed",
  "com.typesafe.akka" %% "akka-slf4j",
  "com.typesafe.akka" %% "akka-stream",
).map( _ % "2.6.14")

lazy val akkaHttp = Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.4"
)

lazy val sqs = Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "2.0.2",
  "software.amazon.awssdk" % "sqs" % "2.16.31",
)

libraryDependencies ++= common ++ akka ++ akkaHttp ++ sqs

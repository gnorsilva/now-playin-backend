name := "now-playin-backend"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.1",
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "io.lemonlabs" %% "scala-uri" % "1.1.1",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "org.reactivemongo" %% "reactivemongo" % "0.13.0",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.mockito" % "mockito-core" % "2.18.3" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % Test
)

testOptions in Test += Tests.Argument("-oF")
fork in Test := true

enablePlugins(JavaAppPackaging)
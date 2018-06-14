package com.gnorsilva.nowplayin.backend

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

class ApiSpec extends FreeSpec with Matchers with BeforeAndAfterAll with OneInstancePerTest
  with ScalaFutures with IntegrationPatience {

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  lazy val server = new Server()

  override protected def beforeAll(): Unit = {
    super.beforeAll
    server.start
  }

  override protected def afterAll {
    super.afterAll
    materializer.shutdown()
    system.terminate().futureValue
    server.stop
  }

  "The Api should reply with a placeholder response when a request is made" in {
    val responseFuture = Http().singleRequest(HttpRequest(uri = "http://localhost:8080/"))
    val response = responseFuture.futureValue

    val responseString = response.entity
      .dataBytes
      .map(_.utf8String)
      .runWith(Sink.head)
      .futureValue

    responseString shouldBe "Nothing to see here, move along..."
  }
}

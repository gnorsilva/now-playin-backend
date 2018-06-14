package com.gnorsilva.nowplayin.backend

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Framing, Source}
import akka.util.ByteString
import com.gnorsilva.nowplayin.backend.helpers.MongoDbAccess
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


class StreamConsumptionIntegrationSpec extends FreeSpec with Matchers with BeforeAndAfterAll
  with Eventually with IntegrationPatience with ScalaFutures with MongoDbAccess {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  lazy val server = new Server(testDbConfig)

  override protected def afterAll(): Unit = {
    super.afterAll
    materializer.shutdown()
    system.terminate().futureValue
    server.stop
  }

  "Incoming streamed messages are parsed and inserted into database" in {
    setupSingleRunStreamingServer

    server.start

    eventually {
      val expectedValues = List(
        BSONDocument("artistName" -> "Bryan Adams"),
        BSONDocument("artistName" -> "Bryan Adams"),
        BSONDocument("artistName" -> "Chris Craig"),
        BSONDocument("artistName" -> "Daryl Hall & John Oates"),
        BSONDocument("artistName" -> "Felice Messam Ft. Canton Jones"),
        BSONDocument("artistName" -> "Os Paralamas Do Sucesso"),
        BSONDocument("artistName" -> "Pedro Blaze"),
        BSONDocument("artistName" -> "Pointer Sisters"),
        BSONDocument("artistName" -> "The Predatorz"),
        BSONDocument("artistName" -> "Unspoken")
      ).map(BSONDocument.pretty)

      artistPlaysStoredInMongo.map(BSONDocument.pretty) shouldBe expectedValues
    }
  }

  def setupSingleRunStreamingServer = {
    val messages = getClass.getResource("/twitter_api_messages").toURI
    val messagesSource = FileIO.fromPath(Paths.get(messages))
      .via(Framing.delimiter(ByteString("\n"), Int.MaxValue).map(_ ++ ByteString("\r\n")))
      .throttle(1, 100 milliseconds)

    val emptyInfiniteStream =
      Source.fromIterator(() => Iterator.continually(ByteString("\r\n")))
        .throttle(1, 100 milliseconds)

    var streamed = false
    val route =
      path("1.1" / "statuses" / "filter.json") {
        post {
          if (!streamed) {
            streamed = true
            complete(HttpEntity(ContentTypes.`application/json`, messagesSource))
          } else {
            complete(HttpEntity(ContentTypes.`application/json`, emptyInfiniteStream))
          }
        }
      }

    lazy val futureBinding = Http().bindAndHandle(handler = route, interface = "localhost", port = 8090)

    futureBinding.futureValue
  }

  def artistPlaysStoredInMongo: List[BSONDocument] = {
    val collection = mongoDb.collection[BSONCollection]("artistplays")

    val findAll = collection
      .genericQueryBuilder
      .projection(BSONDocument("artistName" -> 1, "_id" -> 0))
      .sort(BSONDocument("artistName" -> 1))
      .cursor[BSONDocument]()
      .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())

    findAll.futureValue
  }

}

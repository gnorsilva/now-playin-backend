package com.gnorsilva.nowplayin.backend

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.helpers.MongoDbAccess
import org.scalatest._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import reactivemongo.api.collections.bson.BSONCollection


class ApiSpec extends FreeSpec with Matchers with BeforeAndAfterAll with OneInstancePerTest
  with ScalaFutures with IntegrationPatience with Eventually with MongoDbAccess {

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  lazy val server = new Server(testDbConfig)
  lazy val artistPlays = mongoDb.collection[BSONCollection]("artistplays")

  val Now = Instant.now()
  val Yesterday = Now.minus(1, DAYS)

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

  "Root replies with an empty 200" in {
    val response = Http().singleRequest(HttpRequest(uri = "http://localhost:8080/")).futureValue

    response.status shouldBe StatusCodes.OK

    val responseString = response.entity
      .dataBytes
      .map(_.utf8String)
      .runWith(Sink.head)
      .futureValue

    responseString shouldBe empty
  }

  "Streaming api provides play counts in Json via SSE" in {
    setupInitialData()

    var lastStreamedData: String = ""
    var lastStreamEventType: Option[String] = None

    Http().singleRequest(HttpRequest(uri = "http://localhost:8080/stream"))
      .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      .foreach(_.runForeach { event =>
        lastStreamedData = event.data
        lastStreamEventType = event.eventType
      })

    import spray.json._

    eventually {
      lastStreamedData.parseJson.prettyPrint shouldBe FirstBatch
      lastStreamEventType shouldBe Some("nowplayin")
    }

    val insertNewData = artistPlays.insert[ArtistPlay](false).many(Seq(
      ArtistPlay("Tally Ho", Now),
      ArtistPlay("Jung Fung", Now),
      ArtistPlay("Jung Fung", Now),
      ArtistPlay("Jung Fung", Now),
      ArtistPlay("Lakshmi Bing", Now),
      ArtistPlay("Sam Jones", Now)
    ))
    insertNewData.futureValue.ok shouldBe true

    eventually {
      lastStreamedData.parseJson.prettyPrint shouldBe SecondBatch
      lastStreamEventType shouldBe Some("nowplayin")
    }
  }

  private def setupInitialData() = {
    val insertPreviousData = artistPlays.insert[ArtistPlay](ordered = false).many(Seq(
      // Yesterday's plays
      ArtistPlay("Sam Jones", Yesterday),
      ArtistPlay("Sam Jones", Yesterday),
      ArtistPlay("Sam Jones", Yesterday),
      ArtistPlay("Tim Rogers", Yesterday),
      ArtistPlay("John McDoe", Yesterday),
      ArtistPlay("John McDoe", Yesterday),
      ArtistPlay("Billy Bin", Yesterday),
      ArtistPlay("Billy Bin", Yesterday),

      // Now
      ArtistPlay("Sam Jones", Now),
      ArtistPlay("John McDoe", Now),
      ArtistPlay("Jung Fung", Now),
      ArtistPlay("Blue Slime", Now)
    ))
    insertPreviousData.futureValue.ok shouldBe true
  }

  val FirstBatch =
    """{
      |  "monthlyTop10": [{
      |    "artistName": "Sam Jones",
      |    "count": 4
      |  }, {
      |    "artistName": "John McDoe",
      |    "count": 3
      |  }, {
      |    "artistName": "Billy Bin",
      |    "count": 2
      |  }, {
      |    "artistName": "Jung Fung",
      |    "count": 1
      |  }, {
      |    "artistName": "Blue Slime",
      |    "count": 1
      |  }, {
      |    "artistName": "Tim Rogers",
      |    "count": 1
      |  }],
      |  "last5MinutesTop10": [{
      |    "artistName": "Jung Fung",
      |    "count": 1
      |  }, {
      |    "artistName": "Blue Slime",
      |    "count": 1
      |  }, {
      |    "artistName": "John McDoe",
      |    "count": 1
      |  }, {
      |    "artistName": "Sam Jones",
      |    "count": 1
      |  }],
      |  "lastPlayed": "Blue Slime"
      |}""".stripMargin

  val SecondBatch =
    """{
      |  "monthlyTop10": [{
      |    "artistName": "Sam Jones",
      |    "count": 5
      |  }, {
      |    "artistName": "Jung Fung",
      |    "count": 4
      |  }, {
      |    "artistName": "John McDoe",
      |    "count": 3
      |  }, {
      |    "artistName": "Billy Bin",
      |    "count": 2
      |  }, {
      |    "artistName": "Lakshmi Bing",
      |    "count": 1
      |  }, {
      |    "artistName": "Tally Ho",
      |    "count": 1
      |  }, {
      |    "artistName": "Blue Slime",
      |    "count": 1
      |  }, {
      |    "artistName": "Tim Rogers",
      |    "count": 1
      |  }],
      |  "last5MinutesTop10": [{
      |    "artistName": "Jung Fung",
      |    "count": 4
      |  }, {
      |    "artistName": "Sam Jones",
      |    "count": 2
      |  }, {
      |    "artistName": "Lakshmi Bing",
      |    "count": 1
      |  }, {
      |    "artistName": "Tally Ho",
      |    "count": 1
      |  }, {
      |    "artistName": "John McDoe",
      |    "count": 1
      |  }, {
      |    "artistName": "Blue Slime",
      |    "count": 1
      |  }],
      |  "lastPlayed": "Sam Jones"
      |}""".stripMargin

}

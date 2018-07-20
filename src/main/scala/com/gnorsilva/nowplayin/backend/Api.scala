package com.gnorsilva.nowplayin.backend

import java.time.Instant
import java.time.temporal.ChronoUnit.MINUTES

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{BroadcastHub, Keep, Source}
import com.gnorsilva.nowplayin.backend.Api._
import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.PlayCount
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object Api {

  private val EventType = "nowplayin"

  case class PlayedArtists(monthlyTop10: Seq[PlayCount],
                           last5MinutesTop10: Seq[PlayCount],
                           lastPlayed: Option[String])

  object PlayCountJsonProtocol extends DefaultJsonProtocol {
    implicit val playCountFormat = jsonFormat2(PlayCount)
  }

  object PlayedArtistsJsonProtocol extends DefaultJsonProtocol {
    import PlayCountJsonProtocol._

    implicit val playedArtistsFormat = jsonFormat3(PlayedArtists)
  }

}

class Api(interface: String, port: Int, streamInterval: FiniteDuration, artistPlaysRepository: ArtistPlaysRepository)
         (implicit system: ActorSystem, materializer: ActorMaterializer) {

  def start(): Future[Http.ServerBinding] = {
    Http().bindAndHandle(routes, interface, port)
  }

  private val routes: Route = {
    path("") {
      get {
        complete(HttpEntity.Empty)
      }
    } ~ path("stream") {
      get {
        complete {
          dataStream
        }
      }
    }
  }

  import PlayedArtistsJsonProtocol._

  private val dataStream = Source
    .tick(1.seconds, streamInterval, NotUsed)
    .mapAsync(1)(_ => playedArtists())
    .map(artists => ServerSentEvent(artists.toJson.compactPrint, EventType))
    .keepAlive(15.second, () => ServerSentEvent.heartbeat)
    .toMat(BroadcastHub.sink(bufferSize = 1))(Keep.right)
    .run()

  private def playedArtists(): Future[PlayedArtists] = {
    for {
      monthlyTop10 <- artistPlaysRepository.topPlayCount()
      top10Last5Minutes <- artistPlaysRepository.topPlayCount(time = Some(fiveMinutesAgo))
      someArtist <- artistPlaysRepository.lastPlayedArtist()
    } yield PlayedArtists(monthlyTop10, top10Last5Minutes, someArtist.map(_.artistName))
  }

  private def fiveMinutesAgo: Instant = Instant.now.minus(5, MINUTES)

}

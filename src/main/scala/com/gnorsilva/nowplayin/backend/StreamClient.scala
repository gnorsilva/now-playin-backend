package com.gnorsilva.nowplayin.backend

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Framing
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import com.gnorsilva.nowplayin.backend.StreamClient.{ConnectToStream, StreamMessage}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object StreamClient {
  case class ConnectToStream()

  case class StreamMessage(content: String)
}

class StreamClient(twitterOAuth: OAuthString, streamProcessor: ActorRef, serverHost: String) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)

  override def preStart() = {
    connectToStream
  }

  def connectToStream = {
    log.debug("attempting to connectToStream")

    val uri = s"$serverHost/1.1/statuses/filter.json"
    val method = POST
    val params = Map("track" -> "now playing by, now playing -")

    val authString = twitterOAuth.create(baseUrl = uri, httpMethod = method.value, parameters = params)

    val authorization = HttpHeader.parse("Authorization", authString) match {
      case Ok(header, _) => header
    }

    val request = HttpRequest(
      uri = uri,
      method = method,
      headers = immutable.Seq(authorization),
      entity = FormData(params).toEntity(HttpCharsets.`UTF-8`)
    )

    http.singleRequest(request).pipeTo(self)
  }

  def receive = {

    case ConnectToStream() =>
      connectToStream

    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      log.debug("Connected, receiving data")
      handleDataStream(entity)

    case HttpResponse(statusCode, headers, entity, _) =>
      log.debug(s"$statusCode => $headers")
      entity.discardBytes _
      context.system.scheduler.scheduleOnce(10.seconds, self, ConnectToStream())

    case Failure(_) =>
      log.debug("Failure")
      context.system.scheduler.scheduleOnce(10.seconds, self, ConnectToStream())

  }

  private def handleDataStream(entity: ResponseEntity) = {
    entity.withoutSizeLimit.dataBytes
      .via(Framing.delimiter(ByteString("\r\n"), Int.MaxValue).async)
      .map(_.utf8String.trim)
      .filter(_.nonEmpty)
      .runForeach(streamProcessor ! StreamMessage(_))
      .onComplete {
        case Success(done) =>
          log.debug(s"onComplete success: $done")
          connectToStream

        case Failure(done) =>
          log.debug(s"onComplete failure :( : $done")
          connectToStream
      }
  }
}

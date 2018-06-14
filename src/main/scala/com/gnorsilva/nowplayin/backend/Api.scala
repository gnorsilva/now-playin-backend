package com.gnorsilva.nowplayin.backend

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.concurrent.Future

object Api {

  lazy val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(_, _, _, _, _) =>
      HttpResponse(entity = "Nothing to see here, move along...")
  }

  def apply(interface: String, port: Int)(implicit system: ActorSystem): Future[Http.ServerBinding] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system))

    Http().bindAndHandleSync(requestHandler, interface, port)
  }

}

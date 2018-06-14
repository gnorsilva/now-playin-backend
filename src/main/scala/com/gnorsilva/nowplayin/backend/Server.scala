package com.gnorsilva.nowplayin.backend

import akka.actor.{ActorSystem, Props}
import com.gnorsilva.nowplayin.backend.MongoDbFactory.MongoDbConfig
import com.gnorsilva.nowplayin.backend.OAuthString.{Clock, Noncer, OAuthSecrets}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object Server extends App {
  val server = new Server()
  server.start
  sys.addShutdownHook({
    server.stop
  })
}

class Server(dbConfig: MongoDbConfig = MongoDbConfig()) {

  private val timeout = 10.seconds

  private val appConf = ConfigFactory.load()

  implicit val system: ActorSystem = ActorSystem()

  private val oAuthData = OAuthSecrets(
    consumerKey = appConf.getString("now-playin.twitter.oauth.consumer-key"),
    consumerSecret = appConf.getString("now-playin.twitter.oauth.consumer-secret"),
    token = appConf.getString("now-playin.twitter.oauth.token"),
    tokenSecret = appConf.getString("now-playin.twitter.oauth.token-secret"))

  def start: Unit = {
    val mongoDb = MongoDbFactory.loadMongoDb(dbConfig)
    val repository = ArtistPlaysRepository(mongoDb, dbConfig)

    val oAuth = new OAuthString(Noncer, Clock, oAuthData)
    val streamProcessor = system.actorOf(Props(classOf[StreamProcessor], repository))
    val twitterApiUrl = appConf.getString("now-playin.twitter.api.url")
    system.actorOf(Props(classOf[StreamClient], oAuth, streamProcessor, twitterApiUrl))

    val apiInterface = appConf.getString("now-playin.api.interface")
    val apiPort = appConf.getInt("now-playin.api.port")
    val apiBinding = Api(apiInterface, apiPort)
    Await.result(apiBinding, timeout)
  }

  def stop: Unit = {
    Await.result(system.terminate(), timeout)
  }

}

package com.gnorsilva.nowplayin.backend

import akka.actor.{ActorSystem, Props}
import com.gnorsilva.nowplayin.backend.MongoDbFactory.MongoDbConfig
import com.gnorsilva.nowplayin.backend.OAuthString.{Clock, Noncer, OAuthSecrets}
import com.typesafe.config.ConfigFactory

object Server extends App {
  new Server().start
}

class Server(dbConfig: MongoDbConfig = MongoDbConfig()) {

  private val appConf = ConfigFactory.load()

  private val oAuthData = OAuthSecrets(
    consumerKey = appConf.getString("now-playin.twitter.oauth.consumer-key"),
    consumerSecret = appConf.getString("now-playin.twitter.oauth.consumer-secret"),
    token = appConf.getString("now-playin.twitter.oauth.token"),
    tokenSecret = appConf.getString("now-playin.twitter.oauth.token-secret"))

  def start = {
    val system = ActorSystem()
    val mongoDb = MongoDbFactory.loadMongoDb(dbConfig)
    val repository = ArtistPlaysRepository(mongoDb, dbConfig)
    val oAuth = new OAuthString(Noncer, Clock, oAuthData)
    val streamProcessor = system.actorOf(Props(classOf[StreamProcessor], repository))
    val twitterApiUrl = appConf.getString("now-playin.twitter.api.url")
    system.actorOf(Props(classOf[StreamClient], oAuth, streamProcessor, twitterApiUrl))
  }

}

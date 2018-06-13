package com.gnorsilva.nowplayin.backend

import java.time.Duration

import com.typesafe.config.ConfigFactory
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Success

object MongoDbFactory {

  private val appConf = ConfigFactory.load()

  private val setupTimeout = 10.seconds

  def loadMongoDb(dbConfig: MongoDbConfig): DefaultDB = {
    val parsedUri: MongoConnection.ParsedURI = MongoConnection.parseURI(dbConfig.uri) match {
      case Success(uri) => uri
      case _ => throw new IllegalArgumentException(s"Unable to parse mongo uri: ${dbConfig.uri}")
    }

    val mongoDriver = new MongoDriver
    val connection = mongoDriver.connection(parsedUri)

    val dbName = dbConfig.name.getOrElse(parsedUri.db match {
      case Some(name) => name
      case _ => throw new IllegalArgumentException(s"No database specified in uri ${dbConfig.uri}")
    })

    Await.result(connection.database(dbName), setupTimeout)
  }

  case class MongoDbConfig(uri: String, name: Option[String], ttl: Duration)

  object MongoDbConfig {

    def apply(dbName: Option[String] = None): MongoDbConfig = {
      val mongoUri = appConf.getString("now-playin.mongo.uri")
      val ttl = appConf.getDuration("now-playin.mongo.ttl.artist-plays")
      MongoDbConfig(mongoUri, dbName, ttl)
    }
  }
}

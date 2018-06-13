package com.gnorsilva.nowplayin.backend

import java.time.Duration
import java.util.Date

import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.MongoDbFactory.MongoDbConfig
import reactivemongo.api.DefaultDB
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, Macros}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ArtistPlaysRepository {

  private val setupTimeout = 10.seconds

  def apply(mongoDb: DefaultDB, dBConfig: MongoDbConfig): ArtistPlaysRepository = {
    val repository = new ArtistPlaysRepository(mongoDb)
    val repositorySetup = repository.setup(dBConfig.ttl)
    Await.result(repositorySetup, setupTimeout)
    repository
  }

  case class ArtistPlay(artistName: String, playedDate: Date)

  object ArtistPlay {
    implicit val songPlayReader: BSONDocumentReader[ArtistPlay] = Macros.reader[ArtistPlay]
    implicit val songPlayWriter: BSONDocumentWriter[ArtistPlay] = Macros.writer[ArtistPlay]
  }
}

class ArtistPlaysRepository private (db: DefaultDB) {

  private val ARTISTPLAYS = "artistplays"

  private val collection: BSONCollection = db.collection[BSONCollection](ARTISTPLAYS)

  private def setup(expire: Duration): Future[Boolean] = {
    val timeToLive = Index(
      key = List("playedDate" -> IndexType.Ascending),
      options = BSONDocument("expireAfterSeconds" -> expire.getSeconds))

    collection.create().transformWith {
      _ => collection.indexesManager.ensure(timeToLive)
    }
  }

  def insertArtistPlay(artistPlay: ArtistPlay): Future[WriteResult] = {
    collection.insert[ArtistPlay](artistPlay)
  }
}

package com.gnorsilva.nowplayin.backend

import java.time.{Duration, Instant}

import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.{ArtistPlay, PlayCount}
import com.gnorsilva.nowplayin.backend.MongoDbFactory.MongoDbConfig
import reactivemongo.api.collections.bson.BSONBatchCommands.AggregationFramework._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DefaultDB}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONNumberLike, BSONString, Macros}

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

  case class ArtistPlay(artistName: String, playedDate: Instant)

  object ArtistPlay {
    implicit val songPlayReader: BSONDocumentReader[ArtistPlay] = Macros.reader[ArtistPlay]
    implicit val songPlayWriter: BSONDocumentWriter[ArtistPlay] = Macros.writer[ArtistPlay]
  }

  case class PlayCount(artistName: String, count: Int)

  implicit object PlayCountReader extends BSONDocumentReader[PlayCount] {
    def read(bson: BSONDocument): PlayCount = {
      val opt: Option[PlayCount] = for {
        artistName <- bson.getAs[String]("_id")
        count <- bson.getAs[BSONNumberLike]("count").map(_.toInt)
      } yield new PlayCount(artistName, count)

      opt.get
    }
  }

}

class ArtistPlaysRepository private(db: DefaultDB) {

  private val ArtistPlays = "artistplays"

  private val collection: BSONCollection = db.collection[BSONCollection](ArtistPlays)

  private def setup(expire: Duration): Future[Boolean] = {
    val timeToLive = Index(
      key = List("playedDate" -> IndexType.Ascending),
      options = BSONDocument("expireAfterSeconds" -> expire.getSeconds))

    val artistName = Index(key = List("artistName" -> IndexType.Ascending))

    collection.create().transformWith { _ =>
      for {
        first <- collection.indexesManager.ensure(timeToLive)
        second <- collection.indexesManager.ensure(artistName)
      } yield first && second
    }
  }

  def insertArtistPlay(artistPlay: ArtistPlay): Future[WriteResult] = {
    collection.insert[ArtistPlay](artistPlay)
  }

  def topPlayCount(numberOfArtists: Int = 10, time: Option[Instant] = None): Future[List[PlayCount]] = {
    val (firstOperator, otherOperators) = playCountOperators(numberOfArtists, time)

    collection.aggregatorContext[PlayCount](firstOperator, otherOperators)
      .prepared
      .cursor
      .collect[List](-1, Cursor.FailOnError[List[PlayCount]]())
  }

  private def playCountOperators(numberOfArtists: Int, time: Option[Instant]) = {
    val group = Group(BSONString("$artistName"))("count" -> SumValue(1))
    val sort = Sort(Descending("count"))
    val limit = Limit(numberOfArtists)

    time match {
      case Some(instant) => (
        Match(BSONDocument("playedDate" -> BSONDocument("$gt" -> instant))),
        List(group, sort, limit)
      )
      case _ => (
        group,
        List(sort, limit)
      )
    }
  }

  def lastPlayedArtist(): Future[Option[ArtistPlay]] = {
    collection
      .find(BSONDocument.empty)
      .sort(BSONDocument("_id" -> -1))
      .one[ArtistPlay]
  }

  def mostPlayed(): Future[List[BSONDocument]] = {
    val group = Group(BSONString("$artistName"))("count" -> SumValue(1))
    val list = List(Sort(Descending("count")), Limit(5))

    collection.aggregatorContext[BSONDocument](group, list)
      .prepared
      .cursor
      .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
  }
}

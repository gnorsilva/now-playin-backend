package com.gnorsilva.nowplayin.backend

import java.util.Date

import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.helpers.MongoDbAccess
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, Matchers}
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONLong}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ArtistPlayRepositorySpec extends FreeSpec with Matchers with ScalaFutures
  with MongoDbAccess with Eventually with IntegrationPatience {

  "ArtistPlaysRepository" - {

    "Saves a play with an artist's name and played date" in {
      val repository = ArtistPlaysRepository(mongoDb, testDbConfig)

      val date = new Date

      repository.insertArtistPlay(ArtistPlay("David Bowie", date)).futureValue
      repository.insertArtistPlay(ArtistPlay("Miles Davis", date)).futureValue

      val collection = mongoDb.collection[BSONCollection]("artistplays")

      val find = collection
        .genericQueryBuilder
        .projection(BSONDocument("artistName" -> 1, "playedDate" -> 1, "_id" -> 0))
        .sort(BSONDocument("artistName" -> 1))
        .cursor[BSONDocument]()
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())

      find.futureValue shouldBe List(
        BSONDocument("artistName" -> "David Bowie", "playedDate" -> BSONDateTime(date.getTime)),
        BSONDocument("artistName" -> "Miles Davis", "playedDate" -> BSONDateTime(date.getTime))
      )
    }

    "is able to create a ttl index on load" - {
      "when the collection does not already exist" in {
        val collection = mongoDb.collection[BSONCollection]("artistplays")
        collection.drop(false).futureValue

        ArtistPlaysRepository(mongoDb, testDbConfig)
        
        val list = collection.indexesManager.list().futureValue
        list.head.key shouldBe List("playedDate" -> IndexType.Ascending)
        list.head.options shouldBe BSONDocument("expireAfterSeconds" -> BSONLong((31 days).toSeconds))
      }

      "when the collection already exists" in {
        val collection = mongoDb.collection[BSONCollection]("artistplays")
        collection.insert[ArtistPlay](ArtistPlay("David Bowie", new Date)).futureValue

        ArtistPlaysRepository(mongoDb, testDbConfig)

        val list = collection.indexesManager.list().futureValue
        list.head.key shouldBe List("playedDate" -> IndexType.Ascending)
        list.head.options shouldBe BSONDocument("expireAfterSeconds" -> BSONLong((31 days).toSeconds))
      }
    }
  }
}

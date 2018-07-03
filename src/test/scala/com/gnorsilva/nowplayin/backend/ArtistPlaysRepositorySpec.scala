package com.gnorsilva.nowplayin.backend

import java.time.Instant
import java.time.temporal.ChronoUnit.{DAYS, MINUTES}

import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.{ArtistPlay, PlayCount}
import com.gnorsilva.nowplayin.backend.helpers.MongoDbAccess
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, Matchers, OneInstancePerTest}
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONLong}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ArtistPlaysRepositorySpec extends FreeSpec with Matchers with ScalaFutures
  with MongoDbAccess with Eventually with IntegrationPatience with OneInstancePerTest {

  lazy val repository = ArtistPlaysRepository(mongoDb, testDbConfig)
  lazy val Collection = mongoDb.collection[BSONCollection]("artistplays")
  lazy val Now = Instant.now()

  "ArtistPlaysRepository" - {

    "Saves a play with an artist's name and played date" in {
      repository.insertArtistPlay(ArtistPlay("David Bowie", Now)).futureValue
      repository.insertArtistPlay(ArtistPlay("Miles Davis", Now)).futureValue

      val find = Collection
        .genericQueryBuilder
        .projection(BSONDocument("artistName" -> 1, "playedDate" -> 1, "_id" -> 0))
        .sort(BSONDocument("artistName" -> 1))
        .cursor[BSONDocument]()
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())

      find.futureValue shouldBe List(
        BSONDocument("artistName" -> "David Bowie", "playedDate" -> BSONDateTime(Now.toEpochMilli)),
        BSONDocument("artistName" -> "Miles Davis", "playedDate" -> BSONDateTime(Now.toEpochMilli))
      )
    }

    "returns the latest played artist" in {
      Collection.insert(ArtistPlay("David Bowie", Now)).futureValue
      Collection.insert(ArtistPlay("Miles Davis", Now)).futureValue

      repository.lastPlayedArtist().futureValue shouldBe Some(ArtistPlay("Miles Davis", Now))
    }

    "returns the top played artists" - {
      val Yesterday = Now.minus(1, DAYS)

      "10 by default and for all time" in {
        Collection.insert[ArtistPlay](ordered = false).many(Seq(
          ArtistPlay("David Bowie", Yesterday),
          ArtistPlay("David Bowie", Yesterday),
          ArtistPlay("Beatles", Yesterday),
          ArtistPlay("Beatles", Yesterday),
          ArtistPlay("Beatles", Yesterday),
          ArtistPlay("Beatles", Yesterday),
          ArtistPlay("Kraftwerk", Yesterday),
          ArtistPlay("Alicia Keys", Yesterday),
          ArtistPlay("Pink Floyd", Yesterday),
          ArtistPlay("Pink Floyd", Yesterday),
          ArtistPlay("Jimi Hendrix", Yesterday),
          ArtistPlay("Prodigy", Yesterday),
          ArtistPlay("Sepultura", Yesterday),
          ArtistPlay("Metallica", Yesterday),
          ArtistPlay("Metallica", Yesterday),
          ArtistPlay("Metallica", Yesterday),
          ArtistPlay("Marvin Gaye", Yesterday),
          ArtistPlay("John Coltrane", Yesterday),
          ArtistPlay("John Coltrane", Yesterday),
          ArtistPlay("John Coltrane", Yesterday),
          ArtistPlay("Air", Yesterday),
          ArtistPlay("Air", Yesterday),
          ArtistPlay("Air", Yesterday)
        )).futureValue

        val expected = List(
          PlayCount("Beatles", 4),
          PlayCount("Metallica", 3),
          PlayCount("John Coltrane", 3),
          PlayCount("Air", 3),
          PlayCount("David Bowie", 2),
          PlayCount("Pink Floyd", 2),
          PlayCount("Prodigy", 1),
          PlayCount("Jimi Hendrix", 1),
          PlayCount("Alicia Keys", 1),
          PlayCount("Marvin Gaye", 1)
        )

        repository.topPlayCount().futureValue shouldBe expected
      }

      "specified by amount and time" in {
        Collection.insert[ArtistPlay](ordered = false).many(Seq(
          ArtistPlay("David Bowie", Yesterday),
          ArtistPlay("Beatles", Yesterday),
          ArtistPlay("Kraftwerk", Now.minus(6, MINUTES)),
          ArtistPlay("Alicia Keys", Now.minus(6, MINUTES)),

          ArtistPlay("Beatles", Now.minus(4, MINUTES)),
          ArtistPlay("Beatles", Now.minus(4, MINUTES)),
          ArtistPlay("Beatles", Now.minus(4, MINUTES)),
          ArtistPlay("Pink Floyd", Now.minus(4, MINUTES)),
          ArtistPlay("Pink Floyd", Now.minus(4, MINUTES)),
          ArtistPlay("Jimi Hendrix", Now.minus(4, MINUTES)),
          ArtistPlay("Prodigy", Now.minus(4, MINUTES)),
          ArtistPlay("Sepultura", Now.minus(4, MINUTES)),
          ArtistPlay("Metallica", Now.minus(4, MINUTES)),
          ArtistPlay("Metallica", Now.minus(4, MINUTES)),
          ArtistPlay("Metallica", Now.minus(4, MINUTES)),
          ArtistPlay("Marvin Gaye", Now.minus(4, MINUTES)),
          ArtistPlay("Miles Davis", Now.minus(4, MINUTES)),
          ArtistPlay("Miles Davis", Now.minus(4, MINUTES)),
          ArtistPlay("John Coltrane", Now.minus(4, MINUTES)),
          ArtistPlay("Air", Now.minus(4, MINUTES)),
          ArtistPlay("Air", Now.minus(4, MINUTES)),
          ArtistPlay("Air", Now.minus(4, MINUTES))
        )).futureValue

        val expected = List(
          PlayCount("Beatles", 3),
          PlayCount("Metallica", 3),
          PlayCount("Air", 3),
          PlayCount("Miles Davis", 2),
          PlayCount("Pink Floyd", 2)
        )

        repository.topPlayCount(numberOfArtists = 5, time = Some(Now.minus(5, MINUTES))).futureValue shouldBe expected
      }
    }

    "is able to create indices on load" - {
      "when the collection does not already exist" in {
        Collection.drop(failIfNotFound = false).futureValue

        ArtistPlaysRepository(mongoDb, testDbConfig)

        indicesShouldBeSetup()
      }

      "when the collection already exists" in {
        Collection.insert[ArtistPlay](ArtistPlay("David Bowie", Instant.now())).futureValue

        ArtistPlaysRepository(mongoDb, testDbConfig)

        indicesShouldBeSetup()
      }
    }
  }

  private def indicesShouldBeSetup() = {
    val indices = Collection.indexesManager.list().futureValue

    indices.head.key shouldBe List("artistName" -> IndexType.Ascending)

    indices(1).key shouldBe List("playedDate" -> IndexType.Ascending)
    indices(1).options shouldBe BSONDocument("expireAfterSeconds" -> BSONLong((31 days).toSeconds))
  }
}

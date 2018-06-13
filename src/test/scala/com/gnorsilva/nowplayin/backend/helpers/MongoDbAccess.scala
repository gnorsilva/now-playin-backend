package com.gnorsilva.nowplayin.backend.helpers

import com.gnorsilva.nowplayin.backend.MongoDbFactory
import com.gnorsilva.nowplayin.backend.MongoDbFactory.MongoDbConfig
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.api.DefaultDB

import scala.concurrent.ExecutionContext.Implicits.global

trait MongoDbAccess extends Suite with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {

  val testDbConfig: MongoDbConfig = MongoDbConfig(Some(getClass.getSimpleName))

  lazy val mongoDb: DefaultDB = MongoDbFactory.loadMongoDb(testDbConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach
    mongoDb.drop().futureValue
  }
}

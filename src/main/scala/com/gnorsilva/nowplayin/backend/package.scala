package com.gnorsilva.nowplayin

import java.time.Instant

import reactivemongo.bson.{BSONDateTime, BSONHandler}

import scala.concurrent.duration.{Duration, FiniteDuration}

package object backend {

  implicit object BSONInstantHandler extends BSONHandler[BSONDateTime, Instant] {
    def read(time: BSONDateTime): Instant = Instant.ofEpochMilli(time.value)

    def write(instant: Instant) = BSONDateTime(instant.toEpochMilli)
  }

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration = Duration.fromNanos(d.toNanos)

}

package com.gnorsilva.nowplayin.backend

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import com.gnorsilva.nowplayin.backend.ArtistParser.{InvalidArtist, ValidArtist}
import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.StreamClient.StreamMessage
import com.gnorsilva.nowplayin.backend.StreamProcessor.Tweet
import spray.json._

object StreamProcessor {

  case class Tweet(text: String) {
    def isRetweet: Boolean = text.startsWith("RT @")
  }

  object TweetJsonProtocol extends DefaultJsonProtocol {
    implicit val tweetFormat = jsonFormat1(Tweet)
  }

}

class StreamProcessor(artistParser: ArtistParser, repository: ArtistPlaysRepository) extends Actor with ActorLogging {

  import com.gnorsilva.nowplayin.backend.StreamProcessor.TweetJsonProtocol._

  override def receive: Receive = {
    case StreamMessage(content) =>

      val tweet = content.parseJson.convertTo[Tweet]
      if (!tweet.isRetweet) {

        artistParser.parse(tweet) match {
          case ValidArtist(name) =>
            repository.insertArtistPlay(ArtistPlay(name, new Date))
            log.debug(s"$name ---> $tweet")
          case InvalidArtist() =>
            log.debug(s"Unparsed: ${tweet.text}")
        }
      }
  }

}

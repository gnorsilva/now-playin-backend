package com.gnorsilva.nowplayin.backend

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.StreamClient.StreamMessage
import com.gnorsilva.nowplayin.backend.StreamProcessor.{Tweet, regexes}
import spray.json._

import scala.annotation.tailrec
import scala.util.matching.Regex

object StreamProcessor {

  case class Tweet(text: String) {
    def isRetweet: Boolean = text.startsWith("RT @")
  }

  object TweetJsonProtocol extends DefaultJsonProtocol {
    implicit val tweetFormat = jsonFormat1(Tweet)
  }

  private val ops = """(?iuU)"""

  private val negatives = """(?!presented )"""

  private val artist = """(?:[^!\w]|\W)?([\w\.\ \(\)\/\'&\$\-]*?)"""

  private val greedyArtist = """(?:[^!\w]|\W)?([\w\.\ \(\)\/\'&\$\-]*)"""

  private val regexes: List[Regex] = List(
    raw"""${ops}now playing(?:.*\:\s*| on.*- | by | \d+[A-Z]?[ ]?- |\s*)$artist - """,
    raw"""$ops$negatives\sby ["']$artist(?=["'])""",
    raw"""$ops$negatives\sby[ ]+@$artist\s""",
    raw"""$ops$negatives\sby $artist (?=[-]+|on|is playing|listen|live via|at|now[ ]?playing|click.*)""",
    raw"""$ops$negatives\sby $greedyArtist""",
    raw"""$ops$artist - """
  ).map(_.r.unanchored)

}

class StreamProcessor(repository: ArtistPlaysRepository) extends Actor with ActorLogging {

  private val TWITTER_SHORT_URLS = """https://t.co/\w*"""

  import com.gnorsilva.nowplayin.backend.StreamProcessor.TweetJsonProtocol._

  override def receive: Receive = {
    case StreamMessage(content) =>
      val tweet = content.parseJson.convertTo[Tweet]

      if (!tweet.isRetweet) {
        val sanitizedText = tweet.text
          .replaceAll(TWITTER_SHORT_URLS, "")
          .replaceAll("&amp;", "&")

        val name = extractArtistName(sanitizedText)
        if (name.nonEmpty) {
          repository.insertArtistPlay(ArtistPlay(name, new Date))
          log.info(s"$name ---> $tweet")
        } else {
          log.info(s"Unparsed: $sanitizedText")
        }
      }
  }

  @tailrec
  private def extractArtistName(tweet: String, regexes: List[Regex] = regexes): String = {
    regexes match {
      case head :: tail => tweet match {
        case head(s) => processArtistName(s)
        case _ => extractArtistName(tweet, tail)
      }
      case List() => ""
    }
  }

  private def processArtistName(artistName: String): String = {
    artistName
      .trim
      .toLowerCase
      .split(' ')
      .map(capitalize)
      .mkString(" ")
  }

  private def capitalize(string: String): String =
    if (string.isEmpty || string.charAt(0).isUpper) {
      string
    } else {
      val i = string.takeWhile(!_.isLetter).length
      if (i >= string.length) {
        string
      } else {
        val chars = string.toCharArray
        chars(i) = chars(i).toUpper
        new String(chars)
      }
    }

}

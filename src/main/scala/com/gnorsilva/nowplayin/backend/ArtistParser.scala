package com.gnorsilva.nowplayin.backend

import com.gnorsilva.nowplayin.backend.ArtistParser._
import com.gnorsilva.nowplayin.backend.StreamProcessor.Tweet

import scala.annotation.tailrec
import scala.util.matching.Regex

object ArtistParser {

  trait Artist

  case class ValidArtist(name: String) extends Artist

  case class InvalidArtist() extends Artist

  private val Options = """(?iuU)"""

  private val Negatives = """(?!presented )"""

  private val Artist = """(?:[^!\w]|\W)?([\w\.\ \(\)\/\'&\$\-]*?)"""

  private val GreedyArtist = """(?:[^!\w]|\W)?([\w\.\ \(\)\/\'&\$\-]*)"""

  private val Regexes: List[Regex] = List(
    raw"""${Options}now playing(?:.*\:\s*| on.*- | by | \d+[A-Z]?[ ]?- |\s*)$Artist - """,
    raw"""$Options$Negatives\sby ["']$Artist(?=["'])""",
    raw"""$Options$Negatives\sby[ ]+@$Artist\s""",
    raw"""$Options$Negatives\sby $Artist (?=[-]+|on|is playing|listen|live via|at|now[ ]?playing|click.*)""",
    raw"""$Options$Negatives\sby $GreedyArtist""",
    raw"""$Options(?:\@[\w]+ - )$Artist - """,
    raw"""$Options$Artist - """
  ).map(_.r.unanchored)

  private val TwitterShortUrls = """https://t.co/\w*"""

  private val InvalidArtistRegex = """(?i)^(?:unknown|playing|track|various|advert).*"""
}

class ArtistParser {


  def parse(tweet: Tweet): Artist = {
    val sanitizedText = tweet.text
      .replaceAll(TwitterShortUrls, "")
      .replaceAll("&amp;", "&")

    val name = extractArtistName(sanitizedText)
    if (name.isEmpty || name.matches(InvalidArtistRegex)) {
      InvalidArtist()
    } else {
      ValidArtist(name)
    }
  }

  @tailrec
  private def extractArtistName(tweet: String, regexes: List[Regex] = ArtistParser.Regexes): String = {
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

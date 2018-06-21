package com.gnorsilva.nowplayin.backend

import com.gnorsilva.nowplayin.backend.ArtistParser.{Artist, InvalidArtist, ValidArtist}
import com.gnorsilva.nowplayin.backend.StreamProcessor.Tweet
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class ArtistParserSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  val edgeCases = Table(
    ("artist", "text"),
    ("Dj Khaled", "Now playing dj khaled - out here grinding.mp3 by !"),
    ("Bonnie Tyler", "is now playing ♫: bonnie tyler - total eclipse of the heart (clubstone edit)"),
    ("Suzi Lane", "Now Playing: Suzi Lane - Ooh, La, La (Long #Version) - https://t.co/6Dy4H8y2IJ"),
    ("Tijocelyne", "Now Playing Live:  - tijocelyne - #IamSoLougarou #BalKonpaLive24Sou24"),
    ("Social Distortion", "Now playing  - Social Distortion - I Was Wrong\nListen here: https://t.co/dKB9U4GK9M"),
    ("The Beach Boys", "Now Playing:\nFeel Flows by The Beach Boys\non https://t.co/2YZndTluEB"),
    ("Jessie J / David Guetta", "Now playing: 'LASERLIGHT' by 'JESSIE J / DAVID GUETTA' check it out!"),
    ("M People", "Now playing: 'RENAISSANCE' by \"M PEOPLE\" on faux FM"),
    ("Kay & Stoxx Feat. Mary Geras", "Now Playing Kay &amp; Stoxx feat. Mary Geras - Aint Nobody(Rivaz Club Mix) Listen Now!! https://t.co/CnKrO3DDSP"),
    ("John Coltrane", "Now Playing: Spiritual\nby John Coltrane\nat 02:32:57\non Jazz90.1 @901jazz\nListen Online at https://t.co/kXfiTvNJz3"),
    ("Yo Gotti", "#KingDennisG is now playing Yo Gotti - Down In The DM live on https://t.co/RJ61oYHCc8"),
    ("Dbanj", "NaijaXtreme Radio Now playing CHAPO by Dbanj @iamdbanj!\nLISTEN HERE  \u007bhttps://t.co/rL5EqgKNVk\u007d"),
    ("Alison Wonderland", "Alison Wonderland - Happy Place Now playing on Da 1 Radio. Listen Here!!!  https://t.co/rfpM3iIQ2M  #NowPlaying #Da1Radio"),
    ("Bob Baldwin", "\"Now Playing On WRJR\" \nBob Baldwin - Love's Light In Flight - Love Trippin'\n\n(Join \"The WRJR Universe\" Click link b… https://t.co/Nzvkx2t15F"),
    ("Ryan Bradley (Feat. Tevs)", "MakeAVoice Radio: Now playing \"Ryan Bradley (Feat. Tevs) - Night Like This\""),
    ("Maleficent (James Newton Howard)", "Now playing: Maleficent (James Newton Howard) - The Christening "),
    ("The Blackbyrds", "Now playing: Spaced Out by THE BLACKBYRDS on https://t.co/nyZ9KKWhYI "),
    ("Janeliasoul", "Now playing by  @Janeliasoul - I Am Bold  #1 African Radio https://t.co/QfmxE0pMGp"),
    ("Tim Cunningham", "\"Now Playing On WRJR\" \nTim Cunningham - Truly \n\n(Join \"The WRJR Universe\" Click link below!)\nhttps://t.co/btdWLV3Ea9"),
    ("I$aiah Feat. Royal Flush", "Now Playing on \"DNAradio\" - I$AIAH Feat. Royal Flush - The Largest Borough (Clean)"),
    ("Katy Perry", "Now Playing: Roulette by Katy Perry at https://t.co/8k2zy9bhQ7"),
    ("Kendrick Lamar", "Now playing Humble by Kendrick Lamar! https://t.co/QYG8mKzaob  Listen in Mon-Fri to; #TheGudTymezShow -… https://t.co/h1JOgb22sR"),
    ("Nickleback", "Now Playing Nickleback - Never gonna be alone presented by Ellinikosfm"),
    ("Cœur De Pirate", "Now Playing: Carry On by Cœur de pirate at https://t.co/8k2zy9bhQ7"),
    ("Xxodysintellekt", "Now playing Put You Down by @XxodysIntellekt! https://t.co/QYG8mKzaob  Listen in Mon-Fri to; #TheGudTymezShow -… https://t.co/XvBCunqjsQ"),
    ("Wu-tang Clan", "Now playing If Time Is Money (Fly Navigation) (Feat. Method Man) by Wu-Tang Clan!"),
    ("George Michael", "Now playing: George Michael - One More Try - Hear it now at https://t.co/CvzilQ85Yu #80s #80smusic"),
    ("John Doe", "Now Playing: HUMBLE.  by John Doe on 986thefrequency https://t.co/z8a1JCZOo3"),
    ("Newedition", "Now Playing My Secret (Didja Gitit Yet?) by @NewEdition Fellow us on twitter/instagram/facebook @mysoulradio"),
    ("2 In A Room", "Now Playing: Wiggle It by 2 In A Room https://t.co/nXDpysl5Ar"),
    ("Delta Iv", "Now playing 11B - Delta IV - Only Heaven (Gary Afterlife Remix) #beats2dance #Ch2 #trance #trancefamily… https://t.co/FMojVp6EhN"),
    ("Justin Bieber", "Now playing 120- Justin Bieber - Love Yourself (Allexinno Remix) @ by !"),
    ("Pedro Blaze", "Now Playing Pedro Blaze - Lights Shinin - - https://t.co/0t5CiKd5uj"),
    ("Iio", "NOW PLAYING on RudeBoy Radio..... Rapture (Different Guys &amp; Dima Flash 2k15 Remix) by Iio --- LISTEN LIVE...… https://t.co/MN1l0JJzdn"),
    ("The Ultimate 3", "@StreamSpinner - The Ultimate 3 - My Shadow is now playing on MPG Radios.. \nMetal Revolution - https://t.co/djotCporig"),
    ("Ian Sutherland", "@mpgradio - Ian Sutherland - And The Road Goes On is now playing on MPG Radios.. \nDigital City - https://t.co/Kb7wbydCEf"),
  )

  val tweetsToIgnore = Table(
    "text",
    "Some random tweet without an artist name",
    "Unknown - Foo bar bar",
    "Unknown Artist - Foo bar bar",
    "Various Artists - Foo bar bar",
    "Various - Foo bar bar",
    "Playing - Foo bar bar",
    "Track - Foo bar bar",
    "Advert - Foo bar bar",
  )

  val parser = new ArtistParser

  "ArtistParser" - {

    "is able to parse the various edge cases" - {
      forAll(edgeCases) { (artist: String, text: String) =>
        artist in {
          parser.parse(Tweet(text)) shouldBe ValidArtist(artist)
        }
      }
    }

    "ignores tweets it cannot parse or contain invalid artist names" - {
      forAll(tweetsToIgnore) { text =>
        text in {
          parser.parse(Tweet(text)) shouldBe InvalidArtist()
        }
      }
    }
  }

}

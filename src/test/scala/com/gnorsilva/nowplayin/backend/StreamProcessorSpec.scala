package com.gnorsilva.nowplayin.backend

import akka.actor.{ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, ImplicitSender, TestKit}
import com.gnorsilva.nowplayin.backend.ArtistPlaysRepository.ArtistPlay
import com.gnorsilva.nowplayin.backend.StreamClient.StreamMessage
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{never, verify}
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks

class StreamProcessorSpec extends TestKit(ActorSystem("test")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with TableDrivenPropertyChecks
  with MockitoSugar with Eventually with OneInstancePerTest {

  override protected def afterAll {
    super.afterAll
    TestKit.shutdownActorSystem(system)
  }

  val artistParser = new ArtistParser
  val mockRepository = mock[ArtistPlaysRepository]
  val streamProcessor = system.actorOf(
    Props(classOf[StreamProcessor], artistParser, mockRepository)
      .withDispatcher(CallingThreadDispatcher.Id))

  "Stream processor" - {

    "receives raw json tweet, parses it and saves it to the repository" in {
      streamProcessor ! StreamMessage("""{"text":"Now playing: David Bowie - Heroes"}""")

      verify(mockRepository).insertArtistPlay(argThat(MatchesArtistName("David Bowie")))
    }

    "ignores tweets it cannot parse" in {
      streamProcessor ! StreamMessage("""{"text":"Some random tweet without an artist name"}""")

      verify(mockRepository, never()).insertArtistPlay(any())
    }

    "ignores retweets" in {
      streamProcessor ! StreamMessage("""{"text":"RT @foo Now playing: David Bowie - Heroes"}""")

      verify(mockRepository, never()).insertArtistPlay(any())
    }
  }

  case class MatchesArtistName(expectedName: String) extends ArgumentMatcher[ArtistPlay] {
    override def matches(actual: ArtistPlay): Boolean = actual.artistName.equals(expectedName)
  }

}

package com.gnorsilva.nowplayin.backend

import com.gnorsilva.nowplayin.backend.OAuthString.{Clock, Noncer, OAuthSecrets}
import org.scalatest.{FreeSpec, Matchers}

class OAuthStringSpec extends FreeSpec with Matchers {

  "Creating the OAuthString" in {
    val oAuthData = OAuthSecrets(
      consumerKey = "xvz1evFS4wEEPTGEFPHBog",
      consumerSecret = "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw",
      token = "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
      tokenSecret = "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")

    val clock = new Clock {
      override def currentTimeSeconds: Long = 1318622958
    }

    val noncer = new Noncer {
      override def generateNonce: String = "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg"
    }

    val twitterOAuth = new OAuthString(noncer, clock, oAuthData)

    val httpMethod = "POST"
    val baseUrl = "https://api.twitter.com/1.1/statuses/update.json"
    val parameters = Map(
      "status" -> "Hello Ladies + Gentlemen, a signed OAuth request!",
      "include_entities" -> "true"
    )

    val expectedString =
      "OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\", " +
      "oauth_nonce=\"kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg\", " +
      "oauth_signature=\"hCtSmYh%2BiHYCEqBWrE7C7hYmtUk%3D\", " +
      "oauth_signature_method=\"HMAC-SHA1\", " +
      "oauth_timestamp=\"1318622958\", " +
      "oauth_token=\"370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb\", " +
      "oauth_version=\"1.0\""

    twitterOAuth.create(httpMethod, baseUrl, parameters) shouldBe expectedString
  }

}

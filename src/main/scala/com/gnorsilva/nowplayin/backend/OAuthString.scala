package com.gnorsilva.nowplayin.backend

import java.util.Base64

import com.gnorsilva.nowplayin.backend.OAuthString.{Clock, Noncer, OAuthSecrets}
import io.lemonlabs.uri.encoding.PercentEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.util.Random

object OAuthString {

  trait Clock {
    def currentTimeSeconds: Long
  }

  object Clock extends Clock {
    override def currentTimeSeconds: Long = System.currentTimeMillis / 1000
  }

  trait Noncer {
    def generateNonce: String
  }

  object Noncer extends Noncer {
    override def generateNonce: String = Random.alphanumeric.take(42).mkString
  }

  case class OAuthSecrets(consumerKey: String,
                          consumerSecret: String,
                          token: String,
                          tokenSecret: String)

}

class OAuthString(noncer: Noncer, clock: Clock, secrets: OAuthSecrets) {
  private val encoder = PercentEncoder()

  private val OAUTH_CONSUMER_KEY = "oauth_consumer_key"
  private val OAUTH_SIGNATURE_METHOD = "oauth_signature_method"
  private val OAUTH_TOKEN = "oauth_token"
  private val OAUTH_VERSION = "oauth_version"
  private val OAUTH_NONCE = "oauth_nonce"
  private val OAUTH_TIMESTAMP = "oauth_timestamp"
  private val OAUTH_SIGNATURE = "oauth_signature"

  private val defaultValues = Map(
    OAUTH_CONSUMER_KEY -> secrets.consumerKey,
    OAUTH_SIGNATURE_METHOD -> "HMAC-SHA1",
    OAUTH_TOKEN -> secrets.token,
    OAUTH_VERSION -> "1.0",
  )

  private val hmac = {
    val signingKey = s"${secrets.consumerSecret}&${secrets.tokenSecret}"
    val secret = new SecretKeySpec(signingKey.getBytes, "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(secret)
    mac
  }

  def create(httpMethod: String = "POST", baseUrl: String, parameters: Map[String, String] = Map()): String = {
    val currentValues = defaultValues ++ Map(
      OAUTH_NONCE -> noncer.generateNonce,
      OAUTH_TIMESTAMP -> String.valueOf(clock.currentTimeSeconds)
    )

    val signature = createSignature(httpMethod, baseUrl, currentValues ++ parameters)

    createAuthString(currentValues ++ Map(OAUTH_SIGNATURE -> signature))
  }

  private def createAuthString(parameters: Map[String, String]): String = {
    val string = parameters
      .toSeq
      .sortBy(_._1)
      .map { case (k, v) => s"""$k="${encode(v)}"""" }
      .reduce(_ + ", " + _)

    s"OAuth $string"
  }

  private def createSignature(httpMethod: String, baseUrl: String, parameters: Map[String, String]): String = {
    val parametersString = parameters
      .toSeq
      .sortBy(_._1)
      .map { case (k, v) => s"${encode(k)}=${encode(v)}" }
      .reduce(_ + "&" + _)

    val sigBaseString = s"${httpMethod.toUpperCase}&${encode(baseUrl)}&${encode(parametersString)}"
    val result = hmac.doFinal(sigBaseString.getBytes)
    Base64.getEncoder.encodeToString(result)
  }

  private def encode(s: String) = encoder.encode(s, "UTF-8")

}

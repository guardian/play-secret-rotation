package com.gu.play.secretrotation

import java.time.Clock
import java.time.Clock.systemUTC
import java.util.function.Supplier

import play.api.http.{SecretConfiguration, _}
import play.api.mvc._
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}


trait RotatingSecretComponents {
  val httpConfiguration: HttpConfiguration

  val secretRotationSupplier: Supplier[SecretState]

  lazy val requestFactory: RequestFactory =
    RotatingSecretComponents.requestFactoryFor(secretRotationSupplier, httpConfiguration)
}

object RotatingSecretComponents {
  def requestFactoryFor(secretRotationSupplier: Supplier[SecretState], hc: HttpConfiguration): RequestFactory =
    new DefaultRequestFactory(
      new DefaultCookieHeaderEncoding(hc.cookies),
      new RotatingKeySessionCookieBaker(hc.session, secretRotationSupplier),
      new RotatingKeyFlashCookieBaker(hc.flash, secretRotationSupplier)
    )
}

trait RotatingSecretCookieCodec extends CookieDataCodec {

  val secretRotationSupplier: Supplier[SecretState]
  val jwtConfiguration: JWTConfiguration

  implicit val c: Clock = systemUTC()

  private def jwtCodecFor(secret: SecretConfiguration): JWTCookieDataCodec =
    DefaultJWTCookieDataCodec(secret, jwtConfiguration)

  override def encode(data: Map[String, String]): String =
    jwtCodecFor(secretRotationSupplier.get().activeSecret).encode(data)

  override def decode(data: String): Map[String, String] = {
    secretRotationSupplier.get().decode[Map[String, String]](jwtCodecFor(_).decode(data), _.nonEmpty).getOrElse(Map.empty)
  }

}

class RotatingKeySessionCookieBaker(
  val config: SessionConfiguration,
  val secretRotationSupplier: Supplier[SecretState]) extends SessionCookieBaker with RotatingSecretCookieCodec {
  override val jwtConfiguration: JWTConfiguration = config.jwt
}

class RotatingKeyFlashCookieBaker(
  val config: FlashConfiguration,
  val secretRotationSupplier: Supplier[SecretState]) extends FlashCookieBaker with RotatingSecretCookieCodec {
  override val jwtConfiguration: JWTConfiguration = config.jwt
}




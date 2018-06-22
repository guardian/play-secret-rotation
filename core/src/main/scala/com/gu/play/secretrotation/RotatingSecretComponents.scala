package com.gu.play.secretrotation

import java.time.Clock
import java.time.Clock.systemUTC

import play.api.http._
import play.api.mvc._
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}
import play.api.{BuiltInComponentsFromContext, Configuration}


trait RotatingSecretComponents extends BuiltInComponentsFromContext {

  val secretStateSupplier: SnapshotProvider

  override def configuration: Configuration = {
    val nonRotatingSecretOnlyUsedToSatisfyConfigChecks: String = secretStateSupplier.snapshot().secrets.active.secret

    super.configuration ++ Configuration("play.http.secret.key" -> nonRotatingSecretOnlyUsedToSatisfyConfigChecks)
  }

  override lazy val requestFactory: RequestFactory =
    RotatingSecretComponents.requestFactoryFor(secretStateSupplier, httpConfiguration)
}

object RotatingSecretComponents {
  def requestFactoryFor(snapshotProvider: SnapshotProvider, hc: HttpConfiguration): RequestFactory =
    new DefaultRequestFactory(
      new DefaultCookieHeaderEncoding(hc.cookies),
      new RotatingKeySessionCookieBaker(hc.session, snapshotProvider),
      new RotatingKeyFlashCookieBaker(hc.flash, snapshotProvider)
    )


  trait RotatingSecretCookieCodec extends CookieDataCodec {
    val snapshotProvider: SnapshotProvider
    val jwtConfiguration: JWTConfiguration

    implicit val c: Clock = systemUTC()

    private def jwtCodecFor(secret: SecretConfiguration) = DefaultJWTCookieDataCodec(secret, jwtConfiguration)

    override def encode(data: Map[String, String]): String =
      jwtCodecFor(snapshotProvider.snapshot().secrets.active).encode(data)

    override def decode(data: String): Map[String, String] = {
      snapshotProvider.snapshot().decode[Map[String, String]](jwtCodecFor(_).decode(data), _.nonEmpty).getOrElse(Map.empty)
    }
  }

  class RotatingKeySessionCookieBaker(
    val config: SessionConfiguration,
    val snapshotProvider: SnapshotProvider) extends SessionCookieBaker with RotatingSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }

  class RotatingKeyFlashCookieBaker(
    val config: FlashConfiguration,
    val snapshotProvider: SnapshotProvider) extends FlashCookieBaker with RotatingSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }
}
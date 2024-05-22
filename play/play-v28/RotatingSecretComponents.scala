package com.gu.play.secretrotation

import java.time.Clock
import java.time.Clock.systemUTC

import play.api.http._
import play.api.mvc._
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}
import play.api.{BuiltInComponentsFromContext, Configuration}
import play.api.libs.crypto.{CSRFTokenSigner, CSRFTokenSignerProvider, DefaultCSRFTokenSigner, DefaultCookieSigner}
import java.security.MessageDigest
import com.github.blemale.scaffeine.{Scaffeine, LoadingCache}
import scala.concurrent.duration._


trait RotatingSecretComponents extends BuiltInComponentsFromContext {

  val secretStateSupplier: SnapshotProvider[String]

  override def configuration: Configuration = {
    val nonRotatingSecretOnlyUsedToSatisfyConfigChecks = secretStateSupplier.snapshot().secrets.active

    Configuration("play.http.secret.key" -> nonRotatingSecretOnlyUsedToSatisfyConfigChecks).withFallback(super.configuration)
  }

  override lazy val requestFactory: RequestFactory =
    RotatingSecretComponents.requestFactoryFor(secretStateSupplier, httpConfiguration)

  override lazy val csrfTokenSigner: CSRFTokenSigner =
    new RotatingSecretComponents.RotatingKeyCSRFTokenSigner(secretStateSupplier, systemUTC())
}

object RotatingSecretComponents {
  def requestFactoryFor(snapshotProvider: SnapshotProvider[String], hc: HttpConfiguration): RequestFactory =
    new DefaultRequestFactory(
      new DefaultCookieHeaderEncoding(hc.cookies),
      new RotatingKeySessionCookieBaker(hc.session, snapshotProvider),
      new RotatingKeyFlashCookieBaker(hc.flash, snapshotProvider)
    )


  trait RotatingSecretCookieCodec extends CookieDataCodec {
    val snapshotProvider: SnapshotProvider[String]
    val jwtConfiguration: JWTConfiguration

    implicit val c: Clock = systemUTC()

    private def jwtCodecFor(secret: String) = DefaultJWTCookieDataCodec(SecretConfiguration(secret), jwtConfiguration)

    override def encode(data: Map[String, String]): String =
      jwtCodecFor(snapshotProvider.snapshot().secrets.active).encode(data)

    override def decode(data: String): Map[String, String] = {
      snapshotProvider.snapshot().decode[Map[String, String]](jwtCodecFor(_).decode(data), _.nonEmpty).getOrElse(Map.empty)
    }
  }

  class RotatingKeySessionCookieBaker(
    val config: SessionConfiguration,
    val snapshotProvider: SnapshotProvider[String]) extends SessionCookieBaker with RotatingSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }

  class RotatingKeyFlashCookieBaker(
    val config: FlashConfiguration,
    val snapshotProvider: SnapshotProvider[String]) extends FlashCookieBaker with RotatingSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }

  class RotatingKeyCSRFTokenSigner(
    snapshotProvider: SnapshotProvider[String],
    clock: Clock) extends CSRFTokenSigner {

    private val signerCache: LoadingCache[String, DefaultCSRFTokenSigner] = Scaffeine()
      .expireAfterAccess(1.day) // stop the cache growing indefinitely
      .build(secret => new DefaultCSRFTokenSigner(new DefaultCookieSigner(SecretConfiguration(secret)), clock))

    private def signerForActiveSecret() = signerCache.get(snapshotProvider.snapshot().secrets.active)
    
    override def signToken(token: String): String = signerForActiveSecret().signToken(token)
    override def generateToken: String = signerForActiveSecret().generateToken
    override def generateSignedToken: String = signerForActiveSecret().generateSignedToken
    override def constantTimeEquals(a: String, b: String): Boolean = signerForActiveSecret().constantTimeEquals(a, b)

    /**
     * This method verifies tokens which may have been signed with a previous secret that we still consider valid
     * for now. It tries all applicable secrets to see if any of them can verify the token.
     */
    override def extractSignedToken(token: String): Option[String] =
      snapshotProvider.snapshot().decodeOpt(secret => signerCache.get(secret).extractSignedToken(token))

    /**
     * It's important that this method doesn't just delegate to an underlying `DefaultCSRFTokenSigner`, because this
     * method uses the `extractSignedToken()` method, and we need to use the tolerant version of that method that's
     * only available in _this_ class.
     */
    override def compareSignedTokens(tokenA: String, tokenB: String): Boolean = {
      for {
        rawA <- extractSignedToken(tokenA)
        rawB <- extractSignedToken(tokenB)
      } yield MessageDigest.isEqual(rawA.getBytes("utf-8"), rawB.getBytes("utf-8"))
    }.getOrElse(false)
  }
}
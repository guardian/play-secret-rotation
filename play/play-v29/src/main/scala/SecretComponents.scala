package com.gu.play.secretrotation

import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import play.api.http._
import play.api.libs.crypto.{CSRFTokenSigner, DefaultCSRFTokenSigner, DefaultCookieSigner}
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}
import play.api.mvc._
import play.api.{BuiltInComponentsFromContext, Configuration}

import java.security.MessageDigest
import java.time.Clock
import java.time.Clock.systemUTC
import scala.concurrent.duration.DurationInt

trait SecretComponents extends BuiltInComponentsFromContext {

  val secretStateSupplier: SnapshotProvider

  override def configuration: Configuration = {
    val nonRotatingSecretOnlyUsedToSatisfyConfigChecks = secretStateSupplier.snapshot().secrets.active

    Configuration("play.http.secret.key" -> nonRotatingSecretOnlyUsedToSatisfyConfigChecks).withFallback(super.configuration)
  }

  override lazy val requestFactory: RequestFactory =
    SecretComponents.requestFactoryFor(secretStateSupplier, httpConfiguration)

  override lazy val csrfTokenSigner: CSRFTokenSigner =
    new SecretComponents.SnapshotKeyCSRFTokenSigner(secretStateSupplier, systemUTC())
}

object SecretComponents {
  def requestFactoryFor(snapshotProvider: SnapshotProvider, hc: HttpConfiguration): RequestFactory =
    new DefaultRequestFactory(
      new DefaultCookieHeaderEncoding(hc.cookies),
      new SnapshotKeySessionCookieBaker(hc.session, snapshotProvider),
      new SnapshotKeyFlashCookieBaker(hc.flash, snapshotProvider)
    )


  trait SnapshotSecretCookieCodec extends CookieDataCodec {
    val snapshotProvider: SnapshotProvider
    val jwtConfiguration: JWTConfiguration

    implicit val c: Clock = systemUTC()

    private def jwtCodecFor(secret: String) = DefaultJWTCookieDataCodec(SecretConfiguration(secret), jwtConfiguration)

    override def encode(data: Map[String, String]): String =
      jwtCodecFor(snapshotProvider.snapshot().secrets.active).encode(data)

    override def decode(data: String): Map[String, String] = {
      snapshotProvider.snapshot().decode[Map[String, String]](jwtCodecFor(_).decode(data), _.nonEmpty).getOrElse(Map.empty)
    }
  }

  class SnapshotKeySessionCookieBaker(
     val config: SessionConfiguration,
     val snapshotProvider: SnapshotProvider) extends SessionCookieBaker with SnapshotSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }

  class SnapshotKeyFlashCookieBaker(
     val config: FlashConfiguration,
     val snapshotProvider: SnapshotProvider) extends FlashCookieBaker with SnapshotSecretCookieCodec {
    override val jwtConfiguration: JWTConfiguration = config.jwt
  }

  class SnapshotKeyCSRFTokenSigner(
     snapshotProvider: SnapshotProvider,
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

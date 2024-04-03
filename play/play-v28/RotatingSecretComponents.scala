package com.gu.play.secretrotation

import java.time.Clock
import java.time.Clock.systemUTC

import play.api.http._
import play.api.mvc._
import play.api.mvc.request.{DefaultRequestFactory, RequestFactory}
import play.api.{BuiltInComponentsFromContext, Configuration}
import play.api.libs.crypto.{CSRFTokenSigner, CSRFTokenSignerProvider}
import java.security.MessageDigest
import com.github.blemale.scaffeine._
import play.api.libs.crypto.{DefaultCSRFTokenSigner, DefaultCookieSigner}


trait RotatingSecretComponents extends BuiltInComponentsFromContext {

  val secretStateSupplier: SnapshotProvider

  override def configuration: Configuration = {
    val nonRotatingSecretOnlyUsedToSatisfyConfigChecks = secretStateSupplier.snapshot().secrets.active

    super.configuration.withFallback(Configuration("play.http.secret.key" -> nonRotatingSecretOnlyUsedToSatisfyConfigChecks))
  }

  override lazy val requestFactory: RequestFactory =
    RotatingSecretComponents.requestFactoryFor(secretStateSupplier, httpConfiguration)

  override lazy val csrfTokenSigner: CSRFTokenSigner =
    new RotatingSecretComponents.RotatingKeyCSRFTokenSigner(secretStateSupplier, systemUTC())
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

    private def jwtCodecFor(secret: String) = DefaultJWTCookieDataCodec(SecretConfiguration(secret), jwtConfiguration)

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

  class RotatingKeyCSRFTokenSigner(
    snapshotProvider: SnapshotProvider,
    clock: Clock) extends CSRFTokenSigner {

    private val signerCache: LoadingCache[String, DefaultCSRFTokenSigner] = Scaffeine()
      .maximumSize(10) // only need 2 really, this config will cope better if the key is rotated rapidly for some reason
      .build(secret => new DefaultCSRFTokenSigner(new DefaultCookieSigner(SecretConfiguration(secret)), clock))

    private def signerForActiveSecret() = signerCache.get(snapshotProvider.snapshot().secrets.active)

    // Methods that
    override def signToken(token: String): String = signerForActiveSecret().signToken(token)
    override def generateToken: String = signerForActiveSecret().generateToken
    override def generateSignedToken: String = signerForActiveSecret().generateToken
    override def constantTimeEquals(a: String, b: String): Boolean = signerForActiveSecret().constantTimeEquals(a, b)
    /**
     * Extract a signed token that was signed by `signToken(String)`.
     *
     * @param token The signed token to extract.
     * @return The verified raw token, or None if the token isn't valid.
     */
    override def extractSignedToken(token: String): Option[String] =
      snapshotProvider.snapshot().decode[Option[String]](secret => signerCache.get(secret).extractSignedToken(token), _.nonEmpty).flatten

    override def compareSignedTokens(tokenA: String, tokenB: String): Boolean = {
      for {
        rawA <- extractSignedToken(tokenA)
        rawB <- extractSignedToken(tokenB)
      } yield MessageDigest.isEqual(rawA.getBytes("utf-8"), rawB.getBytes("utf-8"))
    }.getOrElse(false)
  }
}
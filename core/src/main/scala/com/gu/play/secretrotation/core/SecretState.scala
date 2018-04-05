package com.gu.play.secretrotation.core

import java.time.Clock.systemUTC
import java.time.{Clock, Duration}

import org.threeten.extra.Interval
import play.api.Logger
import play.api.http.SecretConfiguration

sealed trait SecretState {
  def acceptedSecrets: Stream[SecretConfiguration]

  def activeSecret: SecretConfiguration

  def description: String

  /**
    * Want to know:
    * If decoding was successful, but with a legacy secret (especially if we are towards the end of the overlap period)
    */
  def decode[T](decodingFunc: SecretConfiguration => T, successfulDecode: T => Boolean): Option[T] = {
    val secretAndSuccessfulDecodes: Stream[(SecretConfiguration, T)] = for {
      secret <- acceptedSecrets
      decodedValue = decodingFunc(secret)
      if successfulDecode(decodedValue)
    } yield secret -> decodedValue

    secretAndSuccessfulDecodes.headOption.map {
      case (secret, decodedValue) =>
        if (secret != activeSecret) {
          Logger.info("Accepted decode with non-active key : "+description)
        }
        decodedValue
    }
  }
}

case class InitialSecret(secret: SecretConfiguration) extends SecretState {
  override val acceptedSecrets = Stream(secret)

  override val activeSecret = secret

  val description = "Initial secret, no upcoming rotation of secret"
}

case class TransitioningSecret(
  oldSecret: SecretConfiguration,
  newSecret: SecretConfiguration,
  overlapInterval: Interval
)(implicit clock: Clock = systemUTC()) extends SecretState {

  def isOldSecretAccepted = clock.instant.isBefore(overlapInterval.getEnd)
  def isNewSecretActive = clock.instant.isAfter(overlapInterval.getStart)

  def acceptedSecrets: Stream[SecretConfiguration] =
    Stream(oldSecret).filter(_ => isOldSecretAccepted) :+ newSecret

  def activeSecret: SecretConfiguration =
    if (isNewSecretActive) newSecret else oldSecret

  override def description: String = {
    val now = clock.instant()

    if (overlapInterval.isAfter(now)) {
      s"upcoming transition between old and new secrets in ${Duration.between(now, overlapInterval.getStart)} at ${overlapInterval.getStart}"
    } else if (overlapInterval.isBefore(now)) {
      s"transition to latest secret completed at ${overlapInterval.getEnd}"
    } else {
      s"transition between old and new secrets in progress (during $overlapInterval)"
    }
  }

}

package com.gu.play.secretrotation

import java.time.Clock.systemUTC
import java.time.{Clock, Duration}

import org.threeten.extra.Interval
import play.api.Logger
import play.api.http.SecretConfiguration

import scala.math.Ordering.Implicits._

sealed trait SecretState {
  def acceptedSecrets: Stream[SecretConfiguration]

  def activeSecret: SecretConfiguration

  def description: String

  def decode[T](decodingFunc: SecretConfiguration => T, successfulDecode: T => Boolean): Option[T]
}

case class InitialSecret(secret: SecretConfiguration) extends SecretState {
  override val acceptedSecrets = Stream(secret)

  override val activeSecret = secret

  val description = "Initial secret, no upcoming rotation of secret"

  override def decode[T](decodingFunc: SecretConfiguration => T, successfulDecode: T => Boolean): Option[T] =
    Some(decodingFunc(secret)).filter(successfulDecode)
}

case class TransitioningSecret(
  oldSecret: SecretConfiguration,
  newSecret: SecretConfiguration,
  overlapInterval: Interval
)(implicit clock: Clock = systemUTC()) extends SecretState {

  def isOldSecretAccepted = clock.instant.isBefore(overlapInterval.getEnd)
  def isNewSecretActive = clock.instant.isAfter(overlapInterval.getStart)

  val warningThreshold = overlapInterval.getStart.plus(overlapInterval.toDuration.dividedBy(3).multipliedBy(2))

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

  /**
    * Want to know:
    * If decoding was successful, but with a legacy secret (especially if we are towards the end of the overlap period)
    */
  override def decode[T](decodingFunc: SecretConfiguration => T, successfulDecode: T => Boolean): Option[T] = {
    val now = clock.instant()

    val secretAndSuccessfulDecodes: Stream[(SecretConfiguration, T)] = for {
      secret <- acceptedSecrets
      decodedValue = decodingFunc(secret)
      if successfulDecode(decodedValue)
    } yield secret -> decodedValue

    secretAndSuccessfulDecodes.headOption.map {
      case (secret, decodedValue) =>
        if (secret != activeSecret) {
          val message = s"Accepted decode with non-active key : $description"
          if (now > warningThreshold) Logger.warn(message) else Logger.debug(message)
        }
        decodedValue
    }
  }
}

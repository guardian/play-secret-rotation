package com.gu.play.secretrotation

import java.time.Clock.systemUTC
import java.time.{Clock, Duration}

import com.gu.play.secretrotation.DualSecretTransition.Phase.{Completed, InProgress, Upcoming}
import com.gu.play.secretrotation.DualSecretTransition.Secret.{Age, New, Old}
import org.threeten.extra.Interval

import scala.math.Ordering.Implicits._


object DualSecretTransition {
  object Secret {
    sealed trait Age

    object Old extends Age
    object New extends Age
  }

  sealed trait PhaseAges extends Phase[Age]

  object Phase {

    class PhaseAge(val active: Age, val alsoAccepted: Iterable[Age] = None) extends PhaseAges

    object Upcoming   extends PhaseAge(active = Old, alsoAccepted = Some(New))
    object InProgress extends PhaseAge(active = New, alsoAccepted = Some(Old))
    object Completed  extends PhaseAge(active = New)
  }


  case class InitialSecret(secret: String) extends SnapshotProvider {

    override def snapshot(): SecretsSnapshot = new SecretsSnapshot {
      val description = "Initial secret, no known upcoming rotation of secret"

      override def decode[T](decodingFunc: String => T, conclusiveDecode: T => Boolean): Option[T] =
        Seq(decodingFunc(secret)).find(conclusiveDecode)

      override val secrets = new Phase[String] {
        val active = secret
        val alsoAccepted = Nil
      }
    }
  }


  case class TransitioningSecret(
    oldSecret: String,
    newSecret: String,
    overlapInterval: Interval
  )(implicit clock: Clock = systemUTC()) extends SnapshotProvider {

    val secretsByAge = Map(Old -> oldSecret, New -> newSecret)

    val phaseSchedule =
      PhaseSchedule(Upcoming, overlapInterval.getStart -> InProgress, overlapInterval.getEnd -> Completed)

    val overlapDuration = overlapInterval.toDuration
    val permittedSnapshotStaleness = overlapDuration.dividedBy(10)
    val warningThreshold = overlapInterval.getStart.plus(overlapDuration.dividedBy(3).multipliedBy(2))

    def snapshot(): SecretsSnapshot = {
      val snapshotTime = clock.instant()
      val snapshotBestBefore = snapshotTime.plus(permittedSnapshotStaleness)

      val phase = phaseSchedule.phaseAt(snapshotTime)

      new SecretsSnapshot {
        override def secrets: Phase[String] = phase.map(secretsByAge)

        override def description: String = phase match {
          case Upcoming =>
            s"upcoming transition between old and new secrets in ${Duration.between(snapshotTime, overlapInterval.getStart)} at ${overlapInterval.getStart}"
          case InProgress =>
            s"transition between old and new secrets in progress (during $overlapInterval)"
          case Completed =>
            s"transition to latest secret completed at ${overlapInterval.getEnd}"
        }

        /**
          * Want to know:
          * If decoding was successful, but with a legacy secret (especially if we are towards the end of the overlap period)
          */
        override def decode[T](decodingFunc: String => T, conclusiveDecode: T => Boolean): Option[T] = {
          if (clock.instant() > snapshotBestBefore)
            logger.warn("Don't hold onto snapshots! Get a new snapshot with each user interaction.")

          (for {
            secret <- secrets.accepted
            decodedValue = decodingFunc(secret)
            if conclusiveDecode(decodedValue)
          } yield {
            if (secret != secrets.active) {
              val message = s"Accepted decode with non-active key : $description"
              if (clock.instant() > warningThreshold) logger.warn(message) else logger.debug(message)
            }
            decodedValue
          }).headOption
        }
      }
    }
  }
}
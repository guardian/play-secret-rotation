package com.gu.play.secretrotation

import java.time.Instant

import scala.collection.SortedMap

/**
  * A 'phase' holds the particular set of secrets that are active/accepted for a given period of time
  */
trait Phase[T] {
  val active: T

  val alsoAccepted: Iterable[T]

  final def onlyAcceptsActive = alsoAccepted.isEmpty

  final def accepted: Stream[T] = Stream(active) ++ alsoAccepted

  def map[S](f: T => S): Phase[S] = {
    val activeS = f(active)
    val alsoAcceptedS = alsoAccepted.map(f)
    new Phase[S] {
      val active: S = activeS
      val alsoAccepted = alsoAcceptedS
    }
  }
}

/**
  * From hitting a datastore, we can determine a sequence of
  * [[Phase]]s, with their start and stop times.
  *
  * So for any given state, there is a phase schedule.
  */
case class PhaseSchedule[T](initialPhase: Phase[T], phaseStarts: (Instant, Phase[T])*) {
  val phasesByStart = SortedMap(phaseStarts: _*)

  def phaseAt(instant: Instant): Phase[T] =
    phasesByStart.until(instant).values.lastOption.getOrElse(initialPhase)

}
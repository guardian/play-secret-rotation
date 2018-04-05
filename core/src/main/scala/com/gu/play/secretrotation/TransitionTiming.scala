package com.gu.play.secretrotation

import java.time.{Duration, Instant}

import org.threeten.extra.Interval

/**
  * @param overlapDuration how long both secrets will be accepted
  * @param usageDelay don't use a new secret too quickly, other boxes need time to notice
  */
case class TransitionTiming(
  overlapDuration: Duration,
  usageDelay: Duration
) {
  def overlapIntervalForSecretPublishedAt(instant: Instant): Interval =
    Interval.of(instant.plus(usageDelay), overlapDuration)
}

package com.gu.play.secretrotation

import java.time.{Duration, Instant}

import org.threeten.extra.Interval

/**
  * @param overlapDuration how long old secrets will be accepted after the new secret is actively used
  * @param usageDelay don't use a new secret too quickly, other boxes need time to notice
  */
case class TransitionTiming(
  overlapDuration: Duration,
  usageDelay: Duration
) {
  def overlapIntervalForSecretPublishedAt(instant: Instant): Interval =
    Interval.of(instant.plus(usageDelay), overlapDuration)
}

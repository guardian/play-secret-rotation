package com.gu.play.secretrotation

import java.time.Instant

import com.gu.play.secretrotation.DualSecretTransition.Phase.{Completed, InProgress, Upcoming}
import org.scalatest._

class PhaseScheduleSpec extends FlatSpec with Matchers {
  "A phase schedule" should "return the correct phase for a given time" in {
    val startOfOverlap = Instant.now()
    val endOfOverlap = startOfOverlap.plusSeconds(100)

    val schedule =
      PhaseSchedule(Upcoming, startOfOverlap -> InProgress, endOfOverlap -> Completed)

    schedule.phaseAt(startOfOverlap.minusMillis(1)) shouldBe Upcoming
    schedule.phaseAt(startOfOverlap.plusMillis(1)) shouldBe InProgress

    schedule.phaseAt(endOfOverlap.minusMillis(1)) shouldBe InProgress
    schedule.phaseAt(endOfOverlap.plusMillis(1)) shouldBe Completed
  }
}

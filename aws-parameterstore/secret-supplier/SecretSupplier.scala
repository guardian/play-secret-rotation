package com.gu.play.secretrotation.aws.parameterstore

import com.gu.play.secretrotation.DualSecretTransition.{InitialSecret, TransitioningSecret}
import com.gu.play.secretrotation.{CachingSnapshotProvider, SnapshotProvider, TransitionTiming}

/**
  * @param ssmClient use the [[com.gu.play.secretrotation.aws.parameterstore.AwsSdkV2]] implementation
  */
class SecretSupplier(
  val transitionTiming: TransitionTiming,
  parameterName: String,
  ssmClient: MinimalAwsSdkWrapper
) extends CachingSnapshotProvider {

  val InitialVersion = 1

  def loadState(): SnapshotProvider = {
    val latestValue = ssmClient.fetchValues(Seq(parameterName)).head
    val latestVersion = latestValue.metadata.version

    val state = latestVersion match {
      case InitialVersion => InitialSecret(latestValue.value)
      case _ =>
        val previousVersion = latestVersion - 1
        val previousValue = ssmClient.fetchValues(Seq(s"$parameterName:$previousVersion")).head
        TransitioningSecret(
          oldSecret = previousValue.value,
          newSecret = latestValue.value,
          overlapInterval =
            transitionTiming.overlapIntervalForSecretPublishedAt(latestValue.metadata.lastModified)
        )
    }
    logger.info(s"Fetched Secret state: ${state.snapshot().description}")
    state
  }
}

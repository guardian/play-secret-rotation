package com.gu.play.secretrotation.aws.parameterstore

import com.gu.play.secretrotation.DualSecretTransition.{InitialSecret, TransitioningSecret}
import com.gu.play.secretrotation.{CachingSnapshotProvider, SnapshotProvider, TransitionTiming}
import play.api.Logger
import play.api.http.SecretConfiguration

/**
  * @param ssmClient use the implementation of this compiled against AWS SDK v1 or v2
  *                  as required.
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
      case InitialVersion => InitialSecret(SecretConfiguration(latestValue.value))
      case _ =>
        val previousVersion = latestVersion - 1
        val previousValue = ssmClient.fetchValues(Seq(s"$parameterName:$previousVersion")).head
        TransitioningSecret(
          oldSecret = SecretConfiguration(previousValue.value),
          newSecret = SecretConfiguration(latestValue.value),
          overlapInterval =
            transitionTiming.overlapIntervalForSecretPublishedAt(latestValue.metadata.lastModified)
        )
    }
    Logger.info(s"Fetched Secret state: ${state.snapshot().description}")
    state
  }
}

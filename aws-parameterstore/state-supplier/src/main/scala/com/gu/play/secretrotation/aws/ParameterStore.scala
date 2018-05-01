package com.gu.play.secretrotation.aws

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.{DescribeParametersRequest, GetParametersRequest, ParameterMetadata, ParameterStringFilter}
import com.gu.play.secretrotation.DualSecretTransition.{InitialSecret, TransitioningSecret}
import com.gu.play.secretrotation._
import play.api.Logger
import play.api.http.SecretConfiguration

import scala.collection.JavaConverters._

object ParameterStore {
  val InitialVersion = 1

  class SecretSupplier(
    val transitionTiming: TransitionTiming,
    parameterName: String,
    ssmClient: AWSSimpleSystemsManagement
  ) extends CachingSnapshotProvider {

    def loadState(): SnapshotProvider = {
      val metadata = fetchParameterMetadata()
      val latestVersion = metadata.getVersion.toLong

      val state = latestVersion match {
        case InitialVersion => InitialSecret(fetchSecretsByVersion(InitialVersion)(InitialVersion))
        case _ =>
          val previousVersion = latestVersion - 1
          val secretsByVersion = fetchSecretsByVersion(previousVersion, latestVersion)
          TransitioningSecret(
            oldSecret = secretsByVersion(previousVersion),
            newSecret = secretsByVersion(latestVersion),
            overlapInterval =
              transitionTiming.overlapIntervalForSecretPublishedAt(metadata.getLastModifiedDate.toInstant)
          )
      }
      Logger.info(s"Fetched Secret state: ${state.snapshot().description}")
      state
    }

    private def fetchParameterMetadata(): ParameterMetadata = {
      val describeSecretParameterRequest = new DescribeParametersRequest()
        .withParameterFilters(new ParameterStringFilter().withKey("Name").withValues(parameterName))

      ssmClient.describeParameters(describeSecretParameterRequest).getParameters.get(0)
    }

    private def fetchSecretsByVersion(versions: Long*): Map[Long, SecretConfiguration] = {
      val parametersResult = ssmClient.getParameters(
        new GetParametersRequest()
          .withWithDecryption(true)
          .withNames(versions.map(v => s"$parameterName:$v").asJavaCollection)
      )
      parametersResult.getParameters.asScala.map(p =>
        p.getVersion.toLong -> SecretConfiguration(p.getValue)).toMap
    }
  }

}

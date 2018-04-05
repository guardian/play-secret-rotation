package com.gu.play.secretrotation.aws

import java.util.function.Supplier

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient
import com.amazonaws.services.simplesystemsmanagement.model.{DescribeParametersRequest, GetParametersRequest, ParameterMetadata, ParameterStringFilter}
import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import com.gu.play.secretrotation.core._
import play.api.http.SecretConfiguration

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object AwsParameterStore {
  val InitialVersion = 1

  class SecretSupplier(
    transitionTiming: TransitionTiming,
    parameterName: String,
    ssmClient: AWSSimpleSystemsManagementClient
  ) extends Supplier[SecretState] {

    // make sure we don't cache the secret state too long, we need to be ready to use a new secret when issued
    val secretStateCachingDuration = transitionTiming.usageDelay.getSeconds.seconds / 2

    private val cache: LoadingCache[Unit, SecretState] =
      Scaffeine().expireAfterWrite(secretStateCachingDuration).build(loader = _ => fetchSecretState())

    override def get(): SecretState = cache.get(Unit)

    def fetchSecretState(): SecretState = {
      val metadata = fetchParameterMetadata()
      val latestVersion = metadata.getVersion.toLong

      latestVersion match {
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

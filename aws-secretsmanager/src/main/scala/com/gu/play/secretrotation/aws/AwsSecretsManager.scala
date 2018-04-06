package com.gu.play.secretrotation.aws

import java.util.function.Supplier

import com.amazonaws.services.secretsmanager.AWSSecretsManager
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import com.gu.play.secretrotation.{SecretState, TransitionTiming, TransitioningSecret}
import play.api.http.SecretConfiguration

import scala.concurrent.duration._

object AwsSecretStore {
  val CurrentStageName = "AWSCURRENT"
  val PreviousStageName = "AWSPREVIOUS"

  class SecretSupplier(
    transitionTiming: TransitionTiming,
    secretId: String,
    secretsManagerClient: AWSSecretsManager
  ) extends Supplier[SecretState] {

    // make sure we don't cache the secret state too long, we need to be ready to use a new secret when issued
    val secretStateCachingDuration = transitionTiming.usageDelay.getSeconds.seconds / 2

    private val cache: LoadingCache[Unit, SecretState] =
      Scaffeine().expireAfterWrite(secretStateCachingDuration).build(loader = _ => fetchSecretState())

    override def get(): SecretState = cache.get(Unit)

    def fetchSecretState(): SecretState = {
      val currentSecretResult = fetchSecretAtState(CurrentStageName)
      val previousSecretResult = fetchSecretAtState(PreviousStageName)

      TransitioningSecret(
        oldSecret = SecretConfiguration(previousSecretResult.getSecretString),
        newSecret = SecretConfiguration(currentSecretResult.getSecretString),
        overlapInterval =
          transitionTiming.overlapIntervalForSecretPublishedAt(currentSecretResult.getCreatedDate.toInstant) // ??????
      )
    }

    private def fetchSecretAtState(stage: String) = {
      secretsManagerClient.getSecretValue(
        new GetSecretValueRequest().withVersionStage(stage).withSecretId(secretId)
      )
    }
  }

}

package com.gu.play.secretrotation.aws.parameterstore

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent
import com.gu.play.secretrotation.SecretGenerator.generateSecret
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProvider, EnvironmentVariableCredentialsProvider, ProfileCredentialsProvider}
import software.amazon.awssdk.services.ssm.model._
import software.amazon.awssdk.services.ssm.SsmClient

object Lambda extends App {

  def lambdaHandler(input: ScheduledEvent, context: Context): String = {
    new Updater(
      credentials = EnvironmentVariableCredentialsProvider.create(),
      parameterName = System.getenv("PARAMETER_NAME")
    ).updateSecret()
    "OK"
  }

  /*
     This is only invoked (as a 'main' method) in development, *not* when running as an AWS Lambda.
     run com.gu.play.secretrotation.aws.parameterstore.Lambda
  */
  args.toList match {
    case profileName :: parameterName :: Nil =>
      val devUpdater = new Updater(
        ProfileCredentialsProvider.builder().profileName(profileName).build(),
        parameterName
      )
      devUpdater.updateSecret()
    case _ =>
      Console.err.println("ERROR - expected exactly 2 arguments: yourProfile yourParameterName")
      System.exit(1)
  }

  class Updater(credentials: AwsCredentialsProvider, parameterName: String) {
    val ssmClient: SsmClient = SsmClient.builder().credentialsProvider(credentials).build()

    def updateSecret(): Unit = {
      val describeSecretParameterRequest = DescribeParametersRequest.builder()
        .parameterFilters(
          ParameterStringFilter.builder().key("Name").values(parameterName).build()
        )
        .build()

      val metadata = ssmClient.describeParameters(describeSecretParameterRequest).parameters().get(0)

      val kmsKeyId = metadata.keyId()

      val putParameterResult =
        ssmClient.putParameter(
          PutParameterRequest.builder()
            .name(parameterName)
            .overwrite(true)
            .keyId(kmsKeyId)
            .`type`(metadata.`type`())
            .value(generateSecret)
            .build()
        )
      println(s"Updated secret, put parameter version ${putParameterResult.version()}")
    }
  }

}

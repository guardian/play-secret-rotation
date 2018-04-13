package com.gu.play.secretrotation.aws

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProvider, InstanceProfileCredentialsProvider}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.{DescribeParametersRequest, ParameterStringFilter, PutParameterRequest}
import com.gu.play.secretrotation.SecretGenerator.generateSecret

object ParameterStoreLambda extends App {

  def lambdaHandler(): Unit = {
    new Updater(
      credentials = new InstanceProfileCredentialsProvider(false),
      parameterName = System.getenv("PARAMETER_NAME")
    ).updateSecret()
  }

  /*
     This is only invoked (as a 'main' method) in development, *not* when running as an AWS Lambda.
     run com.gu.play.secretrotation.aws.ParameterStoreLambda
  */
  args.toList match {
    case profileName :: parameterName =>
      val devUpdater = new Updater(
        credentials = new ProfileCredentialsProvider(profileName),
        parameterName = args(1)
      )
      devUpdater.updateSecret()
    case _ =>
      Console.err.println("ERROR - expected exactly 2 arguments: yourProfile yourParameterName")
      System.exit(1)
  }

  class Updater(credentials: AWSCredentialsProvider, parameterName: String) {
    val ssmClient =
      AWSSimpleSystemsManagementClientBuilder.standard().withCredentials(credentials).build()

    def updateSecret() = {
      val describeSecretParameterRequest = new DescribeParametersRequest()
        .withParameterFilters(new ParameterStringFilter().withKey("Name").withValues(parameterName))

      val metadata = ssmClient.describeParameters(describeSecretParameterRequest).getParameters.get(0)

      val kmsKeyId = metadata.getKeyId
      
      val putParameterResult =
        ssmClient.putParameter(new PutParameterRequest()
          .withName(parameterName)
          .withOverwrite(true)
          .withKeyId(kmsKeyId)
          .withType(metadata.getType)
          .withValue(generateSecret)
        )
      println(s"Updated secret, put parameter version ${putParameterResult.getVersion}")
    }
  }

}

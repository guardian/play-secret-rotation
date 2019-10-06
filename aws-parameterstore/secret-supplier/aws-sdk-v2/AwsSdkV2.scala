package com.gu.play.secretrotation.aws.parameterstore

import software.amazon.awssdk.services.ssm.SsmClient

import scala.collection.JavaConverters._

case class AwsSdkV2(ssmClient: SsmClient) extends MinimalAwsSdkWrapper{
  override def fetchValues(parameters: Seq[String]): Iterable[ParameterValue] = ssmClient.getParameters(
    _.withDecryption(true).names(parameters.asJavaCollection)
  ).parameters.asScala.map(p => ParameterValue(p.value, Metadata(p.version, p.lastModifiedDate)))
}
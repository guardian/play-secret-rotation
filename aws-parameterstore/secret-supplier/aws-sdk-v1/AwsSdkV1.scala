package com.gu.play.secretrotation.aws.parameterstore

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest

import scala.collection.JavaConverters._

case class AwsSdkV1(ssmClient: AWSSimpleSystemsManagement) extends MinimalAwsSdkWrapper {
  override def fetchValues(parameters: Seq[String]): Iterable[ParameterValue] = ssmClient.getParameters(
    new GetParametersRequest()
      .withWithDecryption(true)
      .withNames(parameters.asJavaCollection)
  ).getParameters.asScala.map(p => ParameterValue(p.getValue, Metadata(p.getVersion, p.getLastModifiedDate.toInstant)))
}
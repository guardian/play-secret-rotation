package com.gu.play.secretrotation.aws.parameterstore

import java.time.Instant

case class Metadata(version: Long, lastModified: Instant)
case class ParameterValue(value: String, metadata: Metadata)

/** This is the minimal functionality we need from AWS SSM ParameterStore,
  * acting as a veneer hiding the differences between v1 & v2 of the AWS SDK for Java,
  * so that we can support both versions of the AWS SDK simultaneously.
  */
trait MinimalAwsSdkWrapper {
  def fetchValues(parameters: Seq[String]): Seq[ParameterValue]
}
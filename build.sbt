import ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

lazy val baseSettings = Seq(
  scalaVersion := "2.13.17",
  crossScalaVersions := Seq(scalaVersion.value, "3.3.6"),
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-release:11"),
  Test / testOptions +=
    Tests.Argument(TestFrameworks.ScalaTest,"-u", s"test-results/scala-${scalaVersion.value}", "-o")
)

val jacksonOverride = "com.fasterxml.jackson.core" % "jackson-core" % "2.20.0"

lazy val core =
  project.settings(baseSettings).settings(
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "5.3.0",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "org.threeten" % "threeten-extra" % "1.8.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    )
  )

lazy val `aws-parameterstore-secret-supplier-base` =
  project.in(file("aws-parameterstore/secret-supplier")).settings(baseSettings).dependsOn(core)

val awsSdkForVersion = Map(
  1 -> "com.amazonaws" % "aws-java-sdk-ssm" % "1.12.793",
  2 -> "software.amazon.awssdk" % "ssm" % "2.32.33"
)

def awsParameterStoreWithSdkVersion(version: Int)=
  Project(s"aws-parameterstore-sdk-v$version", file(s"aws-parameterstore/secret-supplier/aws-sdk-v$version"))
  .settings(baseSettings)
  .dependsOn(`aws-parameterstore-secret-supplier-base`)
  .settings(libraryDependencies += awsSdkForVersion(version))

lazy val `aws-parameterstore-sdk-v1` = awsParameterStoreWithSdkVersion(1)
lazy val `aws-parameterstore-sdk-v2` = awsParameterStoreWithSdkVersion(2)

lazy val `aws-parameterstore-lambda` = project.in(file("aws-parameterstore/lambda"))
  .settings(baseSettings).dependsOn(`secret-generator`).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.4.0",
    "com.amazonaws" % "aws-lambda-java-events" % "3.16.1",
    awsSdkForVersion(1),
    jacksonOverride,
  )
)

lazy val `secret-generator` = project.settings(baseSettings)

val exactPlayVersions = Map(
  "29" -> "com.typesafe.play" %% "play" % "2.9.5",
  "30" -> "org.playframework" %% "play" % "3.0.9"
)

def playVersion(majorMinorVersion: String)= {
  Project(s"play-v$majorMinorVersion", file(s"play/play-v$majorMinorVersion"))
    .settings(baseSettings)
    .dependsOn(core)
    .settings(libraryDependencies ++= Seq(
      exactPlayVersions(majorMinorVersion),
      jacksonOverride,
    ))
}

lazy val `play-v29` = playVersion("29")
lazy val `play-v30` = playVersion("30")


lazy val `play-secret-rotation-root` = (project in file("."))
  .aggregate(
    core,
    `play-v29`,
    `play-v30`,
    `aws-parameterstore-secret-supplier-base`,
    `aws-parameterstore-sdk-v1`,
    `aws-parameterstore-sdk-v2`,
    `secret-generator`,
    `aws-parameterstore-lambda`
  )
  .settings(baseSettings).settings(
  publish / skip := true,
  releaseVersion := fromAggregatedAssessedCompatibilityWithLatestRelease().value,
  releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion
  )
)

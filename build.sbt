import ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

lazy val baseSettings = Seq(
  scalaVersion := "2.13.14",
  crossScalaVersions := Seq(scalaVersion.value, "3.3.3"),
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-release:11"),
  Test / testOptions +=
    Tests.Argument(TestFrameworks.ScalaTest,"-u", s"test-results/scala-${scalaVersion.value}")
)

lazy val core =
  project.settings(baseSettings).settings(
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "5.2.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "org.threeten" % "threeten-extra" % "1.8.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    )
  )

lazy val `aws-parameterstore-secret-supplier-base` =
  project.in(file("aws-parameterstore/secret-supplier")).settings(baseSettings).dependsOn(core)

val awsSdkForVersion = Map(
  1 -> "com.amazonaws" % "aws-java-sdk-ssm" % "1.12.757",
  2 -> "software.amazon.awssdk" % "ssm" % "2.26.12"
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
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
    "com.amazonaws" % "aws-lambda-java-events" % "3.11.6",
    awsSdkForVersion(1)
  )
)

lazy val `secret-generator` = project.settings(baseSettings)

val exactPlayVersions = Map(
  "29" -> "com.typesafe.play" %% "play" % "2.9.2",
  "30" -> "org.playframework" %% "play" % "3.0.4"
)

def playVersion(majorMinorVersion: String)= {
  Project(s"play-v$majorMinorVersion", file(s"play/play-v$majorMinorVersion"))
    .settings(baseSettings)
    .dependsOn(core)
    .settings(libraryDependencies += exactPlayVersions(majorMinorVersion))
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

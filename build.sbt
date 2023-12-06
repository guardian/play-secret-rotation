import ReleaseTransformations.*
import sbtversionpolicy.withsbtrelease.ReleaseVersion.fromAggregatedAssessedCompatibilityWithLatestRelease

lazy val baseSettings = Seq(
  scalaVersion := "2.13.11",
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-unchecked"),
  Test / testOptions +=
    Tests.Argument(TestFrameworks.ScalaTest,"-u", s"test-results/scala-${scalaVersion.value}")
)

lazy val crossCompileScala3 = crossScalaVersions := Seq(scalaVersion.value, "3.3.1")

// Until all dependencies are on scala-java8-compat v1.x, this avoids unnecessary fatal eviction errors
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

lazy val core =
  project.settings(crossCompileScala3, baseSettings).settings(
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "5.2.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "org.threeten" % "threeten-extra" % "1.7.2",
      "org.scalatest" %% "scalatest" % "3.2.16" % Test
    )
  )

lazy val `aws-parameterstore-secret-supplier-base` =
  project.in(file("aws-parameterstore/secret-supplier")).settings(crossCompileScala3, baseSettings).dependsOn(core)

val awsSdkForVersion = Map(
  1 -> "com.amazonaws" % "aws-java-sdk-ssm" % "1.12.606",
  2 -> "software.amazon.awssdk" % "ssm" % "2.20.162"
)

def awsParameterStoreWithSdkVersion(version: Int)=
  Project(s"aws-parameterstore-sdk-v$version", file(s"aws-parameterstore/secret-supplier/aws-sdk-v$version"))
  .settings(crossCompileScala3, baseSettings)
  .dependsOn(`aws-parameterstore-secret-supplier-base`)
  .settings(libraryDependencies += awsSdkForVersion(version))

lazy val `aws-parameterstore-sdk-v1` = awsParameterStoreWithSdkVersion(1)
lazy val `aws-parameterstore-sdk-v2` = awsParameterStoreWithSdkVersion(2)

lazy val `aws-parameterstore-lambda` = project.in(file("aws-parameterstore/lambda"))
  .settings(crossCompileScala3, baseSettings).dependsOn(`secret-generator`).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
    "com.amazonaws" % "aws-lambda-java-events" % "3.11.3",
    awsSdkForVersion(1)
  )
)

lazy val `secret-generator` = project.settings(crossCompileScala3, baseSettings)

val exactPlayVersions = Map(
  "27" -> "com.typesafe.play" %% "play" % "2.7.9",
  "28" -> "com.typesafe.play" %% "play" % "2.8.20",
  "29" -> "com.typesafe.play" %% "play" % "2.9.0",
  "30" -> "org.playframework" %% "play" % "3.0.0"
)

def playVersion(majorMinorVersion: String)= {
  Project(s"play-v$majorMinorVersion", file(s"play/play-v$majorMinorVersion"))
    .settings(baseSettings)
    .dependsOn(core)
    .settings(libraryDependencies += exactPlayVersions(majorMinorVersion))
}


lazy val `play-v27` = playVersion("27")
lazy val `play-v28` = playVersion("28")
lazy val `play-v29` = playVersion("29").settings(crossCompileScala3)
lazy val `play-v30` = playVersion("30").settings(crossCompileScala3)


lazy val `play-secret-rotation-root` = (project in file("."))
  .aggregate(
    core,
    `play-v27`,
    `play-v28`,
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

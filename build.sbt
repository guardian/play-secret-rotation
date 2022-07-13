import ReleaseTransformations._

lazy val baseSettings = Seq(
  scalaVersion := "2.12.15",
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-Xlint", "-unchecked")
)

lazy val crossCompileScala213 = crossScalaVersions := Seq(scalaVersion.value, "2.13.8")

// Until all dependencies are on scala-java8-compat v1.x, this avoids unnecessary fatal eviction errors
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always

lazy val core =
  project.settings(crossCompileScala213, baseSettings).settings(
    libraryDependencies ++= Seq(
      "com.github.blemale" %% "scaffeine" % "5.1.2",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "org.threeten" % "threeten-extra" % "1.7.0",
      "org.scalatest" %% "scalatest" % "3.2.12" % Test
    )
  )

lazy val `aws-parameterstore-secret-supplier-base` =
  project.in(file("aws-parameterstore/secret-supplier")).settings(crossCompileScala213, baseSettings).dependsOn(core)

val awsSdkForVersion = Map(
  1 -> "com.amazonaws" % "aws-java-sdk-ssm" % "1.12.259",
  2 -> "software.amazon.awssdk" % "ssm" % "2.17.189"
)

def awsParameterStoreWithSdkVersion(version: Int)=
  Project(s"aws-parameterstore-sdk-v$version", file(s"aws-parameterstore/secret-supplier/aws-sdk-v$version"))
  .settings(crossCompileScala213, baseSettings)
  .dependsOn(`aws-parameterstore-secret-supplier-base`)
  .settings(libraryDependencies += awsSdkForVersion(version))

lazy val `aws-parameterstore-sdk-v1` = awsParameterStoreWithSdkVersion(1)
lazy val `aws-parameterstore-sdk-v2` = awsParameterStoreWithSdkVersion(2)

lazy val `aws-parameterstore-lambda` = project.in(file("aws-parameterstore/lambda"))
  .settings(crossCompileScala213, baseSettings).dependsOn(`secret-generator`).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
    "com.amazonaws" % "aws-lambda-java-events" % "3.11.0",
    awsSdkForVersion(1)
  )
)

lazy val `secret-generator` = project.settings(crossCompileScala213, baseSettings)

val exactPlayVersions = Map(
  "26" -> "2.6.25",
  "27" -> "2.7.9",
  "28" -> "2.8.11"
)

def playVersion(majorMinorVersion: String)= {
  Project(s"play-v$majorMinorVersion", file(s"play/play-v$majorMinorVersion"))
    .settings(baseSettings)
    .dependsOn(core)
    .settings(libraryDependencies += "com.typesafe.play" %% "play" % exactPlayVersions(majorMinorVersion))
}

lazy val `play-v26` = playVersion("26")
lazy val `play-v27` = playVersion("27").settings(crossCompileScala213)
lazy val `play-v28` = playVersion("28").settings(crossCompileScala213)

lazy val `play-secret-rotation-root` = (project in file("."))
  .aggregate(
    core,
    `play-v26`,
    `play-v27`,
    `play-v28`,
    `aws-parameterstore-secret-supplier-base`,
    `aws-parameterstore-sdk-v1`,
    `aws-parameterstore-sdk-v2`,
    `aws-parameterstore-lambda`
  )
  .settings(baseSettings).settings(
  publishArtifact := false,
  publish := {},
  publishLocal := {},
  releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    // For non cross-build projects, use releaseStepCommand("publishSigned")
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

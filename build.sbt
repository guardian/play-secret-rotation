import ReleaseTransformations._

lazy val baseSettings = Seq(
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-Xlint", "-unchecked")
)

lazy val core =
  project.settings(baseSettings: _*).settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % "2.6.17",
      "org.threeten" % "threeten-extra" % "1.4",
      "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    )
  )

val awsSdkVersion = "1.11.313"

val awsSsm = "com.amazonaws" % "aws-java-sdk-ssm" % awsSdkVersion

lazy val `aws-parameterstore` = project.in(file("aws-parameterstore/state-supplier")).settings(baseSettings: _*).dependsOn(core).settings(
  libraryDependencies += awsSsm
)

lazy val `aws-parameterstore-lambda` = project.in(file("aws-parameterstore/lambda"))
  .settings(baseSettings: _*).dependsOn(`secret-generator`).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
    "com.amazonaws" % "aws-lambda-java-events" % "2.0.2",
    awsSsm
  )
)

lazy val `secret-generator` = project.settings(baseSettings: _*)

lazy val `play-secret-rotation-root` = (project in file("."))
  .aggregate(core, `aws-parameterstore`, `aws-parameterstore-lambda`).
  settings(baseSettings: _*).settings(
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
    releaseStepCommandAndRemaining("+publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
)

assemblyMergeStrategy in assembly := {
  {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
}

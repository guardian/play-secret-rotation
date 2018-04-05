import ReleaseTransformations._

lazy val baseSettings = Seq(
  scalaVersion := "2.12.5",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  organization := "com.gu.play-secret-rotation",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scalacOptions ++= Seq("-deprecation", "-Xlint", "-unchecked")
)

lazy val core =
  project.settings(baseSettings: _*).settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % "2.6.12",
      "com.github.blemale" %% "scaffeine" % "2.5.0",
      "org.threeten" % "threeten-extra" % "1.3.2"
    )
  )

lazy val `aws-parameterstore` = project.settings(baseSettings: _*).dependsOn(core).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.309"
  )
)

lazy val `aws-secretsmanager` = project.settings(baseSettings: _*).dependsOn(core).settings(
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-java-sdk-secretsmanager" % "1.11.309"
  )
)


lazy val `play-secret-rotation-root` = (project in file(".")).aggregate(core, `aws-parameterstore`, `aws-secretsmanager`).
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
    releaseStepCommand("publishSigned"),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  )
)



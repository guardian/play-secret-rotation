sonatypeProfileName := "com.gu"

publishTo in ThisBuild := sonatypePublishTo.value

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/guardian/play-secret-rotation"),
  "scm:git:git@github.com:guardian/play-secret-rotation.git"
))

pomExtra in ThisBuild := (
  <url>https://github.com/guardian/play-secret-rotation</url>
    <developers>
      <developer>
        <id>rtyley</id>
        <name>Roberto Tyley</name>
        <url>https://github.com/rtyley</url>
      </developer>
    </developers>
  )
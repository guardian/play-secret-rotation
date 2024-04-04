play-secret-rotation
=========

_Rotating your [Application Secret](https://www.playframework.com/documentation/2.8.x/ApplicationSecret)
on an active cluster of Play app servers_

[![Release](https://github.com/guardian/play-secret-rotation/actions/workflows/release.yml/badge.svg)](https://github.com/guardian/play-secret-rotation/actions/workflows/release.yml)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v30/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v30/)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v29/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v29/)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v28/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v28/)

Detailed docs on how to use this library:

* [Using AWS Parameter Store](aws-parameterstore/README.md)
* ...but not with _AWS Secrets Manager_, because
  [surprisingly it doesn't suit the use-case very well](https://github.com/guardian/play-secret-rotation/commit/01e7fa86688).

play-secret-rotation
=========

_Rotating your [Application Secret](https://www.playframework.com/documentation/2.9.x/ApplicationSecret)
on an active cluster of Play app servers - without downtime_

[![Release](https://github.com/guardian/play-secret-rotation/actions/workflows/release.yml/badge.svg)](https://github.com/guardian/play-secret-rotation/actions/workflows/release.yml)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v30/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v30/)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v29/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v29/)

#### How to use this Play add-on

You'll need to choose a secure data-store for holding your Application Secret:

* [**AWS Parameter Store**](aws-parameterstore/README.md) - fully supported
* not _AWS Secrets Manager_, because surprisingly it
  [doesn't suit the use-case very well](https://github.com/guardian/play-secret-rotation/commit/01e7fa86688).
* anything else... you'll need to write your own implementation of `com.gu.play.secretrotation.SnapshotProvider`

Then:

* Update your Play server to read the rotating secrets from the data store with [`RotatingSecretComponents`](aws-parameterstore/README.md#updating-applicationcomponents-with-the-rotating-secret)
* Setup a periodic job to update your secret in the secure data-store, eg with [`com.gu.play.secretrotation.aws.parameterstore.Lambda`](aws-parameterstore/README.md#secret-updating-lambda)


#### Could Play add direct support for Application Secret rotation?

...see https://github.com/playframework/playframework/issues/12520

play-secret-rotation
=========

_Rotating your [Application Secret](https://www.playframework.com/documentation/2.6.x/ApplicationSecret)
on an active cluster of Play app servers_

More docs on how to do this:

* [Using AWS Parameter Store](aws-parameterstore/README.md)
* ...but not with _AWS Secrets Manager_, because
  [surprisingly it doesn't suit the use-case very well](https://github.com/guardian/play-secret-rotation/commit/01e7fa86688).

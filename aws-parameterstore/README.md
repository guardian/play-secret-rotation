Using AWS Parameter Store for Play Secret Rotation
=======

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v2/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v2/)

[![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v1/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v1/)

There are three parts to this:

* [Create the parameter](#create-the-aws-parameter-to-hold-the-secret) in AWS Parameter Store to hold the secret
* [Update your Play server](#play-server) to read the rotating secrets from AWS Parameter Store
* [Install an AWS Lambda](#secret-updating-lambda) to update the secret on a regular basis

#### Create the AWS Parameter to hold the Secret

As an example we'll use an AWS Parameter called `/Example/PlayAppSecret` - create your own
AWS Parameter to hold the Application Secret, using a type of `SecureString` and whichever
KMS key you want to use:

![image](https://user-images.githubusercontent.com/52038/39054128-b6dd60b6-44a8-11e8-9cf2-2137bc3a3361.png)

Every time you update this Parameter, your Play app servers will fetch the new secret state
as soon as their short-lived caches expire. After the `usageDelay` has passed, they will
start to sign cookies using the new secret, but will continue to accept cookies signed
with the old secret until `overlapDuration` has passed.

#### Play server

The Play Server is only responsible for _reading_ the updates of the Application Secret - it
doesn't update the secret itself. The state of the Application Secret (the old & new secrets,
and when to begin switching over between the two) is fetched from AWS Parameter Store and cached
with a short-lifetime, to ensure that soon after the AWS Parameter containing the secret is updated,
all app servers are ready to begin using it.

##### Dependencies

You'll need to add two library dependencies for `com.gu.play-secret-rotation` - one dependency specific
to your Play version, and another specific to your AWS SDK version:

* **Play** ... [![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v28/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v28/)
  [![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v27/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v27/)
  [![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/play-v26/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/play-v26/)
* **AWS SDK** ([v1 or v2](https://docs.aws.amazon.com/sdk-for-java/latest/migration-guide/what-is-java-migration.html)) ... [![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v2/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v2/)
  [![play-secret-rotation artifacts](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v1/latest-by-scala-version.svg)](https://index.scala-lang.org/guardian/play-secret-rotation/aws-parameterstore-sdk-v1/)

So, for example:

```scala
libraryDependencies ++= Seq(
  "com.gu.play-secret-rotation" %% "play-v28" % "0.x",
  "com.gu.play-secret-rotation" %% "aws-parameterstore-sdk-v2" % "0.x",
)
```

##### Updating `ApplicationComponents` with the rotating secret

In your `ApplicationComponents`, mix-in `RotatingSecretComponents` and provide the `secretStateSupplier`
required by that trait:

```scala
import com.gu.play.secretrotation._

val secretStateSupplier: SnapshotProvider = {
  import com.gu.play.secretrotation.aws.parameterstore

  new parameterstore.SecretSupplier(
    TransitionTiming(usageDelay = ofMinutes(3), overlapDuration = ofHours(2)),
    "/Example/PlayAppSecret",
    parameterstore.AwsSdkV1(AWSSimpleSystemsManagementClientBuilder.defaultClient())
  )
}
```

_Note that you'll probably have to define credentials/region on the `AWSSimpleSystemsManagementClient`_.

Your Play app servers will need an IAM policy like this in order
to read the secret 'state':

```yaml
- Effect: Allow
  Action: ssm:GetParameters
  Resource: 'arn:aws:ssm:eu-west-1:111222333444:parameter/Example/PlayAppSecret'
- Effect: Allow
  Action: kms:Decrypt
  Resource: 'arn:aws:kms:eu-west-1:111222333444:key/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
```

#### Secret-Updating Lambda

You don't have to do this step straight away! You've already got a
fair bit of benefit from being able to update your Application Secret
without downtime, and you should check that your Play servers are
operating well by testing with a manual update of the Parameter
value before you continue.

Once you're happy that manual updates are working, you can start
automatic scheduled updates with an AWS Lambda: download the [latest published jar](https://search.maven.org/remote_content?g=com.gu.play-secret-rotation&a=aws-parameterstore-lambda_2.12&v=LATEST)
for the AWS Lambda.

Set the Lambda Function code `Handler` to this value:

```
com.gu.play.secretrotation.aws.parameterstore.Lambda::lambdaHandler
```

Set the Lambda Environment variable `PARAMETER_NAME` to the name of the
parameter that contains the secret (in this example _'Example/PlayAppSecret'_).

Set the Lambda Execution role to have a policy like this:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:DescribeParameters",
      "Resource": "arn:aws:ssm:eu-west-1:111222333444:*"
    },
    {
      "Effect": "Allow",
      "Action": "ssm:PutParameter",
      "Resource": "arn:aws:ssm:eu-west-1:111222333444:parameter/Example/PlayAppSecret"
    },
    {
      "Effect": "Allow",
      "Action": "kms:Encrypt",
      "Resource": "arn:aws:kms:eu-west-1:111222333444:key/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    },
    {
      "Effect": "Allow",
      "Action": ["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

Finally, use an AWS CloudWatch Scheduled Event to trigger the Lambda to run at regular intervals.
The Lambda should not run more often than the `overlapDuration` defined in the `secretStateSupplier`
in your Play Server - every 6 hours with a 2 hour overlap will probably work well.

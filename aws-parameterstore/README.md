Using AWS Parameter Store for Play Secret Rotation
=======

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-secret-rotation/aws-parameterstore_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-secret-rotation/aws-parameterstore_2.12)

#### Play server

The Play Server is only responsible for _reading_ the updates of the Application Secret - it
doesn't update the secret itself. The state of the Application Secret (the old & new secrets,
and when to begin switching over between the two) is fetched from AWS Parameter Store and cached
with a short-lifetime, to ensure that soon after the AWS Parameter containing the secret is updated,
all app servers are ready to begin using it.

Add the library dependency:

```scala
libraryDependencies += "com.gu.play-secret-rotation" %% "aws-parameterstore" % "0.7"
```

In your `ApplicationComponents`, mix-in `RotatingSecretComponents` and provide the `secretStateSupplier`
required by that trait:

```scala
  val secretStateSupplier: Supplier[SecretState] = new ParameterStore.SecretSupplier(
    TransitionTiming(usageDelay = Duration.ofMinutes(3), overlapDuration = Duration.ofHours(2)),
    parameterName = "/Example/PlayAppSecret",
    ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient()
  )
```

_Note that you'll probably have to define credentials/region on the `AWSSimpleSystemsManagementClient`_.

Your Play app servers will need an IAM policy like this in order
to read the secret 'state':

```yaml
- Effect: Allow
  Action: ssm:DescribeParameters
  Resource: 'arn:aws:ssm:eu-west-1:111222333444:*'
- Effect: Allow
  Action: ssm:GetParameters
  Resource: 'arn:aws:ssm:eu-west-1:111222333444:parameter/Example/PlayAppSecret'
- Effect: Allow
  Action: kms:Decrypt
  Resource: 'arn:aws:kms:eu-west-1:111222333444:key/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
```

#### Create your own AWS Parameter to hold the Application Secret

The AWS Parameter in this example is called `/Example/PlayAppSecret` - create your own
AWS Parameter to hold the Application Secret, using a type of `SecureString` and whichever
KMS key you want to use:

![image](https://user-images.githubusercontent.com/52038/39054128-b6dd60b6-44a8-11e8-9cf2-2137bc3a3361.png)

Every time you update this Parameter, your Play app servers will fetch the new secret state
as soon as their short-lived caches expire. After the `usageDelay` has passed, they will
start to sign cookies using the new secret, but will continue to accept cookies signed
with the old secret until `overlapDuration` has passed.

#### Secret-Updating Lambda

Set the Lambda Function code `Handler` to this value:

```
com.gu.play.secretrotation.aws.ParameterStoreLambda::lambdaHandler
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

Finally, use a AWS CloudWatch Scheduled Event to trigger the Lambda to run at regular intervals.
The Lambda should not run more often than the `overlapDuration` defined in the `secretStateSupplier`
in your Play Server - every 6 hours with a 2 hour overlap will probably work well.

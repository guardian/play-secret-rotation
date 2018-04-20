Using AWS Parameter Store for Play Secret Rotation
=======

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.gu.play-secret-rotation/aws-parameterstore_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.gu.play-secret-rotation/aws-parameterstore_2.12)

#### Play server

Add the library dependency:

```
libraryDependencies += "com.gu.play-secret-rotation" %% "aws-parameterstore" % "0.7"
```

In your `ApplicationComponents`, mix-in `RotatingSecretComponents` and provide the `secretStateSupplier`
required by that trait:

```
  val secretStateSupplier: Supplier[SecretState] = new ParameterStore.SecretSupplier(
    TransitionTiming(usageDelay = Duration.ofMinutes(3), overlapDuration = Duration.ofHours(2)),
    parameterName = "/Example/PlayAppSecret",
    ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient()
  )
```

_Note that you'll probably have to define credentials/region on the `AWSSimpleSystemsManagementClient`_.

Your Play app servers will need an IAM policy like this in order
to read the secret 'state':

```
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

#### Secret-Updating Lambda

Set the Lambda Function code Handler to this value:

```
com.gu.play.secretrotation.aws.ParameterStoreLambda::lambdaHandler
```

Set the Lambda Environment variable `PARAMETER_NAME` to the name of the
parameter that contains the secret (in this example _'Example/PlayAppSecret'_).

Set the Lambda Execution role to have a policy like this:

```
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



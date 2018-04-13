Using AWS Parameter Store for Play Secret Rotation
=======

##### IAM Policies

###### Play app server IAM Policy

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

###### Secret-Updating Lambda IAM Policy

Your Play app servers will need an IAM policy like this in order
to read the encrypted Secret parameter:

```
- Effect: Allow
  Action: ssm:DescribeParameters
  Resource: 'arn:aws:ssm:eu-west-1:111222333444:*'
- Effect: Allow
  Action: ssm:PutParameter
  Resource: 'arn:aws:ssm:eu-west-1:111222333444:parameter/Example/PlayAppSecret'
- Effect: Allow
  Action: kms:Encrypt
  Resource: 'arn:aws:kms:eu-west-1:111222333444:key/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
```



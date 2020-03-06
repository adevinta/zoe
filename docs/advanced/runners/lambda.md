# Lambda runner

The lambda runner triggers lambda functions to read / produce to the Kafka clusters.

The lambda function needs to be deployed beforehand using the zoe CLI. The deployment context of the lambda function is configured in the `lambda` section of the runners config :

```yaml
runners:
  default: lambda
  config:
    lambda:
      deploy:
        subnets: ["subnet-xxxxxxx"]
        securityGroups: ["sg-yyyyyyy"]
        memory: 512
        timeout: 500
      credentials:
        type: "profile"
        name: "spt-tranquility-pre"
      awsRegion: eu-west-1
```

Before using the lambda runner, the lambda function needs to be deployed using zoe :

```bash
zoe -v lambda deploy
```

Zoe deploys the lambda function in 2 steps :

1. It deploys a minimal set of resources needed by the lambda using CloudFormation (an S3 bucket, AWS roles, etc.). For more details on the resources deployed, checkout the [cloudformation template](https://github.com/adevinta/zoe/blob/master/zoe-cli/resources/lambda.infra.cf.json).
2. It deploys the lambda function itself by uploading the local Zoe JAR into AWS and registering the lambda within AWS.

Once the lambda function deployed, you can use the lambda runner to interact with your cluster :

```bash
zoe -c my-cluster -r lambda topics consume input -n 10
```

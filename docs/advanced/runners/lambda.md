# Lambda runner

The lambda runner triggers lambda functions to read / produce to the Kafka clusters.

The lambda function needs to be deployed beforehand using the zoe CLI. The deployment context of the lambda function needs to be configured in the `lambda` section of the runners config :

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
        name: "my-profile"
      awsRegion: eu-west-1
```

To the deploy the lambda function using the configured deployment context:

```bash
zoe -v lambda deploy
```

Zoe deploys the lambda function in 2 steps :

1. It deploys a minimal set of resources needed by the lambda using CloudFormation (an S3 bucket, AWS roles, etc.). For more details on the resources deployed, checkout the [cloudformation template](https://github.com/adevinta/zoe/blob/master/zoe-cli/resources/lambda.infra.cf.json).
2. It deploys the lambda function itself by uploading the local Zoe JAR into AWS and registering the lambda within AWS. Zoe jar path needs to be set and must point to a valid zoe core jar downloaded beforehand (Todo: a guide for this).

Once the lambda function is deployed, you can use the lambda runner to interact with your cluster :

```bash
zoe -c my-cluster -r lambda topics consume input -n 10
```

## Supported credentials

The authentication method is configured in the credentials section of the lambda runner config. 3 Authentication types are supported :

1. Default: this will use the default AWS credentials chain to authenticate with AWS.

    ```yaml
    credentials:
    type: "default"
    ```

2. Profile: uses the configured profile to authenticate with AWS. The chosen profile needs to be configured in `~/.aws/credentials`.

    ```yaml
    credentials:
       type: "profile"
       name: "my-profile"
    ```

3. Static: uses the statically set keys to authenticate with AWS. The keys themselves can be secrets resolved at runtime by the [secrets provider](../secrets/overview.md).

    ```yaml
    credentials:
       type: "static"
       accessKey: "accessKey"
       secretAccessKey: "secretAccessKey"
    ```

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

## How to deal with an unsupported authentication method

There are currently a bunch of other authentication methods that zoe doesn't support yet. Few examples are: MFA in the command line, the new AWS Single Sign On, etc.

If you are dealing with such an unsupported authentication method, you can easily workaround it by generating an STS session and let zoe make use it. To make the process seamless, you can use the awesome [AWS Vault](https://github.com/99designs/aws-vault) that makes it almost transparent. 

For zoe to be able to discover STS sessions, you need to use the default credentials type:

```text
runners:
  default: lambda
  config:
    lambda:
      credentials:
        type: "default"
      ...
```

You can use [AWS Vault](https://github.com/99designs/aws-vault) to make generating STS sessions transparent with zoe by using:

```bash tab="command"
aws-vault exec my-aws-profile-with-sso -- zoe topics list
```

You can even simplify the process further by creating an alias:

```bash
# Create an alias
alias zoe-pro='aws-vault exec my-aws-profile-with-sso -- zoe'

# Use it here
zoe-pro topics list
```

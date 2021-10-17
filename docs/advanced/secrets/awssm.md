# AWS Secrets manager provider

This secrets provider fetches secret values from [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html).

To use the AWS Secrets Manager provider, add the following in zoe's configuration :

```yaml
secrets:
  provider: awsSecretsManager
  region: eu-west-1  # Optional
```

Using the configuration above, when zoe encounters `secret:JAAS_CONFIG`, it looks up a secret named `JAAS_CONFIG` from AWS Secrets Manager.

## Supported credentials

The AWS Secrets Manager provider needs to authenticate to AWS. By default, it uses the AWS default credentials chain. But this can be overridden in the `credentials` section of the provider config. The same credentials types as the [lambda credentials](../runners/lambda/#supported-credentials) are supported:

=== "Profile credentials"

    ```yaml
    secrets:
      provider: "awsSecretsManager"
      region: "eu-west-1"
      credentials:
        type: "profile"
        name: "my-customr-profile"
    ```

=== "static"

    ```yaml
    secrets:
      provider: "awsSecretsManager"
      region: "eu-west-1"
      credentials:
        type: "static"
        accessKey: "accessKey"
        secretAccessKey: "secretAccessKey"
    ```

If your authentication mode is not supported, you can generate an STS session and use the "default" credentials so that zoe uses this STS session. You can take a look at [the lambda runner credentials section](../runners/lambda.md) to know more about how to achieve that.

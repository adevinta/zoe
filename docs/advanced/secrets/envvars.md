# Environment variables secrets provider

This secrets provider looks up secret values from the environment.

When zoe encounters `secret:SECRET_NAME` in the configuration, this provider searches for an environment variable named `SECRET_NAME`.

The name of the environment variable to look for can be altered using the `append` or `prepend` config properties :

```yaml
secrets:
    provider: env
    append: ZOE_SECRET
    prepend: _PRO
```

In the configuration above, when zoe encounters `secret:JAAS_CONFIG`, this provider looks up for an environment variable named `ZOE_SECRET_JAAS_CONFIG_PRO`.

The `append` and `prepend` properties are useful when using the same secret name across different zoe [environment files](../../configuration/environments.md). Here is a typical example of a `staging.yml` and a `prod.yml` file:

=== "staging.yml"

    ```yaml
    secrets:
        provider: env
        append: ZOE_SECRET
        prepend: _STAGING
    
    clusters:
    
      my-kafka-cluster:
        props:
          bootstrap.servers: my-kafka-cluster-staging.example.com:9092
          security.protocol: SASL_SSL
          sasl.mechanism: SCRAM-SHA-256
          sasl.jaas.config: secret:JAAS_CONFIG
    ```

=== "prod.yml"

    ```yaml
    secrets:
        provider: env
        append: ZOE_SECRET
        prepend: _PROD
    
    clusters:
    
      my-kafka-cluster:
        props:
          bootstrap.servers: my-kafka-cluster-production.example.com:9092
          security.protocol: SASL_SSL
          sasl.mechanism: SCRAM-SHA-256
          sasl.jaas.config: secret:JAAS_CONFIG
    ```

In this case :

- when using `zoe -e staging ...`, the `JAAS_CONFIG` secret wil be retrieved from the environment variable `ZOE_SECRET_JAAS_CONFIG_STAGING`.
- when using : `zoe -e prod ...`, the `JAAS_CONFIG` secret wil be retrieved from the environment variable `ZOE_SECRET_JAAS_CONFIG_PROD`.

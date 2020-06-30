# Secrets providers

Zoe allows you to use secrets in the configuration files without exposing their values. The secret values are resolved at runtime by zoe via secrets providers. This mechanism makes configuration files more easily shareable among the team using git repositories.

Secrets in zoe's configuration files usually take the form of `secret:SECRET_NAME`. Some providers can also require the longer form of `secret:CONTEXT:SECRET_NAME` to provide an additional context to secrets provider. 

A typical use case for using secrets and secrets providers is when dealing with a kafka cluster protected behind a SASL authentication. In this scenario, to interact with the cluster (to consume or produce data) we need to supply credentials in the clients properties (Notice the `sasl.jaas.config`):

```yaml
clusters:

  my-kafka-cluster:
    props:
      bootstrap.servers: my-kafka-cluster.example.com:9092
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: org.apache.kafka.common.security.scram.ScramLoginModule required username="my-ser" password="my-password";
      ...

...
```

Storing credentials that way in the configuration file makes it not easily shareable among the team in git repositories or kubernetes config maps.

To improve this, we can use zoe's support for secrets:

```yaml
clusters:

  my-kafka-cluster:
    props:
      bootstrap.servers: my-kafka-cluster.example.com:9092
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
      sasl.jaas.config: secrets:JAAS_CONFIG
      ...

secrets:
    provider: env
...
```

When zoe encounters `secrets:JAAS_CONFIG`, it uses the configured secrets provider to search for a secret named `JAAS_CONFIG` and uses its value to replace `secret:JAAS_CONFIG`.

Zoe currently supports the following secrets provider (more may be supported in the future) :

- [Environment variables secrets provider](envvars.md): Looks up secret values from the environment.
- [Strongbox secrets provider](strongbox.md): Uses strongbox to fetch secret values.
- [AWS Secrets Manager provider](awssm.md): Fetches secrets from AWS Secrets Manager.
- [Exec provider](exec.md): Uses a custom script to resolve secrets. Use this provider if none of the above providers fits your use case.

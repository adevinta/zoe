# Command based secret provider

If none of the supported secrets provider fits your use case, Zoe provides a generic secrets provider that delegates
secrets deciphering to a custom command.

You can use this provider by adding the following to your zoe config:

```yaml
secrets:
  provider: exec
  command: [ "command", "{secretName}" ]
  timeoutMs: 30000  # default is: 60000
```

This provider catches all secrets in your configuration properties that are in the format: `secret:SECRET_NAME`
or `secret:CONTEXT:SECRET_NAME` and allows you to use the captured `SECRENT_NAME` and `CONTEXT` fields in your command
by using the `{secretName}` and `{context}` pattern (cf. The example above).

## Example

Suppose you have the following configuration:

=== "default.yml"

    ```yaml
    secrets:
        provider: exec
        command: [ "/fetch-secret.sh", "{context}", "{secretName}" ]
    
    clusters:
    
      my-kafka-cluster:
        props:
          bootstrap.servers: my-kafka-cluster-staging.example.com:9092
          security.protocol: SASL_SSL
          sasl.mechanism: SCRAM-SHA-256
          sasl.jaas.config: secret:pro:JAAS_CONFIG
    ```

When using the `zoe -c my-kafka-cluster consume topics -n 10`, zoe will detect the `secret:pro:JAAS_CONFIG` string and
tries to replace with the output of the following command:

```bash
/fetch-secret.sh "PRO" "JAAS_CONFIG"
```

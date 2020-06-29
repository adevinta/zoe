# Command based secret provider

If none of the supported secrets provider fits your use case, Zoe provides a generic secrets provider that delegates secrets deciphering to a custom command.

You can use this provider by adding the following to your zoe config:

```yaml
secrets:
    provider: exec
    command: /path/to/your/script
    timeoutMs: 30000  # default is: 60000
```

This provider catches all secrets in the format: `secret:SECRET_NAME` or `secret:CONTEXT:SECRET_NAME`. For each intercepted secret, the command provided is called with the following arguments:

- `$1` should contain the secret name (in the example above it would be `SECRET_NAME`).
- `$2` should contain the context if it is available. For example, if the intercepted secret is:
    - `secret:CONTEXT:SECRET_NAME`: then `$2` would contain the value `CONTEXT`
    - `secret:SECRET_NAME`: then `$2` would be empty.

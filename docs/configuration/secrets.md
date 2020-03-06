# Secrets

Zoe allows us to refer to secrets into the configuration files without exposing their values. The secret values are resolved at runtime by zoe via secrets providers. This mechanism allows us to easily share configuration files among the team using git repositories.

Secrets provider are configured in the `secrets` section of the configuration :

```yaml
...

secrets:
    provider: env

...
```

Visit the [Secrets Providers dedicated section](../advanced/secrets/overview.md) for more details on secrets providers are configured.

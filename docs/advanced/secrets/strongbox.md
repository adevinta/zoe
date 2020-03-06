# Strongbox secrets provider

This secrets provider fetches secret values from [strongbox](https://github.com/schibsted/strongbox).

To use the strongbox secrets provider, add the following zoe's configuration :

```yaml
secrets:
  provider: strongbox
  region: eu-west-1
  group: my-group
```

When zoe encounters `secret:JAAS_CONFIG`, the strongbox secrets provider looks up a secret named `JAAS_CONFIG` from strongbox that belongs to the group named `my-group`.

The strongbox secrets provider uses the AWS default credentials chain by default to authenticate with AWS. This can be overridden in the provider's config :

```yaml tab="profile"
secrets:
  provider: "strongbox"
  region: "eu-west-1"
  group: "my-group"
  credentials:
    type: "profile"
    name: "my-customr-profile"
```

```yaml tab="static"
secrets:
  provider: "strongbox"
  region: "eu-west-1"
  group: "my-group"
  credentials:
    type: "static"
    accessKey: "accessKey"
    secretAccessKey: "secretAccessKey"
```

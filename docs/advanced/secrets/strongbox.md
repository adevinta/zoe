# Strongbox secrets provider

This secrets provider fetches secret values from [strongbox](https://github.com/schibsted/strongbox).

To use the strongbox secrets provider, add the following in zoe's configuration :

```yaml
secrets:
  provider: strongbox
  region: eu-west-1
  group: my-group
```

Using the configuration above, when zoe encounters `secret:JAAS_CONFIG`, the strongbox secrets provider looks up a secret named `JAAS_CONFIG` that belongs to the group named `my-group` from strongbox.

## Supported credentials

The strongbox secrets provider needs to authenticate to AWS. By default, it uses the AWS default credentials chain. But this can be overridden in the `credentials` section of the strongbox provider config. The same credentials types are supported as the [lambda credentials](../runners/lambda/#supported-credentials):

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

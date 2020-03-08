# Environments

Zoe allows us to separate clusters configuration into multiple environments. This can be achieved by creating a dedicated yaml file for each environment in zoe's configuration directory `~/.zoe/config`.

A typical use case is when dealing with a development, a staging and a production kafka cluster. In this case, we can create 3 configuration files :

```text
~/.zoe/config
├── dev.yml
├── staging.yml
└── prod.yml
```

We can then refer to a specific environment using `--env` or `-e` option :

```bash
zoe --env staging -c my-kafka topics consume input -n 10
```

In this case, Zoe will use the `~/.zoe/config/staging.yml` configuration file.

By default, when `--env` is not specified, zoe will use a default environment called `default` and thus uses the `~/.zoe/config/default.yml`.

Choosing an environment can also be done using an environment variable called `ZOE_ENV` :

```bash
export ZOE_ENV=pro
zoe -c my-kafka topics consume input -n 10
```

# Configuring zoe

Zoe relies on a pre-set configuration to infer most of the parameters at usage time when interacting with a cluster using the CLI. This is one of the key points that makes zoe easy to use.

Zoe loads its configuration from a variety of sources. It also has a complex override chain that makes it highly configurable even in the context of containers.

In this guide, we will discover the following points :

- [Environments](environments.md): We will see how zoe allows us to separate clusters' configuration into multiple environments and how we can select the appropriate environment when using zoe.
- [Configuration loading chain](chain.md): We will then tackle zoe's complex configuration loading chain and how configuration values can be overridden with environment variables.
- [Secrets](secrets.md): Zoe supports storing secrets inside its configuration files to make them more easily shareable (for example using a git repository). Secret values will be loaded at runtime by zoe using secret providers.
- [Configuration reference](reference.md): This section contains the list of all possible values in zoe's configuration file.

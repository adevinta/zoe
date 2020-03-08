# Kubernetes runner

The kubernetes runner launches the consumer / producer processes as pods in a remote kubernetes cluster.

This runner needs to be configured in the `runners.config.kubernetes` section in zoe's configuration file to target your existing kubernetes cluster.

In order to locate and authenticate with the remote cluster, Zoe relies on the usual kube config file that is usually in `~/.kube/config`. It uses the current context by default unless set otherwise in the configuration. It's also possible to configure the pods memory / cpu limits. Here is a complete configuration for the kubernetes runner:

```yaml
runners:
  default: kubernetes
  config:
    kubernetes:
      # Context to use. Optional: By default, zoe uses the current context set in the kube config file.    
      context: mu-kube-context
      # Namespace to use. Optional: By default, zoe uses the 'default' namespace.
      namespace: env-staging
      # Delete pods after completion ?
      deletePodAfterCompletion: true
      # CPU limits
      cpu: "1"
      # Memory limits
      memory: "512M"
      # Timeout for the client operations.
      timeoutMs: 300000
```

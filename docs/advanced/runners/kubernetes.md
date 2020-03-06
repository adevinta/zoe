# Kubernetes runner

The kubernetes runner launches the consumer / producer processes as pods in kubernetes. Zoe uses a docker image with the same version as the CLI to spin up the pods.

Zoe uses the default local kube config file to pick the credentials and information about the remote kubernetes cluster. The target kubernetes context as well as the pods memory / cpu limits are set in the `kubernetes` runner's section :

```yaml
runners:
  default: kubernetes
  config:
    kubernetes:
      context: mu-kube-context
      namespace: env-staging
      deletePodAfterCompletion: true
      cpu: "1"
      memory: "512M"
      timeoutMs: 300000
```

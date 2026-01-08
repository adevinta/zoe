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
      # Annotations to attach to the pods. Optional: By default, empty
      annotations:
        key1: value1
        key2: value2
      # Service Account name. Optional: By default, pods use the 'default' service account.
      # Useful for AWS IRSA (IAM Roles for Service Accounts) or other pod identity mechanisms.
      serviceAccountName: zoe-service-account
```

## AWS IRSA Support

Zoe supports [AWS IAM Roles for Service Accounts (IRSA)](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html) on EKS clusters. This allows Zoe pods to authenticate to AWS services (like MSK) without requiring AWS credentials to be stored in configuration files or environment variables.

To use IRSA with Zoe:

1. **Create an IAM role** with the necessary permissions (e.g., MSK access)
2. **Set up the OIDC provider** for your EKS cluster (usually done during cluster creation)
3. **Create a Kubernetes Service Account** with the `eks.amazonaws.com/role-arn` annotation:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: zoe-service-account
  namespace: zoe-namespace
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/ZoeKafkaAccess
```

4. **Configure Zoe** to use the service account:

```yaml
runners:
  default: kubernetes
  config:
    kubernetes:
      namespace: zoe-namespace
      serviceAccountName: zoe-service-account
      # ... other configuration
```

When configured this way, Zoe pods will automatically receive temporary AWS credentials and can access AWS services according to the IAM role's permissions.

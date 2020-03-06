# Zoe runners

Zoe offloads the interaction workload with the kafka clusters to runners. One of zoe's most powerful feature is its ability to use remote runners (lambda functions or kubernetes pods) instead of running the workloads on the local machine.

Runners can be selected using the `--runner` or `-r` option :

```bash
zoe -c my-cluster --runner kubernetes topics consume input -n 10
```

The command above offloads the consumption of the topic to a kubernetes pod. The result will seamlessly be output by the CLI as if the runner was local.

Zoe currently supports the following runners :

- [Local runner](local.md): Launches the consumers / producers on the same process as the zoe CLI.
- [Kubernetes runner](kubernetes.md): Launches the consumers / producers as pods on a target kubernetes cluster.
- [Lambda runner](lambda.md): Launches the consumers / producers on lambda functions pre-deployed using zoe.

The runners configuration can be set in zoe's configuration in the `runners` section :

```yaml
runners:
  default: lambda
  config:
    kubernetes:
      context: mu-kube-context
      namespace: env-staging
    lambda:
      deploy:
        subnets: ["subnet-xxxxxxx"]
        securityGroups: ["sg-yyyyyyy"]
        memory: 512
        timeout: 500
      credentials:
        type: "profile"
        name: "spt-tranquility-pre"
      awsRegion: eu-west-1
```

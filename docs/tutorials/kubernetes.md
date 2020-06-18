# Zoe with kubernetes

In this tutorial, we will learn how to use Zoe with a Kubernetes runner. We will use it against a Kafka cluster deployed in Kubernetes.

## Prerequisites

- Install Zoe (Install instructions [here](https://adevinta.github.io/zoe/install/overview/))
- kubectl and a working kubernetes cluster (cf. below for more instructions on how to install a kubernetes cluster)

## Prepare the environment

### Kubernetes setup

In this tutorial, you will need [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl) and a working kubernetes cluster that you can use to test Zoe. Several solutions exist to install a kubernetes cluster locally. The most common ones are: [Minikube](https://kubernetes.io/docs/setup/learning-environment/minikube) and [MicroK8s](https://microk8s.io/).

No matter how you installed your local cluster (or if you are using a remote one), make sure your current context in your kube config file is pointing to that cluster (you can explicitly configure zoe to use a [specific context and a specific namespace](https://adevinta.github.io/zoe/advanced/runners/kubernetes/) but for the sake of simplicity we left this part out of this tutorial). To ensure this is the case, you can use the following command:

```bash tab="command"
kubectl cluster-info
```

```bash tab="output"
Kubernetes master is running at https://127.0.0.1:16443
CoreDNS is running at https://127.0.0.1:16443/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy

To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
```

In this, I am using microk8s. The output may be different on your machine.

### Kafka cluster setup

Some kubernetes manifest files have been provided in Zoe's repository to install a single node Kafka cluster. Let's use them:
 
- clone the repository: `git clone https://github.com/adevinta/zoe.git`
- go to the kubernetes tutorial directory: `cd zoe/tutorials/kubernetes`
- apply the manifests: `kubectl apply --prune --selector='context=zoe-demo' -f resources`

This will install a Zookeeper and a Kafka node. You can watch the readiness of the pods using:

```bash tab="command"
kubectl get all -l context=zoe-demo
```

```bash tab="output"
NAME                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)              AGE
service/broker      ClusterIP   10.152.183.151   <none>        9092/TCP,29092/TCP   44m
service/zookeeper   ClusterIP   10.152.183.157   <none>        2181/TCP             44m

NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/broker      1/1     1            1           44m
deployment.apps/zookeeper   1/1     1            1           44m
```

### Configure zoe to point to that cluster

This part of the tutorial and the following ones assume you are in the `zoe/tutorials/kubernetes` folder of the repository.

The repository already provides an example zoe configuration file that can be used against against this Kafka cluster we have just created in kubernetes. All you need to do is to make zoe use this config file. This file is in the `config` folder of the [kubernetes tutorial](https://github.com/adevinta/zoe/tree/master/tutorials/kubernetes).

To make zoe use this configuration file, you can use the `ZOE_CONFIG_DIR` environment variable:

```bash tab="command"
export ZOE_CONFIG_DIR=$(pwd)/tutorials/kubernetes/config
```

This makes zoe point to this config directory instead of the default one (`~/.zoe/config`).

Nothing fancy in this configuration file, just notice the `bootstrap.servers` pointing to `broker:9092`. Keep in mind that in this tutorial we will be using the Kubernetes runner which launches the consumers / producers as pods in this cluster. So we can use the broker service name to point to the kafka pod. Besides that, this config file also defines the `kubernetes` runner as the default one. 

```bash tab="tutorials/kubernetes/config/default.yml"
clusters:
  kafka-in-k8s:
    props:
      bootstrap.servers: "broker:9092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

runners:
  default: "kubernetes"
```

Zoe is now ready to be used against this cluster!

## Interact with the cluster

Produce some data from stdin (notice the `--runner` option in the command):

```bash tab="command"
echo '[{"msg": "hello world"}]' | zoe --runner kubernetes -v -c kafka-in-k8s topics produce --topic input --from-stdin
```

```json tab="output"
{
  "produced": [
    {
      "offset": 0,
      "partition": 0,
      "topic": "input",
      "timestamp": 1583871328050
    }
  ],
  "skipped": []
}
```

```text tab="logs"
2020-03-10 21:15:20 INFO zoe: loading config from url : file:/home/zoe-user/mine/projects/work/zoe/tutorials/kubernetes/config/default.yml
2020-03-10 21:15:21 INFO zoe: producing '1' records to topic 'input'
```

If you want to see what's going on behind the scenes, try the same command with a more verbose mode using `-vv`:

```bash tab="command"
echo '[{"msg": "hello world"}]' | zoe --runner kubernetes -vv -c kafka-in-k8s topics produce --topic input --from-stdin
```

```text tab="logs"
2020-03-10 23:48:05 DEBUG zoe: trying to fetch config url for env 'common' with : EnvVarsConfigUrlProvider
2020-03-10 23:48:05 DEBUG zoe: trying to fetch config url for env 'common' with : LocalConfigDirUrlProvider
2020-03-10 23:48:05 DEBUG zoe: trying to fetch config url for env 'default' with : EnvVarsConfigUrlProvider
2020-03-10 23:48:05 DEBUG zoe: trying to fetch config url for env 'default' with : LocalConfigDirUrlProvider
2020-03-10 23:48:05 INFO zoe: loading config from url : file:/home/zoe-user/mine/projects/work/zoe/tutorials/kubernetes/config/default.yml
2020-03-10 23:48:06 DEBUG Config: Trying to configure client from Kubernetes config...
2020-03-10 23:48:06 DEBUG Config: Found for Kubernetes config at: [/home/zoe-user/.kube/config].
2020-03-10 23:48:06 INFO zoe: producing '1' records to topic 'input'
2020-03-10 23:48:06 DEBUG zoe: launching function 'produce'
2020-03-10 23:48:06 DEBUG WatchConnectionManager: Connecting websocket ... io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager@77f905e3
2020-03-10 23:48:06 DEBUG WatchConnectionManager: WebSocket successfully opened
2020-03-10 23:48:06 DEBUG zoe: received event 'ADDED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-03-10T22:48:06Z","labels":{"owner":"zoe"},"name":"zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","namespace":"default","resourceVersion":"1269561","selfLink":"/api/v1/namespaces/default/pods/zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","uid":"fe825a6b-8f22-425f-8770-476cd79088d5"},"spec":{"containers":[{"args":["{\"function\":\"produce\",\"payload\":{\"topic\":\"input\",\"dejsonifier\":{\"type\":\"raw\"},\"keyPath\":null,\"valuePath\":null,\"timestampPath\":null,\"data\":[{\"msg\":\"hello world\"}],\"dryRun\":false,\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.22.0","imagePullPolicy":"IfNotPresent","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"nodeName":"zoe-user-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-5ldg4","secret":{"defaultMode":420,"secretName":"default-token-5ldg4"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with incomplete status: [create-output-file]","reason":"ContainersNotInitialized","status":"False","type":"Initialized"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-03-10T22:48:06Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"tailer","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}},{"image":"docker.io/adevinta/zoe-core:0.22.0","imageID":"","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"create-output-file","ready":false,"restartCount":0,"state":{"waiting":{"reason":"PodInitializing"}}}],"phase":"Pending","qosClass":"Burstable","startTime":"2020-03-10T22:48:06Z"}}
2020-03-10 23:48:06 DEBUG zoe: pod is spinning up...
2020-03-10 23:48:08 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-03-10T22:48:06Z","labels":{"owner":"zoe"},"name":"zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","namespace":"default","resourceVersion":"1269569","selfLink":"/api/v1/namespaces/default/pods/zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","uid":"fe825a6b-8f22-425f-8770-476cd79088d5"},"spec":{"containers":[{"args":["{\"function\":\"produce\",\"payload\":{\"topic\":\"input\",\"dejsonifier\":{\"type\":\"raw\"},\"keyPath\":null,\"valuePath\":null,\"timestampPath\":null,\"data\":[{\"msg\":\"hello world\"}],\"dryRun\":false,\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.22.0","imagePullPolicy":"IfNotPresent","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"nodeName":"zoe-user-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-5ldg4","secret":{"defaultMode":420,"secretName":"default-token-5ldg4"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with incomplete status: [create-output-file]","reason":"ContainersNotInitialized","status":"False","type":"Initialized"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-03-10T22:48:06Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"tailer","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}},{"image":"docker.io/adevinta/zoe-core:0.22.0","imageID":"","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":false,"restartCount":0,"state":{"running":{"startedAt":"2020-03-10T22:48:08Z"}}}],"phase":"Pending","podIP":"10.1.76.34","podIPs":[{"ip":"10.1.76.34"}],"qosClass":"Burstable","startTime":"2020-03-10T22:48:06Z"}}
2020-03-10 23:48:08 DEBUG zoe: pod is spinning up...
2020-03-10 23:48:09 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-03-10T22:48:06Z","labels":{"owner":"zoe"},"name":"zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","namespace":"default","resourceVersion":"1269575","selfLink":"/api/v1/namespaces/default/pods/zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","uid":"fe825a6b-8f22-425f-8770-476cd79088d5"},"spec":{"containers":[{"args":["{\"function\":\"produce\",\"payload\":{\"topic\":\"input\",\"dejsonifier\":{\"type\":\"raw\"},\"keyPath\":null,\"valuePath\":null,\"timestampPath\":null,\"data\":[{\"msg\":\"hello world\"}],\"dryRun\":false,\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.22.0","imagePullPolicy":"IfNotPresent","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"nodeName":"zoe-user-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-5ldg4","secret":{"defaultMode":420,"secretName":"default-token-5ldg4"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-03-10T22:48:09Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-03-10T22:48:06Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-03-10T22:48:06Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"tailer","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}},{"image":"docker.io/adevinta/zoe-core:0.22.0","imageID":"","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","exitCode":0,"finishedAt":"2020-03-10T22:48:08Z","reason":"Completed","startedAt":"2020-03-10T22:48:08Z"}}}],"phase":"Pending","podIP":"10.1.76.34","podIPs":[{"ip":"10.1.76.34"}],"qosClass":"Burstable","startTime":"2020-03-10T22:48:06Z"}}
2020-03-10 23:48:09 DEBUG zoe: pod is spinning up...
2020-03-10 23:48:10 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-03-10T22:48:06Z","labels":{"owner":"zoe"},"name":"zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","namespace":"default","resourceVersion":"1269581","selfLink":"/api/v1/namespaces/default/pods/zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","uid":"fe825a6b-8f22-425f-8770-476cd79088d5"},"spec":{"containers":[{"args":["{\"function\":\"produce\",\"payload\":{\"topic\":\"input\",\"dejsonifier\":{\"type\":\"raw\"},\"keyPath\":null,\"valuePath\":null,\"timestampPath\":null,\"data\":[{\"msg\":\"hello world\"}],\"dryRun\":false,\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.22.0","imagePullPolicy":"IfNotPresent","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"nodeName":"zoe-user-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-5ldg4","secret":{"defaultMode":420,"secretName":"default-token-5ldg4"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-03-10T22:48:09Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-03-10T22:48:10Z","status":"True","type":"Ready"},{"lastTransitionTime":"2020-03-10T22:48:10Z","status":"True","type":"ContainersReady"},{"lastTransitionTime":"2020-03-10T22:48:06Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"containerID":"containerd://c26da49025b9cd0f1dd00a63ecf5c9f1cef047c678f10319a0138b82e748d875","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"tailer","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-03-10T22:48:09Z"}}},{"containerID":"containerd://ace30d76ae869e687dea908bbf2b44912807d208c15dc5617acbbb95dfed4325","image":"docker.io/adevinta/zoe-core:0.22.0","imageID":"docker.io/adevinta/zoe-core@sha256:e4eee0b9f41240175f8f0bf27a7637976570a0071f7bcaa2e34931d30ba79684","lastState":{},"name":"zoe","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-03-10T22:48:09Z"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","exitCode":0,"finishedAt":"2020-03-10T22:48:08Z","reason":"Completed","startedAt":"2020-03-10T22:48:08Z"}}}],"phase":"Running","podIP":"10.1.76.34","podIPs":[{"ip":"10.1.76.34"}],"qosClass":"Burstable","startTime":"2020-03-10T22:48:06Z"}}
2020-03-10 23:48:10 DEBUG zoe: zoe container is in : '{"running":{"startedAt":"2020-03-10T22:48:09Z"}}'
2020-03-10 23:48:13 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-03-10T22:48:06Z","labels":{"owner":"zoe"},"name":"zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","namespace":"default","resourceVersion":"1269590","selfLink":"/api/v1/namespaces/default/pods/zoe-80ebfe20-1d20-4235-835d-3d28149e37c8","uid":"fe825a6b-8f22-425f-8770-476cd79088d5"},"spec":{"containers":[{"args":["{\"function\":\"produce\",\"payload\":{\"topic\":\"input\",\"dejsonifier\":{\"type\":\"raw\"},\"keyPath\":null,\"valuePath\":null,\"timestampPath\":null,\"data\":[{\"msg\":\"hello world\"}],\"dryRun\":false,\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.22.0","imagePullPolicy":"IfNotPresent","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-5ldg4","readOnly":true}]}],"nodeName":"zoe-user-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-5ldg4","secret":{"defaultMode":420,"secretName":"default-token-5ldg4"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-03-10T22:48:09Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-03-10T22:48:13Z","message":"containers with unready status: [zoe]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-03-10T22:48:13Z","message":"containers with unready status: [zoe]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-03-10T22:48:06Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"containerID":"containerd://c26da49025b9cd0f1dd00a63ecf5c9f1cef047c678f10319a0138b82e748d875","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"tailer","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-03-10T22:48:09Z"}}},{"containerID":"containerd://ace30d76ae869e687dea908bbf2b44912807d208c15dc5617acbbb95dfed4325","image":"docker.io/adevinta/zoe-core:0.22.0","imageID":"docker.io/adevinta/zoe-core@sha256:e4eee0b9f41240175f8f0bf27a7637976570a0071f7bcaa2e34931d30ba79684","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"terminated":{"containerID":"containerd://ace30d76ae869e687dea908bbf2b44912807d208c15dc5617acbbb95dfed4325","exitCode":0,"finishedAt":"2020-03-10T22:48:12Z","reason":"Completed","startedAt":"2020-03-10T22:48:09Z"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://25a350485b33ff38ef6d29a5c160af236e540faa656fd1eb4f720c6a0a537584","exitCode":0,"finishedAt":"2020-03-10T22:48:08Z","reason":"Completed","startedAt":"2020-03-10T22:48:08Z"}}}],"phase":"Running","podIP":"10.1.76.34","podIPs":[{"ip":"10.1.76.34"}],"qosClass":"Burstable","startTime":"2020-03-10T22:48:06Z"}}
2020-03-10 23:48:13 DEBUG ExecWebSocketListener: Exec Web Socket: On Close with code:[1000], due to: []
2020-03-10 23:48:13 DEBUG WatchConnectionManager: Force closing the watch io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager@77f905e3
2020-03-10 23:48:13 DEBUG WatchConnectionManager: Closing websocket okhttp3.internal.ws.RealWebSocket@7d79af11
2020-03-10 23:48:13 DEBUG WatchConnectionManager: WebSocket close received. code: 1000, reason: 
2020-03-10 23:48:13 DEBUG WatchConnectionManager: Ignoring onClose for already closed/closing websocket
```

Write data from a file:

```bash tab="command"
zoe --runner kubernetes -c kafka-in-k8s -v topics produce --topic input --from-file data.json
```

```text tab="logs"
2020-03-06 11:07:18 INFO zoe: loading config from url : file:~/.zoe/config/default.yml
2020-03-06 11:07:18 INFO zoe: producing '212' records to topic 'input-topic'
```

```json tab="output"
{
    "produced": [
        {
            "offset": 4,
            "partition": 0,
            "topic": "input",
            "timestamp": 1583489239047
        },
        {
            "offset": 5,
            "partition": 0,
            "topic": "input",
            "timestamp": 1583489239054
        },
        ...
    ],
    "skipped": []
}
```

To avoid repeating the `-c kafka-in-k8s`, we can make this cluster the default by using:

```bash
export ZOE_CLUSTER=kafka-in-k8s
```

Consume some data:

```bash tab="command"
zoe -v --runner kubernetes \
       --output table \
       topics consume input \
       --query '{id: _id, text: text, user: user.name}'
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────────┬───────────────────┐
│ id                       │ text                                                      │ user              │
├──────────────────────────┼───────────────────────────────────────────────────────────┼───────────────────┤
│ 58e008ad0aac31001185ed0c │ The frequency of a domestic cat's purr is the same...     │ first: "Kasimir"  │
│                          │                                                           │ last: "Schulz"    │
├──────────────────────────┼───────────────────────────────────────────────────────────┼───────────────────┤
│ 5b199196ce456e001424256a │ Cats can distinguish different flavors in water.          │ first: "Alex"     │
│                          │                                                           │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────────┼───────────────────┤
│ 5b1b411d841d9700146158d9 │ The Egyptian Mau���s name is derived from the Middle...   │ first: "Alex"     │
│                          │                                                           │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────────┼───────────────────┤
│ 5b4912c60508220014ccfe99 │ Cats aren���t the only animals that purr ��� squirrels... │ first: "Alex"     │
│                          │                                                           │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────────┼───────────────────┤
│ 5b4911940508220014ccfe94 │ By the time a cat is 9 years old, it will only hav...     │ first: "Alex"     │
│                          │                                                           │ last: "Wohlbruck" │
└──────────────────────────┴───────────────────────────────────────────────────────────┴───────────────────┘
```

Filter Kasimir's facts and spin up 5 pods in parallel to consume from the topic (overkill in this case but just for the purpose of the demo):

```bash tab="command"
zoe --runner kubernetes \
    --output table \
    topics consume input \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'" \
    --query '{id: _id, text: text, user: user.name}' \
    --jobs 5
```

```bash tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬──────────────────┐
│ id                       │ text                                                  │ user             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────────────────┤
│ 58e008ad0aac31001185ed0c │ The frequency of a domestic cat's purr is the same... │ first: "Kasimir" │
│                          │                                                       │ last: "Schulz"   │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────────────────┤
│ 58e008800aac31001185ed07 │ Wikipedia has a recording of a cat meowing, becaus... │ first: "Kasimir" │
│                          │                                                       │ last: "Schulz"   │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────────────────┤
│ 58e007db0aac31001185ecf7 │ There are cats who have survived falls from over 3... │ first: "Kasimir" │
│                          │                                                       │ last: "Schulz"   │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────────────────┤
│ 58e009790aac31001185ed14 │ The technical term for "hairball" is "bezoar."        │ first: "Kasimir" │
│                          │                                                       │ last: "Schulz"   │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────────────────┤
│ 58e00a090aac31001185ed16 │ Cats make more than 100 different sounds whereas d... │ first: "Kasimir" │
│                          │                                                       │ last: "Schulz"   │
└──────────────────────────┴───────────────────────────────────────────────────────┴──────────────────┘
```

## Clean up

After you're done, you can clean up all the resources spinned up during this tutorial by using the following command:

```bash tab="command"
kubectl delete all -l context=zoe-demo
```

# Zoe with kubernetes

In this tutorial, we will learn how to use Zoe with the Kubernetes runner.

We will start by spinning up a simple one node Kafka cluster in Kubernetes, and then we will use zoe to interact with it using the `kubernetes` runner. With this runner, zoe spins up consumers / producers as pods, and the results will be forwarded to the caller's machine. This is useful in many scenarios. Here are some examples:

- When the Kafka cluster is not reachable from our local machine and is only reachable from within the Kubernetes cluster.
- If we want to parallelize topics consumption over multiple pods (see an example in this guide).

## Prerequisites

- `zoe` (Install instructions [here](https://adevinta.github.io/zoe/install/overview/))
- `kubectl` and a working kubernetes cluster (cf. below for more instructions on how to install a kubernetes cluster)
- This section assumes you have already gone through the [Basic scenario](https://adevinta.github.io/zoe/guides/simple/guide) guide and will not explain the basics that were already covered there.
 
## Prepare the environment

### Kubernetes setup

In this tutorial, you will need [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl) and a working kubernetes cluster that you can use to test Zoe. Several solutions exist to install a kubernetes cluster locally. The most common ones are: [Minikube](https://kubernetes.io/docs/setup/learning-environment/minikube), [Kind](https://kind.sigs.k8s.io/docs/user/quick-start/), [MicroK8s](https://microk8s.io/) and [k3s](https://k3s.io/).

No matter how you installed your local cluster (or if you are using a remote one), make sure your current context in your kube config file is pointing to that cluster (you can explicitly configure zoe to use a [specific context and a specific namespace](https://adevinta.github.io/zoe/advanced/runners/kubernetes/) but for the sake of simplicity we will leave this part out of this tutorial).

You can ensure that the kubernetes cluster is reachable and is pointing to the right context by using:

=== "Command"

    ```bash
    kubectl cluster-info
    ```

=== "Output"

    ```text
    Kubernetes master is running at https://127.0.0.1:16443
    CoreDNS is running at https://127.0.0.1:16443/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy
    
    To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
    ```

The output may be different on your machine.

### Setup Kafka

Ready to use manifest files to install a single node Kafka cluster on kubernetes are provided [in the repository](https://github.com/adevinta/zoe/tree/master/docs/guides/kubernetes/resources). Let's use them:
 
- Clone the repository: `git clone https://github.com/adevinta/zoe.git`
- Go to the kubernetes guide directory: `cd zoe/docs/guides/kubernetes`
- Apply the manifests: `kubectl apply --prune --selector='context=zoe-demo' -f resources`

This will install a Zookeeper and a Kafka node. You can check the readiness of the pods using:

=== "Command"

    ```bash
    kubectl get all -l context=zoe-demo
    ```

=== "Output"

    ```text
    NAME                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)              AGE
    service/broker      ClusterIP   10.152.183.151   <none>        9092/TCP,29092/TCP   44m
    service/zookeeper   ClusterIP   10.152.183.157   <none>        2181/TCP             44m
    
    NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
    deployment.apps/broker      1/1     1            1           44m
    deployment.apps/zookeeper   1/1     1            1           44m
    ```

### Configure zoe

Create a new file named `k8s.yml` in zoe's configuration directory (`~/.zoe/config` by default) and fill it with the following content:

=== "~/.zoe/config/k8s.yml"

    ```text
    clusters:
      default:
        props:
          bootstrap.servers: "broker:9092"
          key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
          value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
          key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
          value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"
    
    runners:
      default: "kubernetes"
    ```

The configuration above defines the `kubernetes` runner as the default one. When using this runner with no additional configuration, `zoe` targets the kubernetes cluster pointed at by the current context (cf. `kubectl cluster-info` to know which cluster you are targeting). It is possible to point to a specific context. To know more, visit [the kubernetes runner dedicated section](https://adevinta.github.io/zoe/advanced/runners/kubernetes/). 

Notice the `bootstrap.servers` property above pointing to `broker:9092`. We are using the actual broker [Service](https://github.com/adevinta/zoe/blob/master/docs/guides/kubernetes/resources/broker-service.yaml) name defined in kubernetes. This DNS name is only visible inside the kubernetes cluster. This is possible because as explained above, the kubernetes runner spins up the consumers, producers and all the kafka clients as pods inside the cluster. So the internal kubernetes DNS names are visible and usable.

If you don't want to write this configuration by hand, you can copy it directly from the repository by using:

=== "Command"

    ```bash
    zoe config init --from git --url https://github.com/adevinta/zoe --dir docs/guides/kubernetes/config
    ``` 

Ensure zoe is aware about our new configuration:

=== "Command"

    ```bash
    zoe -e k8s -o table config clusters list
    ```

=== "Output"

    ```text
    2020-06-27 20:14:05 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    ┌─────────┬─────────────┬──────────┬────────┬────────┐
    │ cluster │ brokers     │ registry │ topics │ groups │
    ├─────────┼─────────────┼──────────┼────────┼────────┤
    │ default │ broker:9092 │ null     │        │        │
    └─────────┴─────────────┴──────────┴────────┴────────┘
    ```

Notice our use of `-e k8s` in the above command. Zoe supports having multiple configuration files inside its config directory representing different environments. To point to a specific environment, we use the `-e <env name>` (`<env name>` is the name of the configuration file without the extension). When no environment is specified, zoe uses the environment called `default`. 

Zoe is now ready to be used against our cluster!

## Creating the topic

Create a topic called `cat-facts-topic` (This first run will download the `zoe` docker image so expect it to be slower):

=== "Command"

    ```bash
    zoe -e k8s topics create cat-facts-topic --partitions 10
    ```

=== "Output"

    ```json
    2020-06-27 20:54:13 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 20:54:14 INFO zoe: creating topic: cat-facts-topic
    {"done":true}
    ```

Ensure our topic is created:

=== "Command"

    ```bash
    zoe -e k8s -o table topics list
    ```

=== "Output"

    ```json
    2020-06-27 20:56:47 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 20:56:48 INFO zoe: requesting topics...
    ┌───────────────────┐
    │ value             │
    ├───────────────────┤
    │ "cat-facts-topic" │
    └───────────────────┘
    ```

You can higher up the logging verbosity to see how zoe launches pods and watches for changes:

=== "Command"

    ```bash
    zoe -vv -e k8s -o table topics list
    ```

=== "Output"

    ```text
    2020-06-27 21:21:39 DEBUG zoe: trying to fetch config url for env 'common' with : EnvVarsConfigUrlProvider
    2020-06-27 21:21:39 DEBUG zoe: trying to fetch config url for env 'common' with : LocalConfigDirUrlProvider
    2020-06-27 21:21:39 DEBUG zoe: trying to fetch config url for env 'k8s' with : EnvVarsConfigUrlProvider
    2020-06-27 21:21:39 DEBUG zoe: trying to fetch config url for env 'k8s' with : LocalConfigDirUrlProvider
    2020-06-27 21:21:39 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 21:21:39 DEBUG Config: Trying to configure client from Kubernetes config...
    2020-06-27 21:21:39 DEBUG Config: Found for Kubernetes config at: [/home/wlezzar/.kube/config].
    2020-06-27 21:21:40 INFO zoe: requesting topics...
    2020-06-27 21:21:40 DEBUG WatchConnectionManager: Connecting websocket ... io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager@66b71771
    2020-06-27 21:21:40 DEBUG WatchConnectionManager: WebSocket successfully opened
    2020-06-27 21:21:40 DEBUG zoe: received event 'ADDED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-06-27T19:21:40Z","labels":{"owner":"zoe","runnerId":"53ec2205-897e-4cc0-b1eb-07ea091f14c0"},"name":"zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","namespace":"default","resourceVersion":"1513249","selfLink":"/api/v1/namespaces/default/pods/zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","uid":"a8dea11a-a15d-4855-bc7f-ecb7ec7c78d6"},"spec":{"containers":[{"args":["{\"function\":\"topics\",\"payload\":{\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.26.0","imagePullPolicy":"Always","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"nodeName":"wlezzar-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-v9k66","secret":{"defaultMode":420,"secretName":"default-token-v9k66"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-06-27T19:21:40Z","message":"containers with incomplete status: [create-output-file]","reason":"ContainersNotInitialized","status":"False","type":"Initialized"},{"lastTransitionTime":"2020-06-27T19:21:40Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-06-27T19:21:40Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-06-27T19:21:40Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"tailer","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}},{"image":"docker.io/adevinta/zoe-core:0.26.0","imageID":"","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"create-output-file","ready":false,"restartCount":0,"state":{"waiting":{"reason":"PodInitializing"}}}],"phase":"Pending","qosClass":"Burstable","startTime":"2020-06-27T19:21:40Z"}}
    2020-06-27 21:21:40 DEBUG zoe: pod is spinning up...
    2020-06-27 21:21:42 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-06-27T19:21:40Z","labels":{"owner":"zoe","runnerId":"53ec2205-897e-4cc0-b1eb-07ea091f14c0"},"name":"zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","namespace":"default","resourceVersion":"1513256","selfLink":"/api/v1/namespaces/default/pods/zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","uid":"a8dea11a-a15d-4855-bc7f-ecb7ec7c78d6"},"spec":{"containers":[{"args":["{\"function\":\"topics\",\"payload\":{\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.26.0","imagePullPolicy":"Always","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"nodeName":"wlezzar-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-v9k66","secret":{"defaultMode":420,"secretName":"default-token-v9k66"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-06-27T19:21:42Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-06-27T19:21:40Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-06-27T19:21:40Z","message":"containers with unready status: [zoe tailer]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-06-27T19:21:40Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"image":"alpine:3.9.5","imageID":"","lastState":{},"name":"tailer","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}},{"image":"docker.io/adevinta/zoe-core:0.26.0","imageID":"","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"waiting":{"reason":"PodInitializing"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","exitCode":0,"finishedAt":"2020-06-27T19:21:41Z","reason":"Completed","startedAt":"2020-06-27T19:21:41Z"}}}],"phase":"Pending","podIP":"10.42.0.202","podIPs":[{"ip":"10.42.0.202"}],"qosClass":"Burstable","startTime":"2020-06-27T19:21:40Z"}}
    2020-06-27 21:21:42 DEBUG zoe: pod is spinning up...
    2020-06-27 21:21:44 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-06-27T19:21:40Z","labels":{"owner":"zoe","runnerId":"53ec2205-897e-4cc0-b1eb-07ea091f14c0"},"name":"zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","namespace":"default","resourceVersion":"1513264","selfLink":"/api/v1/namespaces/default/pods/zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","uid":"a8dea11a-a15d-4855-bc7f-ecb7ec7c78d6"},"spec":{"containers":[{"args":["{\"function\":\"topics\",\"payload\":{\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.26.0","imagePullPolicy":"Always","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"nodeName":"wlezzar-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-v9k66","secret":{"defaultMode":420,"secretName":"default-token-v9k66"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-06-27T19:21:42Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-06-27T19:21:44Z","status":"True","type":"Ready"},{"lastTransitionTime":"2020-06-27T19:21:44Z","status":"True","type":"ContainersReady"},{"lastTransitionTime":"2020-06-27T19:21:40Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"containerID":"containerd://290cbb74626b8ad76d67d2ef4cfc7a0217a51a33b81eb333d95943bee8f9ffdb","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"tailer","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-06-27T19:21:44Z"}}},{"containerID":"containerd://9e31ab970f1b46beec9a4af8d162f0a6bbaba56cbf3c32c121eae5efaea6d441","image":"docker.io/adevinta/zoe-core:0.26.0","imageID":"docker.io/adevinta/zoe-core@sha256:d70f67c34bbe9c32aa9aea55ac5801ab38652a1546ea26186a3c246616443a7a","lastState":{},"name":"zoe","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-06-27T19:21:43Z"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","exitCode":0,"finishedAt":"2020-06-27T19:21:41Z","reason":"Completed","startedAt":"2020-06-27T19:21:41Z"}}}],"phase":"Running","podIP":"10.42.0.202","podIPs":[{"ip":"10.42.0.202"}],"qosClass":"Burstable","startTime":"2020-06-27T19:21:40Z"}}
    2020-06-27 21:21:44 DEBUG zoe: zoe container is in : '{"running":{"startedAt":"2020-06-27T19:21:43Z"}}'
    2020-06-27 21:21:48 DEBUG zoe: received event 'MODIFIED' with pod : {"apiVersion":"v1","kind":"Pod","metadata":{"creationTimestamp":"2020-06-27T19:21:40Z","labels":{"owner":"zoe","runnerId":"53ec2205-897e-4cc0-b1eb-07ea091f14c0"},"name":"zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","namespace":"default","resourceVersion":"1513268","selfLink":"/api/v1/namespaces/default/pods/zoe-dd615e81-4fec-4eba-81e2-d0b89a0ea897","uid":"a8dea11a-a15d-4855-bc7f-ecb7ec7c78d6"},"spec":{"containers":[{"args":["{\"function\":\"topics\",\"payload\":{\"props\":{\"bootstrap.servers\":\"broker:9092\",\"key.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"value.deserializer\":\"org.apache.kafka.common.serialization.StringDeserializer\",\"key.serializer\":\"org.apache.kafka.common.serialization.StringSerializer\",\"value.serializer\":\"org.apache.kafka.common.serialization.ByteArraySerializer\"}}}","/output/response.txt"],"image":"docker.io/adevinta/zoe-core:0.26.0","imagePullPolicy":"Always","name":"zoe","resources":{"limits":{"cpu":"1","memory":"512M"},"requests":{"cpu":"1","memory":"512M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]},{"args":["sh","-c","while [ -f /output/response.txt ]; do sleep 2; done"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"tailer","resources":{"limits":{"cpu":"100m","memory":"24M"},"requests":{"cpu":"100m","memory":"24M"}},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"dnsPolicy":"ClusterFirst","enableServiceLinks":true,"initContainers":[{"command":["touch","/output/response.txt"],"image":"alpine:3.9.5","imagePullPolicy":"IfNotPresent","name":"create-output-file","resources":{},"terminationMessagePath":"/dev/termination-log","terminationMessagePolicy":"File","volumeMounts":[{"mountPath":"/output","name":"output-volume"},{"mountPath":"/var/run/secrets/kubernetes.io/serviceaccount","name":"default-token-v9k66","readOnly":true}]}],"nodeName":"wlezzar-thinkpad-t460p","priority":0,"restartPolicy":"Never","schedulerName":"default-scheduler","securityContext":{},"serviceAccount":"default","serviceAccountName":"default","terminationGracePeriodSeconds":30,"tolerations":[{"effect":"NoExecute","key":"node.kubernetes.io/not-ready","operator":"Exists","tolerationSeconds":300},{"effect":"NoExecute","key":"node.kubernetes.io/unreachable","operator":"Exists","tolerationSeconds":300}],"volumes":[{"emptyDir":{},"name":"output-volume"},{"name":"default-token-v9k66","secret":{"defaultMode":420,"secretName":"default-token-v9k66"}}]},"status":{"conditions":[{"lastTransitionTime":"2020-06-27T19:21:42Z","status":"True","type":"Initialized"},{"lastTransitionTime":"2020-06-27T19:21:48Z","message":"containers with unready status: [zoe]","reason":"ContainersNotReady","status":"False","type":"Ready"},{"lastTransitionTime":"2020-06-27T19:21:48Z","message":"containers with unready status: [zoe]","reason":"ContainersNotReady","status":"False","type":"ContainersReady"},{"lastTransitionTime":"2020-06-27T19:21:40Z","status":"True","type":"PodScheduled"}],"containerStatuses":[{"containerID":"containerd://290cbb74626b8ad76d67d2ef4cfc7a0217a51a33b81eb333d95943bee8f9ffdb","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"tailer","ready":true,"restartCount":0,"started":true,"state":{"running":{"startedAt":"2020-06-27T19:21:44Z"}}},{"containerID":"containerd://9e31ab970f1b46beec9a4af8d162f0a6bbaba56cbf3c32c121eae5efaea6d441","image":"docker.io/adevinta/zoe-core:0.26.0","imageID":"docker.io/adevinta/zoe-core@sha256:d70f67c34bbe9c32aa9aea55ac5801ab38652a1546ea26186a3c246616443a7a","lastState":{},"name":"zoe","ready":false,"restartCount":0,"started":false,"state":{"terminated":{"containerID":"containerd://9e31ab970f1b46beec9a4af8d162f0a6bbaba56cbf3c32c121eae5efaea6d441","exitCode":0,"finishedAt":"2020-06-27T19:21:47Z","reason":"Completed","startedAt":"2020-06-27T19:21:43Z"}}}],"hostIP":"192.168.1.16","initContainerStatuses":[{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","image":"docker.io/library/alpine:3.9.5","imageID":"docker.io/library/alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178","lastState":{},"name":"create-output-file","ready":true,"restartCount":0,"state":{"terminated":{"containerID":"containerd://d02eff3e9547c1bbda6b90ae18b3baa3cd262886912b56be0bf68f6bed0ed41d","exitCode":0,"finishedAt":"2020-06-27T19:21:41Z","reason":"Completed","startedAt":"2020-06-27T19:21:41Z"}}}],"phase":"Running","podIP":"10.42.0.202","podIPs":[{"ip":"10.42.0.202"}],"qosClass":"Burstable","startTime":"2020-06-27T19:21:40Z"}}
    2020-06-27 21:21:48 DEBUG ExecWebSocketListener: Exec Web Socket: On Close with code:[1000], due to: []
    2020-06-27 21:21:48 DEBUG WatchConnectionManager: Force closing the watch io.fabric8.kubernetes.client.dsl.internal.WatchConnectionManager@66b71771
    2020-06-27 21:21:48 DEBUG WatchConnectionManager: Closing websocket okhttp3.internal.ws.RealWebSocket@68ab0936
    2020-06-27 21:21:48 DEBUG WatchConnectionManager: WebSocket close received. code: 1000, reason: 
    2020-06-27 21:21:48 DEBUG WatchConnectionManager: Ignoring onClose for already closed/closing websocket
    ┌───────────────────┐
    │ value             │
    ├───────────────────┤
    │ "cat-facts-topic" │
    └───────────────────┘
    
    2020-06-27 21:21:48 DEBUG zoe: closing: com.adevinta.oss.zoe.service.runners.KubernetesRunner@885e7ff
    2020-06-27 21:21:48 DEBUG zoe: deleting potentially dangling pods...
    2020-06-27 21:21:48 DEBUG zoe: closing: com.adevinta.oss.zoe.service.storage.BufferedKeyValueStore@625dfff3
    2020-06-27 21:21:48 DEBUG zoe: closing: com.adevinta.oss.zoe.service.secrets.SecretsProviderWithLogging@57272109
    ```

## Producing data

Produce some data from the cat facts dataset (we assume you cloned the repository, and you are in the `docs/guides/kubernetes` directory):

=== "Command"

    ```bash
    zoe -e k8s -o table topics produce --topic cat-facts-topic --from-file data.json
    ```

=== "Output"

    ```text
    2020-06-27 21:12:37 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 21:12:38 INFO zoe: producing '212' records to topic 'cat-facts-topic'
    ┌──────────────────────────────────────────────────────────────────────────────┬─────────┐
    │ produced                                                                     │ skipped │
    ├──────────────────────────────────────────────────────────────────────────────┼─────────┤
    │ offset: 21  partition: 5  topic: "cat-facts-topic"  timestamp: 1593285166459 │         │
    │ offset: 25  partition: 0  topic: "cat-facts-topic"  timestamp: 1593285166500 │         │
    │ offset: 19  partition: 4  topic: "cat-facts-topic"  timestamp: 1593285166501 │         │
    │ offset: 26  partition: 0  topic: "cat-facts-topic"  timestamp: 1593285166501 │         │
    │ offset: 23  partition: 8  topic: "cat-facts-topic"  timestamp: 1593285166501 │         │
    | ...                                                                          |         |
    │ offset: 17  partition: 3  topic: "cat-facts-topic"  timestamp: 1593285166501 │         │
    │ offset: 47  partition: 6  topic: "cat-facts-topic"  timestamp: 1593285166570 │         │
    │ offset: 49  partition: 7  topic: "cat-facts-topic"  timestamp: 1593285166570 │         │
    └──────────────────────────────────────────────────────────────────────────────┴─────────┘
    ```

## Reading data

You can read data using the following command:

=== "Command"

    ```bash
    zoe -e k8s -o table topics consume cat-facts-topic --from 'PT1h' -n 5        
    ```

=== "Output"

    ```text
    2020-06-27 21:18:06 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 21:18:07 INFO zoe: querying offsets...
    2020-06-27 21:18:15 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={8=0, 7=0, 9=0, 4=0, 3=0, 6=0, 5=0, 0=0, 2=0, 1=0}))
    2020-06-27 21:18:24 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={8=5}))
    ┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
    │ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 599f89639a11040c4a163440 │ Here is a video of some cats in zero gravity. yout... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 5       │ null        │
    │                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00a090aac31001185ed16 │ Cats make more than 100 different sounds whereas d... │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 590b9d90229d260020af0b06 │ Evidence suggests domesticated cats have been arou... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e008780aac31001185ed05 │ Owning a cat can reduce the risk of stroke and hea... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 5b3d8e4960d3890713ca39a8 │ A Chinese cat named Baidianr (meaning "white spot"... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
    └──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
    ```

## Parallelizing the consumption to perform expensive searches

Now imagine we want to find cat facts we received in the previous hour, written by the user whose first name is `Kasimir`. With zoe, you can achieve that using:

=== "Command"

    ```bash
    # filter out Kasimir's data
    zoe -e k8s -o table topics consume cat-facts-topic \
           --from 'PT1h' \
           --filter "user.name.first == 'Kasimir'" \
           -n 5
    ```

=== "Output"

    ```text
    2020-06-27 21:28:43 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-27 21:28:44 INFO zoe: querying offsets...
    2020-06-27 21:28:52 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={8=0, 7=0, 9=0, 4=0, 3=0, 6=0, 5=0, 0=0, 2=0, 1=0}))
    2020-06-27 21:29:00 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={8=28}))
    ┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
    │ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00a090aac31001185ed16 │ Cats make more than 100 different sounds whereas d... │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e008780aac31001185ed05 │ Owning a cat can reduce the risk of stroke and hea... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e007db0aac31001185ecf7 │ There are cats who have survived falls from over 3... │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00a850aac31001185ed1a │ Cats have a longer-term memory than dogs, especial... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00ba00aac31001185edfa │ When cats leave their poop uncovered, it is a sign... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    └──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
    ```

Now, what if the topic is too big? With zoe you can parallelize the consumption over multiple pods using the `--jobs` option:

=== "Command"

    ```bash
    # filter out Kasimir's data (parallelize the search over 5 pods)
    zoe -e k8s -o table topics consume cat-facts-topic \
           --from 'PT1h' \
           --filter "user.name.first == 'Kasimir'" \
           --jobs 5 \
           -n 5
    ```

=== "Output"

    ```text
    2020-06-28 01:39:31 INFO zoe: loading config from url : file:~/.zoe/config/k8s.yml
    2020-06-28 01:39:32 INFO zoe: querying offsets...
    2020-06-28 01:39:40 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={8=0, 3=0}))
    2020-06-28 01:39:40 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={7=0, 2=0}))
    2020-06-28 01:39:40 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={9=0, 4=0}))
    2020-06-28 01:39:40 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={6=0, 1=0}))
    2020-06-28 01:39:40 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={5=0, 0=0}))
    2020-06-28 01:39:49 INFO zoe: polling topic 'cat-facts-topic' (subscription : AssignPartitions(partitions={6=9}))
    ┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
    │ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e007cc0aac31001185ecf5 │ Cats are the most popular pet in the United States... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e008a30aac31001185ed0b │ A cat's purr may be a form of self-healing, as it ... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00b820aac31001185edf7 │ One legend claims that cats were created when a li... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e009390aac31001185ed10 │ Most cats are lactose intolerant, and milk can cau... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    ├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
    │ 58e00c080aac31001185ee01 │ Cats only sweat through their foot pads.              │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
    │                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
    └──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
    ```

This is obviously overkill in this case as we have a small dataset but this is extremely useful when dealing with large topics.

## Clean up

=== "Command"

    ```bash
    kubectl delete all -l context=zoe-demo
    ```

[![Chocolatey](https://img.shields.io/chocolatey/v/zoe.svg)](https://chocolatey.org/packages/zoe)

# Windows install as a Chocolatey package

## Install Chocolatey

You only need to have a proper [Chocolatey install](https://chocolatey.org/install).

## Java requirements

Java 11 or higher is required in order to install the runtime-less tarball packages. They only ship with the Zoe CLI jar. 

If you don't have java installed already, you can use [sdkman](https://sdkman.io/) for an easy install and version management of the JDK. If you don't want to install java you can try one of the platform packages provided in the previous section.

Once java is installed, proceed with the `zoe` install :

```
choco install zoe
````

To upgrade :

```
choco upgrade zoe
```

To uninstall :

```
choco uninstall zoe
```

## Init zoe configuration

```bash
zoe config init
```

You are now ready to use zoe. Go to the [guides section](../guides) to start learning zoe.
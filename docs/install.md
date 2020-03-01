# Install Zoe CLI

## Platform package install (Experimental - does not require an already installed JDK)

You can install zoe using one of the platform packages listed below. Only few platforms are supported for now but more will come in the future. 

The platform packages are self contained. They ship with their own version of the java virtual machine (thus the higher size of the package). The host machine does not need to have it's own java runtime.

The platform packages are built with [jpackage](https://jdk.java.net/jpackage/) and JDK 14. 

### Ubuntu / Debian

1. Download the `.deb` package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and install it using `dpkg` :
```
ZOE_VERSION=0.3.0  # change it to the suitable version
curl -L "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe_${ZOE_VERSION}-1_amd64.deb" -o /tmp/zoe.deb
sudo dpkg -i /tmp/zoe.deb
```
2. Add the `/opt/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:/opt/zoe/bin
```
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

### Centos

1. Download the `.rpm` package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and install it using `dpkg` :
```
ZOE_VERSION=0.3.0  # change it to the suitable version
sudo rpm -i "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe-${ZOE_VERSION}-1.x86_64.rpm"
```
2. Add the `/opt/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:/opt/zoe/bin
```
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

## Manual tarball install (requires a JDK on the host machine)

Java 11 or higher is required in order to install the runtime-less tarball packages. They only ship with the Zoe CLI jar. 

If you don't have java installed already, you can use [sdkman](https://sdkman.io/) for an easy install and version management of the JDK. If you don't want to install java you can try one of the platform packages provided above.

Once java is installed, proceed with the following steps :

1. Download the runtime-less zip or tar package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and uncompress it into your home directory (or wherever you wish)
```
ZOE_VERSION=0.3.0  # change it to the suitable version
curl -L "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe-${ZOE_VERSION}.tar.gz" | tar -zx -C $HOME
```
2. Add the `$HOME/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:$HOME/zoe/bin
``` 
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

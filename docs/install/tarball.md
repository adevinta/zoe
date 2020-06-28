# Manual tarball install (requires a JDK on the host machine)

Java 11 or higher is required in order to install the runtime-less tarball packages. They only ship with the Zoe CLI jar. 

If you don't have java installed already, you can use [sdkman](https://sdkman.io/) for an easy install and version management of the JDK. If you don't want to install java you can try one of the platform packages provided in the previous section.

Once java is installed, proceed with the following steps :

1. Download the runtime-less zip or tar package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and uncompress it into your home directory (or wherever you wish)
    
    ```
    ZOE_VERSION=0.23.0  # change it to the suitable version
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
4. You are now ready to use zoe. Go to the [guides section](../guides) to start learning zoe.

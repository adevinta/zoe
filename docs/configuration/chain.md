# Configuration loading chain

Zoe has a complex and powerful configuration override chain that makes it highly configurable.

Zoe configuration properties are considered in the following order (in a decreasing order of priority):

1. `ZOE_CONFIG_OVERRIDE_{env}` environment variable.
2. `ZOE_CONFIG_OVERRIDE` environment variable.
3. `~/.zoe/config/{env}.yml` or `.json` file
4. `~/.zoe/config/common.yml` or `.json` file

The 2 first environment variables must contain the configuration (or a subset) in json format.

The final zoe configuration will be the result of merging all the above configuration values in the increasing level priority.

The special `common.yml` configuration file is supposed to contain configuration values that are common to all the environments. Registered expressions or secrets providers are a good use case. 

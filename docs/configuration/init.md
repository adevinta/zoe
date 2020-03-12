# Config initialization

## From scratch

The simplest way to initialize zoe configuration from scratch is to use the zoe CLI:

```bash tab="command"
zoe config init
``` 

This will create a `default.yml` file in `~/.zoe/config` by default.

## From an existing local directory

If you already have locally a directory that contains ready to use configuration files you can use:

```bash tab="command"
zoe config init --from local --path /path/to/config
```

This will copy the yaml files from the target directory to `~/.zoe/config`.

## From a git repository

You can also copy the configuration from an existing git repository. For example, to copy the zoe config used in the tutorials in the official repository:

```bash tab="command"
zoe config init --from git --url 'https://github.com/adevinta/zoe.git' --dir tutorials/simple/config
```

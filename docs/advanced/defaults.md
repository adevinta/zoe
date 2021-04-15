# Managing defaults

Commands can become quite long when you start using more and more features of zoe. Take a look at the following command
as an example:

```bash
zoe -o table -e pro -c my-cluster topics consume --dialect jq --filter '.id == "2"'
```

The above command:

- Sets the output format for zoe results to `table` with `-o table` instead of the default value `json`.
- Uses the `pro` environment config file using the `-e pro` instead of the default environment `default`.
- Uses the `my-cluster` cluster using `-c my-cluster` instead of the default cluster `default`.
- Sets `jq` as the dialect for writing json queries with `--dialect jq` instead of the default dialect `jmespath`.

If you find yourself repeating the same option values over and over again, it might be worth setting them as the default
option values for zoe.

## Changing the default values for zoe

To override zoe defaults, use the following command:

```
zoe config defaults edit
```

The above command opens your default text editor (ex. nano or vim) and allows you to update the defaults. You can choose
a different editor by using the `EDITOR` environment variable:

```bash
# Use VSCode to edit the values
EDITOR="code -w" zoe config defaults edit
```

For example, by setting the defaults to the following values:

```yaml
cluster: pro
environment: my-cluster
outputFormat: table
topic:
  consume:
    jsonQueryDialect: jq
```

You could rewrite the above command mentioned in the introduction section as the following:

```bash
zoe topics consume --filter '.id == "2"'
```

## Editing defaults manually

Default values are stored and kept in a local file named `$HOME/.zoe/defaults.yml`. You can edit this file manually, but
you lose the type checking and validation provided by the equivalent `zoe config defaults edit` command. Thus, it's
always encouraged to modify the defaults via this command.

## List of overridable defaults

Here is the list of currently overridable defaults:

```yaml
# Default for: `zoe -c <value>`
cluster: default

# Default for: `zoe -e <value>`
environment: default

# Default for: `zoe -o <value>`
outputFormat: raw

topic:
  consume:
    # Default for: `zoe topics consume --dialect <value>`
    jsonQueryDialect: jmespath
```

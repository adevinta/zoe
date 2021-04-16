# Aliases

Zoe allows you to create aliases for commonly used commands. For example, to create an alias
for `zoe -e pro topics list`, use the following command:

```bash
zoe aliases add --name ptl -- -e pro topics list
```

Subsequently, each time you call:

```bash
zoe ptl
```

Zoe will detect that it has a matching alias named `ptl` and replace it with: `zoe -e pro topics list`.

For more details on aliases, use: `zoe aliases --help`.
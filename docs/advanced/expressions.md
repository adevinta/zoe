# Registered expressions

We saw in the section about [consuming data with zoe](../basics/consume/#filtering-data-based-on-content) that zoe can make use of Jmespath filter expressions to output only a subset of the data read from Kafka.

Jmespath expressions can be long and tedious to write especially in the command line. Some characters like quotes and backticks may be interpreted wrongly by the shell. Additionally, it may be repetitive to write if the expression is commonly used.

Zoe allows you to save reusable Jmespath expressions in the configuration file and refer to them by an alias when using zoe.

## Registered expressions without arguments

Registered expressions are set under the `expressions` key in zoe's configuration file :

```yaml
expressions:
  popular_facts: "upvotes >= `2`"
``` 

The example above shows a registered expression aliased by `popular_facts` that represents the expression `upvotes >= 2`. We can refer to this expression using `@popular_facts()` with zoe :

```bash
zoe -v -c my-cluster topics consume --filter '@popular_facts()' 
```

Zoe will replace `@popular_facts()` by `upvotes >= '2'` at runtime without having bash interfering with our expression.

## Registered expressions with arguments

Registered expressions can accept named arguments. Here is an example of an expression that expects a single argument named `value`:

```yaml
expressions:
  short_id: "ends_with(id, '{{ value }}')"
``` 

Notice the `{{ value }}` part. When referring to the expression with `@short_id`, zoe will expect an argument named `value`:

```bash
zoe -v -c my-cluster topics consume --filter '@short_id(value=22121)' 
```

Zoe replaces `@short_id(value=22121)` with `ends_with(id, '22121')` at runtime.

It is also possible for a registered expression to accept multiple named arguments. They just need to be separated by a comma.
 
!!! tip
    Registered expressions allow team members to share commonly used filters between them. A good practice is to put these expressions in the `common.yml` config file so that expressions become available in all the environments.
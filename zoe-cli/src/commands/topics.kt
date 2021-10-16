// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.config.Format
import com.adevinta.oss.zoe.cli.config.ZoeDefaults
import com.adevinta.oss.zoe.cli.utils.batches
import com.adevinta.oss.zoe.cli.utils.fetch
import com.adevinta.oss.zoe.cli.utils.globalTermColors
import com.adevinta.oss.zoe.core.functions.JsonQueryDialect
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.*
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.inject
import java.io.InputStream
import java.lang.Integer.MAX_VALUE
import java.lang.Integer.min
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.ZonedDateTime.now
import java.util.*
import kotlin.math.roundToInt


class TopicsCommand : NoOpCliktCommand(
    name = "topics",
    help = "Inspect, produce or consume from topics",
    printHelpOnEmptyArgs = true
) {
    init {
        subcommands(
            TopicsConsume(),
            TopicsList(),
            TopicsCreate(),
            TopicsDescribe(),
            TopicsProduce()
        )
    }
}


class TopicsList : CliktCommand(name = "list", help = "list topics"), KoinComponent {

    private val all by option("-a", "--all", help = "Also list internal topics").flag(default = false)

    private val filter: Regex?
        by option("-m", "--matching", help = "Filter only topic names matching the given pattern")
            .convert { it.toRegex() }

    private val limit: Int? by option("-n", "--limit", help = "Limit the number of returned results").int()

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val response = service.listTopics(
            ctx.cluster,
            userTopicsOnly = !all,
            filter = filter,
            limit = limit
        )

        ctx.term.output.format(response.topics.toJsonNode()) { echo(it) }
    }
}

class TopicsDescribe : CliktCommand(name = "describe", help = "describe a topic"), KoinComponent {

    private val topic by argument("topic", help = "Topic to read (real or alias)").convert { TopicAliasOrRealName(it) }

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val response = service.describeTopic(ctx.cluster, topic)
        ctx.term.output.format(response.toJsonNode()) { echo(it) }
    }
}

class TopicsCreate : CliktCommand(
    name = "create",
    help = "Create a topic",
    epilog = with(globalTermColors) {
        """```
        |Examples:
        |
        |  Create a topic with a 5 partitions and a replication factor of 1:
        |  > ${bold("zoe topics create my-topic --partitions 5 --replication-factor 1")}
        |
        |  Specify additional configuration parameters:
        |  > ${
            bold(
                "zoe topics create my-topic --partitions 5 --replication-factor 1 --config retention.ms=3600 " +
                    "--config cleanup.policy=compact\n"
            )
        }
        |```""".trimMargin()
    }
), KoinComponent {

    private val topic
        by argument("topic", help = "Topic name (real or alias)").convert { TopicAliasOrRealName(it) }

    private val partitions by option("-p", "--partitions", help = "Number of partitions").int().default(1)
    private val replicationFactor
        by option("-r", "--replication-factor", help = "Replication factor").int().default(1)

    private val topicConfig: Map<String, String>
        by option("-c", "--config", help = "specify additional topic configuration (cf. examples below)")
            .associate()

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val response =
            service.createTopic(ctx.cluster, topic, partitions, replicationFactor, additionalTopicConfig = topicConfig)
        ctx.term.output.format(response.toJsonNode()) { echo(it) }
    }
}

class TopicsConsume : CliktCommand(
    name = "consume",
    help = "Consumes messages from a topic",
    epilog = with(globalTermColors) {
        """```
        |Examples:
        |
        |  Consume 10 records from the input topic from the last 2 hours from `local` aliased cluster:
        |  > ${bold("zoe -c local topics consume input -n 10 --from 'PT2h'")}
        |  
        |  Consume 5 records from the last day and filter only messages with id = 123:
        |  > ${bold("""zoe -c local topics consume input -n 10 --from 'P1d' --filter "id == '123'"""")}
        |  
        |  Select only a subset of fields and output the results as a table:
        |  > ${bold("""zoe -o table -c local topics consume input -n 10 --query '{id: id, text: text.value}'""")}
        |  
        |  Consume from the topic continuously starting from the last hour:
        |  > ${bold("""zoe -c local topics consume input --continuously --from 'PT1h'""")}
        |
        |  Using `--expose-metadata` will make zoe inject a special field (by default, named '__metadata__') containing 
        |  the record's metadata (i.e. headers, offset, key, etc.). This field can be used and accessed as any other 
        |  field in the `--query` and `--filter` options. See below for examples.
        |  
        |  Print the `text` field of the record's content and its offset:
        |  > ${
            bold(
                """zoe topics consume input \
        |       --expose-metadata \
        |       --query '{text: text, offset: __metadata__.offset}'""".trimMargin()
            )
        }
        |
        |  Same query as above but using a different alias for the metadata field injected:
        |  > ${
            bold(
                """zoe topics consume input \
        |       --expose-metadata --metadata-field-alias 'meta' \
        |       --query '{text: text, offset: meta.offset}'""".trimMargin()
            )
        }
        |```""".trimMargin()
    }
), KoinComponent {
    private val defaults: ZoeDefaults = get()

    private val from: Duration? by option(help = "Amount of time to go back in the past").convert { Duration.parse(it) }

    private val filters: List<String> by option(
        "-f",
        "--filter",
        help = "Use jmespath / jq filter expressions against records content"
    ).multiple()

    private val metadataFilters: List<String> by option("--filter-meta").multiple().deprecated(
        message = """
                --filter-meta is deprecated and will be removed in future versions.
                Use `--expose-metadata` to expose the `__metadata__` field in both `--filter` and `--query`.
                See `zoe topics consume --help` for examples.
            """.trimIndent(),
        error = true,
    )

    private val formatter by option("--formatter").default("raw")
    private val query: String? by option("--query", help = "Jmespath query to execute on each record")

    private val maxRecords: Int?
        by option("-n", "--max-records", help = "Max number of records to output").int()

    private val recordsPerBatch: Int?
        by option("--records-per-batch", help = "Max records per lambda call")
            .int()

    private val timeoutPerBatch: Long
        by option("--timeout-per-batch", help = "Timeout per lambda call")
            .long()
            .default(15000L)

    private val parallelism: Int
        by option("-j", "--jobs", help = "Number of readers to spin up in parallel")
            .int()
            .default(1)

    private val continuously: Boolean
        by option("--continuously", help = "Contiously read the topic")
            .flag(default = false)
            .validate {
                if (it && ctx.term.output != Format.Raw)
                    fail("cannot use '--continuously' with output : ${ctx.term.output}")
            }

    private val withMeta: Boolean
        by option("--with-meta").flag(default = false).deprecated(
            message = """
                    --with-meta is deprecated and will be removed in future versions.
                    Use `--expose-metadata` to expose the `__metadata__` field in both `--filter` and `--query`.
                    See `zoe topics consume --help` for examples.
                """.trimIndent(),
            error = true,
        )

    private val topic: TopicAliasOrRealName
        by argument("topic", help = "Target topic to read (alias or real)").convert { TopicAliasOrRealName(it) }

    private val dialect: JsonQueryDialect
        by option("--dialect", help = "Json query dialect to use with `--query` and `--filter`")
            .choice(JsonQueryDialect.values().associateBy { it.code })
            .default(defaults.topic.consume.jsonQueryDialect)

    private val exposeRecordsMetadata: Boolean
        by option(
            "--expose-metadata",
            help = """Expose the kafka records metadata (offset, headers, etc.) in a special field injected into the 
                    | record's content. This field will be displayed as part of the output and can be accessed within 
                    | `--query` & `--filter` queries. Use `--metadata-field-alias` to control the name of the injected field
                    |""".trimMargin()
        ).flag(default = defaults.topic.consume.exposeRecordsMetadata)

    private val metadataFieldAlias: String
        by option(
            "--metadata-field-alias",
            help = "Controls the name of the metadata field injected into the records when using --expose-metadata"
        ).default(defaults.topic.consume.metadataFieldAlias)

    private val skipNonDeserializableRecords: Boolean
        by option("-S", "--skip-non-deserializable-records", help = "Ignore records that are not deserializable")
            .flag(default = false)

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val maxRecords = maxRecords ?: (if (continuously) MAX_VALUE else 5)
        val from = when (from) {
            null -> ConsumeFrom.Earliest
            else -> ConsumeFrom.Timestamp(ts = now().minus(from).toEpochSecond() * 1000)
        }
        val stop = if (continuously) StopCondition.Continuously else StopCondition.TopicEnd
        val recordsPerBatch = recordsPerBatch ?: min(maxRecords, if (continuously) 20 else 100)

        val records =
            service
                .read(
                    cluster = ctx.cluster,
                    topic = topic,
                    from = from,
                    filters = filters,
                    query = query,
                    parallelism = parallelism,
                    numberOfRecordsPerBatch = recordsPerBatch,
                    timeoutPerBatch = timeoutPerBatch,
                    formatter = formatter,
                    stopCondition = stop,
                    dialect = dialect,
                    metadataFieldAlias = metadataFieldAlias.takeIf { exposeRecordsMetadata },
                    skipNonDeserializableRecords = skipNonDeserializableRecords,
                )
                .onEach { if (it is RecordOrProgress.Progress && !continuously) log(it.range) }
                .filter { it is RecordOrProgress.Record }
                .map { (it as RecordOrProgress.Record).record.content }
                .take(maxRecords)

        ctx.term.output.format(records) { echo(it) }
    }

    private fun log(progress: Iterable<ConsumptionRange>) = progress.forEach {
        it.progress.run {
            val message =
                "progress on partition ${String.format("%02d", it.partition)}\t" +
                    "timestamp -> ${currentTimestamp?.let { ts -> dateFmt.format(Date(ts)) }}\t" +
                    "consumed -> $numberOfRecords / ${it.until?.let { until -> until - it.from } ?: "Inf"} " +
                    "(${it.percent()}%)"

            logger.info(ctx.term.colors.yellow(message))
        }
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun ConsumptionRange.percent(): Int {
        val percent =
            until?.let { until -> (((progress.currentOffset ?: from) - from) / (until - from).toDouble()) * 100 }
        return percent?.roundToInt() ?: -1
    }
}

class TopicsProduce : CliktCommand(
    name = "produce",
    help = "produce messages into topics",
    printHelpOnEmptyArgs = true,
    epilog = with(globalTermColors) {
        """```
        |Examples:
        |
        |  Produce a record into the 'input' topic from stdin (requires input to be a json array):
        |  > ${bold("""echo '[{"id": "1", "msg": "hello"}]' | zoe -c local topics produce -t input --from-stdin""")}
        |  
        |  Use streaming mode to accept records one by one (does not require input to be a json array):
        |  > ${
            bold(
                """echo '{"id": "1", "msg": "hello"}' | zoe -c local topics produce -t input --from-stdin --streaming"""
            )
        }
        |  
        |  Use the id field of the input messages to determine the Kafka record key:
        |  > ${bold("""echo '[{"id": "1", "msg": "hello"}]' | zoe -c local topics produce -t input --from-stdin --key-path 'id'""")}
        |  
        |  Write the data from a json file (requires the content in the file to be a json array):
        |  > ${bold("""zoe -c local topics produce -t input --from-file data.json""")}
        |  
        |  Pipe data from another topic:
        |  > ${bold("""zoe -c remote topics consume input --continuously | zoe -c local topics produce -t output --from-stdin --streaming""")}
        |
        |```""".trimMargin()
    }
), KoinComponent {

    private val dryRun by option("--dry-run", help = "Do not actually produce records").flag(default = false)
    private val topic
        by option("-t", "--topic", help = "Topic to write to")
            .convert { TopicAliasOrRealName(it) }
            .required()
    private val subject by option("--subject", help = "Avro subject name to use")
    private val keyPath by option("-k", "--key-path", help = "Jmespath (or jq) expression to extract the key")
    private val valuePath by option("-v", "--value-path", help = "Jmespath (or jq) expression to extract the value")
    private val timestampPath by option("--ts-path", help = "Jmespath (or jq) expression to extract the timestamp")
    private val streaming by option("--streaming", help = "Read data line by line continuously").flag(default = false)
    private val timeoutMs by option("--timeout", help = "Timeout in millis").long().default(Long.MAX_VALUE)
    private val fromStdin by option("--from-stdin", help = "Consume data from stdin").flag(default = false)
    private val fromFile
        by option("--from-file", help = "Consume data from a json file")
            .file(mustExist = true, canBeFile = true, mustBeReadable = true)

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val input: InputStream = when {
            fromStdin -> System.`in`
            fromFile != null -> fromFile!!.inputStream()
            else -> userError("either use '--from-stdin' or '--from-file'")
        }

        withTimeout(timeoutMs) {
            fetch(input, streaming = streaming)
                .map { it.toJsonNode() }
                .let { flow ->
                    if (!streaming) {
                        flow.map {
                            require(it is ArrayNode) { "invalid data : requires a json array" }
                            it.toList()
                        }
                    } else {
                        flow.batches(5000, this)
                    }
                }
                .filter { it.isNotEmpty() }
                .collect { batch ->
                    val response = service.produce(
                        cluster = ctx.cluster,
                        topic = topic,
                        subject = subject,
                        messages = batch,
                        keyPath = keyPath,
                        valuePath = valuePath,
                        timestampPath = timestampPath,
                        dejsonifier = null,
                        dryRun = dryRun
                    )
                    ctx.term.output.format(response.toJsonNode()) { echo(it) }
                }
        }
    }
}

fun topicsCommand() = TopicsCommand()
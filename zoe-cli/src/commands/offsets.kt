package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.utils.globalTermColors
import com.adevinta.oss.zoe.core.functions.OffsetQuery
import com.adevinta.oss.zoe.core.utils.buildJson
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.GroupAliasOrRealName
import com.adevinta.oss.zoe.service.OffsetSpec
import com.adevinta.oss.zoe.service.TopicAliasOrRealName
import com.adevinta.oss.zoe.service.ZoeService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Duration

class OffsetsCommand : NoOpCliktCommand(name = "offsets", help = "Offsets management commands"), KoinComponent {
    init {
        subcommands(Read(), Write())
    }

    class Read : CliktCommand(name = "read", help = "Read consume group offsets and lag"), KoinComponent {
        private val group by option("--group", help = "Group to target").convert { GroupAliasOrRealName(it) }.required()
        private val ctx by inject<CliContext>()
        private val service by inject<ZoeService>()

        override fun run() = runBlocking {
            val offsets = service.groupOffsets(ctx.cluster, group).offsets
            ctx.term.output.format(offsets.toJsonNode()) { echo(it) }
        }
    }

    class Write : CliktCommand(
        name = "set",
        help = "Set consumer group offsets to a new position",
        printHelpOnEmptyArgs = true,
        epilog = with(globalTermColors) {
            """```
            |Examples:
            |
            |  Set offsets to earliest:
            |  > ${bold("""zoe offsets set --group my-group --topic my-topic --earliest""")}
            |  
            |  Set offsets to some specific values (e.g. partition 0 to offset 2, partition 1 to offset 10):
            |  > ${bold("""zoe offsets set --group my-group --topic my-topic --offsets 0=2,1=10""")}
            |  
            |  Set offsets to some specific timestamp:
            |  > ${bold("""zoe offsets set --group my-group --topic my-topic --timestamp 1213214121""")}
            |  
            |  Rewind offsets by some specific duration (e.g. two days):
            |  > ${bold("""zoe offsets set --group my-group --topic my-topic --rewind-by 'PT2d'""")}
            |```""".trimMargin()
        }
    ),
        KoinComponent {
        private val group by option("--group", help = "Group to target")
            .convert { GroupAliasOrRealName(it) }
            .required()

        private val ctx by inject<CliContext>()
        private val service by inject<ZoeService>()

        private val topic by option("-t", "--topic", help = "Target topic")
            .convert { TopicAliasOrRealName(it) }
            .required()

        private val offsetsSpec: OffsetSpec by mutuallyExclusiveOptions(
            option(help = "Set the group offsets to earliest or latest").switch(
                "--earliest" to OffsetSpec.Query(OffsetQuery.Beginning(), null),
                "--latest" to OffsetSpec.Query(OffsetQuery.End(), null),
            ),
            option("--timestamp", help = "Set the group offsets to the specified timestamp")
                .long()
                .convert { OffsetSpec.Query(OffsetQuery.Timestamp(it), null) },
            option(
                "--rewind-by",
                help = "Rewind the group offsets by the specified duration (ex. 'PT2d' for two days)"
            ).convert {
                OffsetSpec.Query(
                    OffsetQuery.Timestamp(System.currentTimeMillis() - Duration.parse(it).toMillis()),
                    null
                )
            },
            option("--offsets", help = "Specify offsets statically (ex. '1=200,2=100')").convert { offsets ->
                OffsetSpec.Static(
                    offsets.trim()
                        .split(",")
                        .map { it.split("=", limit = 2) }
                        .associate { (partition, offset) -> partition.toInt() to offset.toLong() }
                )
            }
        ).single().required()

        private val onlyPartitions: Set<Int>? by option("--only-partitions")
            .convert { partitions ->
                partitions
                    .split(",")
                    .map { it.toIntOrNull() ?: throw IllegalArgumentException("invalid partition: $it") }
                    .toSet()
            }

        override fun run() = runBlocking {
            val spec =
                onlyPartitions
                    ?.let {
                        when (val spec = offsetsSpec) {
                            is OffsetSpec.Static -> spec.copy(spec.offsets.filter { (partition, _) -> partition in it })
                            is OffsetSpec.Query -> spec.copy(onlyForPartitions = it)
                        }
                    }
                    ?: offsetsSpec

            service.setGroupOffsets(
                ctx.cluster,
                group = group,
                topic = topic,
                spec = spec
            )

            ctx.term.output.format(buildJson { put("success", true) }) { echo(it) }
        }
    }
}

fun offsetsCommands() = OffsetsCommand()
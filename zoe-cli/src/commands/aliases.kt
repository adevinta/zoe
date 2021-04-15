package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.utils.globalTermColors
import com.adevinta.oss.zoe.cli.utils.yaml
import com.adevinta.oss.zoe.cli.utils.yamlPrettyWriter
import com.adevinta.oss.zoe.core.utils.buildJson
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.required
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.switch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.File

class AliasesCommand : NoOpCliktCommand(name = "aliases", help = "Manage zoe aliases") {

    companion object {
        private fun aliasesFile(home: String): File = File(home).resolve("aliases.yml")
        private fun aliasesManager(home: String): AliasesManager = YamlFileSourcedAliasesManager(aliasesFile(home))

        fun aliases(home: String): Map<String, kotlin.collections.List<String>> = runBlocking {
            try {
                aliasesManager(home).list()
            } catch (err: Exception) {
                logger.warn("Error fetching the aliases", err)
                emptyMap()
            }
        }
    }

    init {
        subcommands(Init(), List(), Add(), Remove(), Edit())
    }

    class Init : CliktCommand(
        name = "init",
        help = "Load the default aliases (doesn't override current ones)"
    ), KoinComponent {
        private val ctx by inject<CliContext>()

        override fun run() = runBlocking {
            aliasesManager(ctx.home).add(
                mapOf(
                    "tc" to listOf("topics", "consume"),
                    "tp" to listOf("topics", "produce"),
                )
            )
        }

    }

    class List : CliktCommand(name = "list", help = "List currently defined aliases"), KoinComponent {
        private val ctx by inject<CliContext>()

        override fun run() = runBlocking {
            val response =
                aliasesManager(ctx.home)
                    .list()
                    .toJsonNode()
                    .let { buildJson { set<JsonNode>("aliases", it) } }

            ctx.term.output.format(response) { echo(it) }
        }
    }

    class Edit : CliktCommand(name = "edit", help = "Edit the aliases file directly"), KoinComponent {
        private val ctx by inject<CliContext>()

        override fun run() {
            TermUi.editFile(aliasesFile(ctx.home).absolutePath, requireSave = true)
        }
    }

    class Add : CliktCommand(
        name = "add",
        help = "Add an alias",
        printHelpOnEmptyArgs = true,
        epilog = with(globalTermColors) {
            """```
            |Examples:
            |
            |  Add an alias named 'ptl' for '-e pro topics list':
            |  > ${bold("""zoe aliases add --name tl -- -e pro topics list""")}
            |  
            |  Then you can use it using:
            |  > ${bold("""zoe ptl""")}
            |
            |```""".trimMargin()
        }
    ), KoinComponent {
        private val ctx by inject<CliContext>()

        private val alias by option("--name", help = "Alias name").required()
        private val command by argument("command", help = "Aliased command").multiple(required = true)

        override fun run() = runBlocking {
            aliasesManager(ctx.home).add(mapOf(alias to command))
        }
    }

    class Remove : CliktCommand(name = "remove", help = "Remove aliases"), KoinComponent {
        sealed class AliasToRemove {
            data class Single(val name: String) : AliasToRemove()
            object All : AliasToRemove()
        }

        private val ctx by inject<CliContext>()

        private val alias: AliasToRemove by mutuallyExclusiveOptions(
            option().switch("--all" to AliasToRemove.All),
            option("--name").convert { AliasToRemove.Single(it) }
        ).single().required()

        override fun run() = runBlocking {
            val manager = aliasesManager(ctx.home)
            when (val toRemove = alias) {
                is AliasToRemove.Single -> manager.remove(listOf(toRemove.name))
                AliasToRemove.All -> manager.removeAll()
            }
        }
    }


}

private interface AliasesManager {
    suspend fun add(aliases: Map<String, List<String>>)
    suspend fun remove(aliases: List<String>)
    suspend fun removeAll()
    suspend fun list(): Map<String, List<String>>
}

private class YamlFileSourcedAliasesManager(private val path: File) : AliasesManager {
    override suspend fun add(aliases: Map<String, List<String>>) = writeAliases(loadAliases() + aliases)
    override suspend fun remove(aliases: List<String>) = writeAliases(loadAliases() - aliases)
    override suspend fun removeAll() = writeAliases(emptyMap())
    override suspend fun list(): Map<String, List<String>> = loadAliases()

    private suspend fun loadAliases(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        path.takeIf { it.exists() }?.let {
            try {
                yaml.readValue<Map<String, List<String>>>(it)
            } catch (err: JsonProcessingException) {
                error("Unreadable alias file '$path'. If the problem persists, delete the file manually. (err: $err)")
            }
        } ?: emptyMap()
    }

    private suspend fun writeAliases(aliases: Map<String, List<String>>) =
        withContext(Dispatchers.IO) { yamlPrettyWriter.writeValue(path, aliases) }

}

fun aliasesCommand() = AliasesCommand()
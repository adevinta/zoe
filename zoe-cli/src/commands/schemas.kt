// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.commands.DeploySchema.SubjectNameStrategyEnum.*
import com.adevinta.oss.zoe.cli.utils.fetch
import com.adevinta.oss.zoe.cli.utils.globalTermColors
import com.adevinta.oss.zoe.core.functions.SchemaContent
import com.adevinta.oss.zoe.core.functions.SchemaContent.AvdlSchema
import com.adevinta.oss.zoe.core.functions.SchemaContent.AvscSchema
import com.adevinta.oss.zoe.core.functions.SubjectNameStrategy
import com.adevinta.oss.zoe.core.functions.TopicNameStrategySuffix
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.ZoeService
import com.adevinta.oss.zoe.service.utils.userError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.InputStream

@ExperimentalCoroutinesApi
@FlowPreview
class SchemasCommand : CliktCommand(
    name = "schemas",
    help = "List, describe or deploy schemas",
    printHelpOnEmptyArgs = true
) {

    override fun run() {}
}

@FlowPreview
@ExperimentalCoroutinesApi
class ListSchemas : CliktCommand(name = "list"), KoinComponent {
    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val cluster = ctx.requireCluster()
        val subjects = service.listSchemas(cluster).subjects.map { it.subject }
        ctx.term.output.format(mapOf("subjects" to subjects).toJsonNode()) { echo(it) }
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
class DescribeSchema : CliktCommand(name = "describe"), KoinComponent {
    private val subject: String by argument("subject", help = "Subject name to describe")

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val cluster = ctx.requireCluster()
        val subject = service.listSchemas(cluster).subjects.find { it.subject == subject }
            ?: userError("subject not found : $subject")

        ctx.term.output.format(subject.toJsonNode()) { echo(it) }
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
class DeploySchema : CliktCommand(
    name = "deploy",
    help = """
        Deploy a schema to the registry
        
        Examples :
        
        > zoe -q schemas deploy --avdl --from-file schema.avdl --name SerenityResult
        
        {"type":"actual","id":21,"subject":"com.schibsted.serenity.ad.v1.ModuleReason"}
        
        > zoe schemas deploy --avdl --from-file schema.avdl --name ModuleReason --strategy topicRecord --topic input
        
        {"type":"actual","id":21,"subject":"tenant-input-topic-com.schibsted.serenity.ad.v1.ModuleReason"}
    """,
    printHelpOnEmptyArgs = true,
    epilog = with(globalTermColors) {
        """```
        |Examples:
        |
        |  Deploy a schema named 'SerenityResult' from an .avdl file:
        |  > ${bold("""zoe -c local schemas deploy --avdl --from-file schema.avdl --name SerenityResult""")}
        |  
        |  Use the dry-run mode to check the compiled schema :
        |  > ${bold("""zoe -c local schemas deploy --avdl --from-file schema.avdl --name SerenityResult""")}
        |
        |```""".trimMargin()
    }
), KoinComponent {

    enum class SubjectNameStrategyEnum { Topic, Record, TopicRecord }
    enum class SchemaType { Avsc, Avdl }

    private val strategy
            by option("--strategy", help = "Subject naming strategy")
                .choice("topic" to Topic, "record" to Record, "topicRecord" to TopicRecord)
                .default(Record)

    private val fromStdin by option("--from-stdin", help = "Consume data from stdin").flag(default = false)
    private val fromFile
            by option("--from-file", help = "Consume data from a json file")
                .file(mustExist = true, canBeFile = true, mustBeReadable = true)

    private val topic by option("--topic", help = "Target topic")
    private val suffix
            by option("--suffix", help = "Suffix for subject name")
                .choice(TopicNameStrategySuffix.values().map { it.code to it }.toMap())

    private val content by argument("schema", help = "Schema json content").optional()

    private val type
            by option().switch(SchemaType.values().map { "--${it.name.toLowerCase()}" to it }.toMap())
                .default(SchemaType.Avsc)

    private val name
            by option("--name", help = "Name of the schema in the avdl file (required when using --avdl)")

    private val dryRun by option("--dry-run", help = "Do not actually create the schema").flag(default = false)

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val strategy: SubjectNameStrategy = strategy.let {
            when (it) {
                Topic -> {
                    requireNotNull(topic) { "--topic needs to be supplied when using strategy : $it" }
                    requireNotNull(suffix) { "--suffix needs to be supplied when using strategy : $it" }
                    SubjectNameStrategy.TopicNameStrategy(topic!!, suffix!!)
                }
                Record -> SubjectNameStrategy.RecordNameStrategy
                TopicRecord -> {
                    requireNotNull(topic) { "--topic needs to be supplied when using strategy : $it" }
                    SubjectNameStrategy.TopicRecordNameStrategy(topic!!)
                }
            }
        }

        val schema: SchemaContent = kotlin.run {
            val input: InputStream = when {
                fromStdin -> System.`in`
                fromFile != null -> fromFile!!.inputStream()
                content != null -> content!!.byteInputStream(Charsets.UTF_8)
                else -> userError("supply the schema as an argument or use one of : '--from-stdin', '--from-file'")
            }

            val content = fetch(input, streaming = false).first()

            when (type) {
                SchemaType.Avsc -> AvscSchema(content)
                SchemaType.Avdl -> AvdlSchema(
                    content, name ?: userError("--name is required when using '$type'")
                )
            }
        }

        val cluster = ctx.requireCluster()
        val subject = service.deploySchema(cluster, schema, strategy, dryRun)

        ctx.term.output.format(subject.toJsonNode()) { echo(it) }
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
fun schemasCommand() = SchemasCommand().subcommands(
    ListSchemas(),
    DescribeSchema(),
    DeploySchema()
)

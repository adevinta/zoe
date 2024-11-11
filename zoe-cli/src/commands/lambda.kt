// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.cli.commands.LambdaCommand.Companion.LambdaInfraStackName
import com.adevinta.oss.zoe.cli.commands.LambdaCommand.Companion.LambdaRoleNameOutputKey
import com.adevinta.oss.zoe.cli.commands.LambdaCommand.Companion.ZoeTags
import com.adevinta.oss.zoe.cli.config.EnvConfig
import com.adevinta.oss.zoe.cli.config.configOrDefault
import com.adevinta.oss.zoe.cli.config.resolve
import com.adevinta.oss.zoe.cli.utils.*
import com.adevinta.oss.zoe.core.Handler
import com.adevinta.oss.zoe.core.utils.buildJson
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.runners.LambdaZoeRunner
import com.adevinta.oss.zoe.service.utils.lambdaClient
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.KoinComponent
import org.koin.core.inject
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.core.SdkPojo
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient
import software.amazon.awssdk.services.cloudformation.model.*
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import software.amazon.awssdk.services.lambda.model.*
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Duration

class LambdaCommand : CliktCommand(name = "lambda", help = "Manage the AWS Lambda runner") {
    companion object {
        val ZoeTags = mapOf("service" to "zoe")
        const val LambdaInfraStackName = "zoe-infra-stack"
        const val LambdaRoleNameOutputKey = "IamRoleLambdaExecutionName"
    }

    override fun run() {}
}


class DescribeLambda : CliktCommand(name = "describe", help = "Describe the currently deployed lambda"), KoinComponent {
    private val environment by inject<EnvConfig>()
    private val ctx by inject<CliContext>()

    override fun run() = runBlocking {
        val lambdaConfig = environment.runners.configOrDefault().lambda
        val lambda = with(lambdaConfig) {
            lambdaClient(
                credentials.resolve(),
                Region.of(awsRegion)
            )
        }
        val name = LambdaZoeRunner.functionName(ctx.version, lambdaConfig.nameSuffix)
        val response =
            lambda
                .getFunctionOrNull(name)
                ?.toJsonNode()
                ?: userError("lambda function not found: $name")

        ctx.term.output.format(response) { echo(it) }
    }
}


class DeployLambda : CliktCommand(name = "deploy", help = "Deploy zoe core as an AWS lambda"), KoinComponent {

    private val ctx by inject<CliContext>()
    private val environment by inject<EnvConfig>()

    private val jarUrl: URL
        by option("--jar-url", help = "Url to the zoe jar file", envvar = "ZOE_JAR_URL")
            .convert { URL(it) }
            .defaultLazy {
                val baseUrl = "https://github.com/adevinta/zoe/releases/download"
                URL("$baseUrl/v${ctx.version}/zoe-core-${ctx.version}-all.jar")
            }

    private val dryRun: Boolean by option("--dry-run", help = "Dry run mode").flag(default = false)

    private val aws by lazy {
        with(environment.runners.configOrDefault().lambda) {
            object {
                val iam = iamClient(credentials.resolve(), Region.of(awsRegion))
                val lambda = lambdaClient(credentials.resolve(), Region.of(awsRegion))
                val cf = cfClient(credentials.resolve(), Region.of(awsRegion))
            }
        }
    }

    override fun run() = runBlocking {
        val lambdaFunctionName = LambdaZoeRunner.functionName(
            version = ctx.version,
            suffix = environment.runners.configOrDefault().lambda.nameSuffix
        )

        val template =
            loadFileFromResources("lambda.infra.cf.json")
                ?: userError("Zoe infra cloud formation template not found !")

        val infra = aws.cf.createOrUpdateStack(LambdaInfraStackName, template, ZoeTags, dryRun = dryRun)

        val roleArn = when {
            dryRun -> "not-available-on-dry-run"
            else -> {
                val stack = (infra as StackCreationResult.ActualRun).current
                val roleName = stack.getOutput(LambdaRoleNameOutputKey)
                    ?: userError("output key not found in current stack : $LambdaRoleNameOutputKey")
                aws.iam.getRole { it.roleName(roleName) }.await().role().arn()
            }
        }

        val deployConfig =
            environment.runners.configOrDefault().lambda.deploy ?: userError("you must specify a deploy config !")

        val jar = withContext(Dispatchers.IO) {
            Files
                .createTempDirectory("zoe-jar")
                .toFile()
                .also(File::deleteOnExit)
                .resolve("zoe-core.jar")
                .apply {
                    logger.info("copying zoe jar from '$jarUrl' into '$path")
                    outputStream().use { out -> jarUrl.openStream().use { inp -> inp.copyTo(out) } }
                }
        }

        val lambda = aws.lambda.createOrUpdateLambda(
            name = lambdaFunctionName,
            concurrency = deployConfig.concurrency,
            entrypoint = Handler::class.java.name,
            tags = ZoeTags,
            memory = deployConfig.memory,
            timeout = deployConfig.timeout,
            securityGroups = deployConfig.securityGroups,
            subnets = deployConfig.subnets,
            jar = jar,
            roleArn = roleArn,
            inheritFromPrevious = false,
            dryRun = dryRun
        )

        ctx.term.output.format(
            mapOf(
                "dryRun" to dryRun,
                "infra" to infra.asJson(),
                "lambda" to lambda.asJson()
            ).toJsonNode()
        ) { echo(it) }
    }
}

class DestroyLambda : CliktCommand(name = "destroy", help = "destroy lambda infrastructure"), KoinComponent {

    private val environment by inject<EnvConfig>()
    private val ctx by inject<CliContext>()

    private val all: Boolean
        by option(
            "-a",
            "--all",
            help = "Delete all existing versions of the zoe lambda function (default is only current)"
        ).flag(default = false)

    private val deleteStack: Boolean
        by option(
            "--purge-stack",
            help = "Delete the cloud formation stack supporting zoe (by default only the lambda functions are removed)"
        ).flag(default = false)

    private val dryRun: Boolean
        by option("--dry-run", help = "Safe run without actually modifying the resources")
            .flag(default = false)

    private val aws by lazy {
        with(environment.runners.configOrDefault().lambda) {
            object {
                val lambda = lambdaClient(credentials.resolve(), Region.of(awsRegion))
                val cloudformation = cfClient(credentials.resolve(), Region.of(awsRegion))
            }
        }
    }

    override fun run() = runBlocking {
        val functionsToDelete =
            if (all) {
                aws.lambda
                    .listAllFunctions()
                    .filter { it.functionName().startsWith(LambdaZoeRunner.LambdaFunctionNamePrefix) }
                    .map { it.functionName() }
                    .toList()
            } else {
                val lambdaFunctionName = LambdaZoeRunner.functionName(
                    version = ctx.version,
                    suffix = environment.runners.configOrDefault().lambda.nameSuffix
                )

                listOfNotNull(
                    aws.lambda
                        .getFunctionOrNull(lambdaFunctionName)
                        ?.configuration
                        ?.functionName()
                )
            }

        for (functionName in functionsToDelete) {
            logger.info("deleting function : $functionName (dry run: $dryRun)")
            if (!dryRun) {
                aws.lambda.deleteFunction { it.functionName(functionName) }.await()
            }
        }

        if (deleteStack) {
            aws.cloudformation.describeStack(LambdaInfraStackName)?.run {
                val name = stackName()
                logger.info("deleting stack : $name (dry run: $dryRun)")
                if (!dryRun) {
                    aws.cloudformation.deleteStack { it.stackName(name) }.await()
                    aws.cloudformation.waitForStackDeletion(name)
                }
            }
        }
    }
}

fun SdkPojo.asJson(): JsonNode = buildJson {
    for (field in sdkFields()) {
        val value = field.getValueOrDefault(this@asJson)
        set<JsonNode>(field.memberName(), if (value is SdkPojo) value.asJson() else TextNode("$value"))
    }
}

fun StackCreationResult.asJson() = buildJson {
    when (this@asJson) {
        is StackCreationResult.ActualRun -> {
            set<JsonNode>("current", current.asJson())
            set<JsonNode>("previous", previous?.asJson() ?: NullNode.instance)
            set<JsonNode>("changes", changes.map { it.asJson() }.toJsonNode())
        }
        is StackCreationResult.DryRun -> {
            set<JsonNode>("current", current?.asJson() ?: NullNode.instance)
            set<JsonNode>("changes", changes.map { it.asJson() }.toJsonNode())
        }
    }
}

fun LambdaUpdateResult.asJson() = buildJson {
    when (this@asJson) {
        is LambdaUpdateResult.ActualRun -> {
            set<JsonNode>("current", current.let {
                buildJson {
                    set<JsonNode>("configuration", it.configuration.asJson())
                    set<JsonNode>("code", it.code.asJson())
                    put("concurrency", it.concurrency)
                    set<JsonNode>("tags", it.tags.toJsonNode())
                }
            })
        }
        is LambdaUpdateResult.DryRun -> {
            set<JsonNode>("current", current?.let {
                buildJson {
                    set<JsonNode>("configuration", it.configuration.asJson())
                    set<JsonNode>("code", it.code.asJson())
                    put("concurrency", it.concurrency)
                    set<JsonNode>("tags", it.tags.toJsonNode())
                }
            })
            set<JsonNode>("request", request.asJson())
            put("concurrency", concurrency)
        }
    }
}

sealed class StackCreationResult {
    data class DryRun(val current: Stack?, val changes: List<Change>) : StackCreationResult()
    data class ActualRun(val current: Stack, val previous: Stack?, val changes: List<Change>) : StackCreationResult()
}

data class LambdaDescription(
    val configuration: FunctionConfiguration,
    val code: FunctionCodeLocation,
    val concurrency: Int?,
    val tags: Map<String, String>
)

sealed class LambdaUpdateResult {
    data class DryRun(val current: LambdaDescription?, val request: CreateFunctionRequest, val concurrency: Int?) :
        LambdaUpdateResult()

    data class ActualRun(val current: LambdaDescription) : LambdaUpdateResult()
}

fun buildLambdaZipPackage(jar: File): ByteArray = zipEntries(mapOf("lib/${jar.name}" to jar.toPath()))

internal suspend fun LambdaAsyncClient.createOrUpdateLambda(
    name: String,
    inheritFromPrevious: Boolean,
    jar: File,
    entrypoint: String,
    roleArn: String?,
    memory: Int?,
    timeout: Int?,
    tags: Map<String, String>,
    securityGroups: List<String>,
    subnets: List<String>,
    concurrency: Int?,
    dryRun: Boolean
): LambdaUpdateResult {
    val current = getFunctionOrNull(name)

    if (current != null) {
        logger.info("function already exists : $current")
    }

    val inherited = current?.takeIf { inheritFromPrevious }
    val actualConcurrency = concurrency ?: inherited?.concurrency

    val request: CreateFunctionRequest =
        CreateFunctionRequest.builder()
            .functionName(name)
            .code {
                it.zipFile(SdkBytes.fromByteBuffer(ByteBuffer.wrap(buildLambdaZipPackage(jar)).asReadOnlyBuffer()))
            }
            .memorySize(memory ?: (inherited?.configuration?.memorySize()) ?: userError("memory not provided !"))
            .timeout(timeout ?: (inherited?.configuration?.timeout()) ?: userError("timeout not provided !"))
            .runtime(Runtime.JAVA21)
            .handler(entrypoint)
            .publish(true)
            .role(inherited?.configuration?.role() ?: roleArn ?: userError("role arn must be provided !"))
            .tags(tags)
            .vpcConfig {
                it.securityGroupIds(
                    securityGroups
                        .takeUnless(List<String>::isEmpty)
                        ?: (inherited?.configuration?.vpcConfig()?.securityGroupIds())
                        ?: emptyList()
                )
                it.subnetIds(
                    subnets
                        .takeUnless(List<String>::isEmpty)
                        ?: (inherited?.configuration?.vpcConfig()?.subnetIds())
                        ?: emptyList()
                )
            }
            .overrideConfiguration { it.apiCallAttemptTimeout(Duration.ofMinutes(5)) }
            .build()

    return when {
        !dryRun -> {
            if (current != null) deleteFunction { it.functionName(current.configuration.functionName()) }.await()

            logger.info("creating function : $name")

            createFunction(request).await()

            if (actualConcurrency != null) {
                logger.info("setting concurrency to : $actualConcurrency")
                putFunctionConcurrency { it.functionName(name).reservedConcurrentExecutions(actualConcurrency) }.await()
            }

            LambdaUpdateResult.ActualRun(getFunctionOrNull(name)!!)
        }
        else -> LambdaUpdateResult.DryRun(current, request, actualConcurrency)
    }
}

internal suspend fun CloudFormationAsyncClient.createOrUpdateStack(
    name: String,
    template: String,
    tags: Map<String, String>,
    dryRun: Boolean
): StackCreationResult {
    val current = describeStack(name)

    val changeSet = kotlin.run {

        val changeSetName = "$name-cs-${System.currentTimeMillis()}"
        val shouldCreate =
            current == null || current.stackStatus() == StackStatus.REVIEW_IN_PROGRESS

        createChangeSet {
            it.stackName(name)
            it.templateBody(template)
            it.changeSetName(changeSetName)
            it.changeSetType(if (shouldCreate) ChangeSetType.CREATE else ChangeSetType.UPDATE)
            it.capabilities(Capability.CAPABILITY_NAMED_IAM)
            it.tags(tags.map { tag -> Tag.builder().key(tag.key).value(tag.value).build() })
            it.parameters(
                Parameter
                    .builder()
                    .parameterKey("ZoeFunctionName")
                    .parameterValue(LambdaZoeRunner.LambdaFunctionNamePrefix)
                    .build()
            )
        }.await()

        waitForChangeset(name, changeSetName)
    }

    if (changeSet.changes().isEmpty()) {
        logger.info("change set failed because it is empty...")
        return when {
            dryRun -> StackCreationResult.DryRun(current, emptyList())
            else -> StackCreationResult.ActualRun(current!!, current, emptyList())
        }
    }

    logger.info("change set created : $changeSet")

    return when {
        !dryRun -> {

            val newStack = run {
                executeChangeSet { it.stackName(changeSet.stackName()).changeSetName(changeSet.changeSetName()) }
                    .await()

                waitForStack(name)
            }

            logger.info("stack updated : $newStack")

            StackCreationResult.ActualRun(current = newStack, previous = current, changes = changeSet.changes())
        }
        else -> {
            StackCreationResult.DryRun(current = current, changes = changeSet.changes())
        }
    }
}

internal fun Stack.getOutput(key: String): String? = outputs().find { it.outputKey() == key }?.outputValue()

internal suspend fun LambdaAsyncClient.getFunctionOrNull(name: String): LambdaDescription? =
    try {
        val result = getFunction { it.functionName(name) }.await()
        LambdaDescription(
            configuration = result.configuration(),
            code = result.code(),
            concurrency = result.concurrency()?.reservedConcurrentExecutions(),
            tags = result.tags()
        )
    } catch (error: ResourceNotFoundException) {
        null
    }

internal suspend fun CloudFormationAsyncClient.describeStack(name: String) =
    try {
        describeStacks { it.stackName(name) }.await().stacks().firstOrNull()
    } catch (err: CloudFormationException) {
        if (err.awsErrorDetails().errorMessage().contains("does not exist")) null else throw err
    }

internal fun LambdaAsyncClient.listAllFunctions(): Flow<FunctionConfiguration> = flow {
    var nextMarker: String? = null
    do {
        val response = listFunctions { it.maxItems(10).marker(nextMarker) }.await()
        for (function in response.functions()) {
            emit(function)
        }
        nextMarker = response.nextMarker()
    } while (nextMarker != null)
}

val StackTerminalStates = setOf(
    StackStatus.CREATE_COMPLETE,
    StackStatus.CREATE_FAILED,
    StackStatus.DELETE_COMPLETE,
    StackStatus.DELETE_FAILED,
    StackStatus.ROLLBACK_COMPLETE,
    StackStatus.ROLLBACK_FAILED,
    StackStatus.UPDATE_COMPLETE,
    StackStatus.UPDATE_ROLLBACK_COMPLETE,
    StackStatus.UPDATE_ROLLBACK_FAILED
)

val StackFailedStates = setOf(
    StackStatus.CREATE_FAILED,
    StackStatus.DELETE_FAILED,
    StackStatus.ROLLBACK_COMPLETE,
    StackStatus.ROLLBACK_FAILED,
    StackStatus.UPDATE_ROLLBACK_COMPLETE,
    StackStatus.UPDATE_ROLLBACK_FAILED
)

internal suspend fun CloudFormationAsyncClient.waitForStack(
    name: String,
    statuses: Set<StackStatus> = StackTerminalStates,
    timeoutMs: Long = 60000,
    logging: Boolean = true,
    failOnError: Boolean = true
) = retrySuspendingUntilNotNull(
    timeoutMs,
    onTimeoutMsg = "timeout while waiting for stack : $name (statuses : $statuses)"
) {
    describeStack(name)
        .also { if (logging) logger.info("waiting for stack : ${it?.stackName()} (status : ${it?.stackStatus()})") }
        ?.takeIf { it.stackStatus() in statuses }
        ?.let {
            if (failOnError && it.stackStatus() in StackFailedStates)
                throw Exception("stack update failed : $it")
            else it
        }
}

internal suspend fun CloudFormationAsyncClient.waitForStackDeletion(
    name: String,
    timeoutMs: Long = 60000,
    logging: Boolean = true
) = retrySuspendingUntilNotNull(
    timeoutMs,
    onTimeoutMsg = "timeout while waiting for stack..."
) {
    describeStack(name)
        .also { if (logging) logger.info("waiting for stack deletion : ${it?.stackName()}") }
        .let {
            when {
                it == null -> true
                it.stackStatus() == StackStatus.DELETE_FAILED -> false
                else -> null
            }
        }
}

internal suspend fun CloudFormationAsyncClient.waitForChangeset(
    stackName: String,
    changeSetName: String,
    timeoutMs: Long = 60000,
    logging: Boolean = true
) = retrySuspendingUntilNotNull(
    timeoutMs,
    onTimeoutMsg = "timeout while waiting for change set...",
    block = {
        describeChangeSet { it.changeSetName(changeSetName).stackName(stackName) }
            .await()
            .also { if (logging) logger.info("waiting for change set : ${it.changeSetName()} (status : ${it.status()})") }
            .takeIf {
                val status = it.status()
                status == ChangeSetStatus.CREATE_COMPLETE ||
                    (status == ChangeSetStatus.FAILED && "didn't contain changes" in it.statusReason())
            }
    }
)

fun lambdaCommands() = LambdaCommand().subcommands(
    DescribeLambda(),
    DeployLambda(),
    DestroyLambda()
)

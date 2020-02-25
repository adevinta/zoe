// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.config

import com.adevinta.oss.zoe.cli.utils.toPrettyPrintedTable
import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.core.utils.toJsonString
import com.adevinta.oss.zoe.service.config.ClusterConfig
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList

data class EnvConfig(
    val executors: ExecutorsSection,
    val storage: StorageConfig?,
    val secrets: SecretsProviderConfig?,
    val expressions: Map<String, String> = emptyMap(),
    val clusters: Map<String, ClusterConfig> = emptyMap()
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = StorageConfig.LocalFs::class, name = "local"),
    JsonSubTypes.Type(value = StorageConfig.AwsFs::class, name = "s3")
)
sealed class StorageConfig {
    data class LocalFs(val root: String) : StorageConfig()
    data class AwsFs(val bucket: String, val prefix: String) : StorageConfig()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "provider")
@JsonSubTypes(
    JsonSubTypes.Type(value = SecretsProviderConfig.Strongbox::class, name = "strongbox"),
    JsonSubTypes.Type(value = SecretsProviderConfig.EnvVars::class, name = "env")
)
sealed class SecretsProviderConfig {
    data class Strongbox(
        val region: String,
        val credentials: AwsCredentialsConfig = AwsCredentialsConfig.Default
    ) :
        SecretsProviderConfig()

    data class EnvVars(val prepend: String?, val append: String?) : SecretsProviderConfig()
}

data class ExecutorsSection(
    val default: ExecutorName = ExecutorName.Lambda,
    val config: ExecutorsConfig = ExecutorsConfig()
)

data class ExecutorsConfig(
    val lambda: LambdaExecutorConfig = LambdaExecutorConfig(),
    val kubernetes: KubernetesExecutorConfig = KubernetesExecutorConfig(),
    val local: LocalExecutorConfig = LocalExecutorConfig()
)

data class LambdaExecutorConfig(
    val deploy: LambdaDeployConfig? = null,
    val credentials: AwsCredentialsConfig = AwsCredentialsConfig.Default,
    val awsRegion: String? = null,
    val enabled: Boolean = false
)

data class LambdaDeployConfig(
    val subnets: List<String>,
    val securityGroups: List<String>,
    val memory: Int,
    val timeout: Int?,
    val concurrency: Int?
)

data class LocalExecutorConfig(
    val enabled: Boolean = true
)

data class KubernetesExecutorConfig(
    val namespace: String = "default",
    val context: String? = null,
    val deletePodAfterCompletion: Boolean = true,
    val cpu: String = "1",
    val memory: String = "512M",
    val timeoutMs: Long = 300000
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AwsCredentialsConfig.Profile::class, name = "profile"),
    JsonSubTypes.Type(value = AwsCredentialsConfig.Static::class, name = "static"),
    JsonSubTypes.Type(value = AwsCredentialsConfig.Default::class, name = "default")
)
sealed class AwsCredentialsConfig {
    object Default : AwsCredentialsConfig()
    data class Profile(val name: String) : AwsCredentialsConfig()
    data class Static(val accessKey: String, val awsSecretAccessKey: String) : AwsCredentialsConfig()
}

fun AwsCredentialsConfig.resolve(): AWSCredentialsProvider = when (this) {
    is AwsCredentialsConfig.Default -> DefaultAWSCredentialsProviderChain()
    is AwsCredentialsConfig.Profile -> ProfileCredentialsProvider(name)
    is AwsCredentialsConfig.Static -> AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, awsSecretAccessKey))
}

enum class ExecutorName(@JsonValue val code: String) {
    Lambda("lambda"), Local("local"), Kubernetes("kubernetes")
}

enum class Format {
    Json, Raw, Table;

    fun format(content: JsonNode, foreach: (String) -> Unit) = when (this) {
        Raw -> foreach(content.toJsonString())
        Json -> foreach(content.toJsonString())
        Table -> foreach(content.toPrettyPrintedTable())
    }

    suspend fun format(records: Flow<JsonNode>, foreach: (String) -> Unit) = when (this) {
        Raw -> records.collect { foreach(it.toJsonString()) }
        else -> {
            val collected = records.toList().toJsonNode()
            format(collected, foreach)
        }
    }
}

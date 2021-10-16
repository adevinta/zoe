// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.config

import com.adevinta.oss.zoe.cli.utils.HelpMessages
import com.adevinta.oss.zoe.cli.utils.yaml
import com.adevinta.oss.zoe.core.utils.json
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.lookup.StringLookupFactory
import java.io.File
import java.net.URL

fun ConfigUrlProvider.createConfig(env: String): EnvConfig {
    val commonConfig: ObjectNode =
        find("common")?.let(::loadConfigFromUrl)?.requireObjectNode() ?: json.createObjectNode()

    val overrideConfig: ObjectNode =
        System.getenv("ZOE_CONFIG_OVERRIDE")?.let(json::readTree)?.requireObjectNode() ?: json.createObjectNode()

    val envOverrideConfig: ObjectNode =
        System.getenv("ZOE_CONFIG_OVERRIDE_${env.uppercase()}")
            ?.let(json::readTree)?.requireObjectNode() ?: json.createObjectNode()

    val envConfig: ObjectNode =
        find(env)?.let(::loadConfigFromUrl)?.requireObjectNode()
            ?: userError(
                message = "config url not found for env '$env'",
                help = HelpMessages.howToConfigureEnvironments(missingEnv = env, configUrlProvider = this)
            )

    val completeConfig = commonConfig.apply {
        setAll<JsonNode>(envConfig)
        setAll<JsonNode>(overrideConfig)
        setAll<JsonNode>(envOverrideConfig)
    }

    return completeConfig.parseJson()
}

interface ConfigUrlProvider {
    fun find(env: String): URL?
}

class ConfigUrlProviderChain(val providers: List<ConfigUrlProvider>) : ConfigUrlProvider {
    override fun find(env: String): URL? =
        providers
            .asSequence()
            .map {
                logger.debug("trying to fetch config url for env '$env' with : ${it::class.simpleName}")
                it.find(env)
            }
            .filterNotNull()
            .firstOrNull()
}

object EnvVarsConfigUrlProvider : ConfigUrlProvider {
    override fun find(env: String): URL? =
        sequenceOf("ZOE_CONFIG_URL_${env.uppercase()}", "ZOE_CONFIG_URL")
            .map { System.getenv(it)?.let(::URL) }
            .filterNotNull()
            .firstOrNull()
}

class LocalConfigDirUrlProvider(private val directory: File) : ConfigUrlProvider {
    override fun find(env: String): URL? {
        val file = directory.listFiles()?.find { it.isFile && it.nameWithoutExtension == env } ?: return null
        return file.toURI().toURL()
    }
}

private fun loadConfigFromUrl(url: URL): JsonNode {
    logger.info("loading config from url : $url")
    val content = with(StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup())) {
        replace(String(url.readBytes()))
    }
    val parsed = if (url.path.endsWith(".yml")) yaml.readTree(content) else json.readTree(content)
    return parsed ?: NullNode.getInstance()
}

private fun JsonNode.requireObjectNode(): ObjectNode {
    require(this is ObjectNode) { "expected json object, got : $this" }
    return this
}
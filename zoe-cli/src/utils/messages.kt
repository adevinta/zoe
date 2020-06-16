package com.adevinta.oss.zoe.cli.utils

import com.adevinta.oss.zoe.cli.config.ConfigUrlProvider
import com.adevinta.oss.zoe.cli.config.ConfigUrlProviderChain
import com.adevinta.oss.zoe.cli.config.LocalConfigDirUrlProvider
import com.amazonaws.services.lambda.model.ResourceNotFoundException

object HelpMessages {
    private const val DocUrl = "https://adevinta.github.io/zoe"

    fun howToConfigureEnvironments(missingEnv: String, configUrlProvider: ConfigUrlProvider): String {
        val usedUrlProviders = detectUsedConfigUrlProviders(configUrlProvider)

        return if (LocalConfigDirUrlProvider::class.java.name in usedUrlProviders) {
            if (missingEnv == "default") {
                "Zoe doesn't seem to be initialized... Have you run: `zoe config init`?"
            } else {
                "You are missing a `$missingEnv.yml` file in your config directory. " +
                    "Checkout the documentation to learn how to configure environments " +
                    "(section: ${DocUrl}/configuration/environments)"
            }
        } else {
            "Checkout the documentation to learn how to configure environments " +
                "in ${DocUrl}/configuration/environments"
        }
    }

    fun howToInitZoeLambda() = listOf(
        "It looks like the zoe lambda function is not deployed in your AWS environment. " +
            "Have you you run: `zoe -e <env> lambda deploy`?",
        "You may also need to configure the lambda runner in your zoe config. " +
            "Check out: ${DocUrl}/advanced/runners/lambda"
    )
}

private data class KnownError<T : Throwable>(val err: Class<T>, val help: (err: Throwable) -> List<String>)

private val knownErrors: List<KnownError<out Throwable>> = listOf(
    KnownError(err = ResourceNotFoundException::class.java, help = { HelpMessages.howToInitZoeLambda() })
)

fun <T : Throwable> T.help(): List<String> =
    knownErrors.find { it.err.isAssignableFrom(this::class.java) }?.help?.invoke(this) ?: emptyList()

fun detectUsedConfigUrlProviders(configUrlProvider: ConfigUrlProvider): Set<String> =
    when (configUrlProvider) {
        is ConfigUrlProviderChain -> configUrlProvider.providers.flatMap { detectUsedConfigUrlProviders(it) }.toSet()
        else -> setOf(configUrlProvider::class.java.name)
    }
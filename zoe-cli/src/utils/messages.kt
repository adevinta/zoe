package com.adevinta.oss.zoe.cli.utils

import com.adevinta.oss.zoe.cli.config.ConfigUrlProvider
import com.adevinta.oss.zoe.cli.config.ConfigUrlProviderChain
import com.adevinta.oss.zoe.cli.config.LocalConfigDirUrlProvider
import com.adevinta.oss.zoe.service.DejsonifierNotInferrable
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException

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
            "Did you run: `zoe -e <env> lambda deploy`?",
        "You may also need to configure the lambda runner in your zoe config. " +
            "Check out: ${DocUrl}/advanced/runners/lambda"
    )

    fun helpWithDejsonifierInference(error: DejsonifierNotInferrable) = when (error.reason) {
        DejsonifierNotInferrable.Reason.MissingValueDeserializer ->
            listOf(
                "You need to set the 'value.deserializer' parameter in the 'props' section of your cluster config." +
                    " For more details, checkout: $DocUrl/advanced/avro"
            )
        DejsonifierNotInferrable.Reason.MissingSubjectName ->
            listOf(
                "The subject name of a topic can be defined in the 'topics' section in the cluster config when " +
                    "defining an alias of a topic. For an example, checkout: $DocUrl/advanced/avro",
                "If you are not using a topic alias and your topic is not listed in the configuration file, " +
                    "you can specify the subject name in the command itself using `--subject <subject>`"
            )
        DejsonifierNotInferrable.Reason.MissingRegistry ->
            listOf(
                "You need to add the registry address in you cluster config. For an example, " +
                    "checkout: $DocUrl/advanced/avro"
            )
    }
}

/**
 * Known errors
 */
fun <T : Throwable> T.help(): List<String> = when (this) {
    is ResourceNotFoundException -> HelpMessages.howToInitZoeLambda()
    is DejsonifierNotInferrable -> HelpMessages.helpWithDejsonifierInference(this)
    else -> emptyList()
}

fun detectUsedConfigUrlProviders(configUrlProvider: ConfigUrlProvider): Set<String> =
    when (configUrlProvider) {
        is ConfigUrlProviderChain -> configUrlProvider.providers.flatMap { detectUsedConfigUrlProviders(it) }.toSet()
        else -> setOf(configUrlProvider::class.java.name)
    }
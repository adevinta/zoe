package com.adevinta.oss.zoe.cli.utils

import com.adevinta.oss.zoe.cli.config.ConfigUrlProvider
import com.adevinta.oss.zoe.cli.config.ConfigUrlProviderChain
import com.adevinta.oss.zoe.cli.config.EnvVarsConfigUrlProvider
import com.adevinta.oss.zoe.cli.config.LocalConfigDirUrlProvider

object HelpMessages {
    private const val DocUrl = "https://adevinta.github.io/zoe"

    fun howToConfigureEnvironments(missingEnv: String, configUrlProvider: ConfigUrlProvider): String {
        val usedUrlProviders = detectUsedConfigUrlProviders(configUrlProvider)

        return if (LocalConfigDirUrlProvider::class.java.name in usedUrlProviders) {
            if (missingEnv == "default") {
                "Zoe doesn't seem to be initialized... Have you run: `zoe config init`?"
            } else {
                "You are missing an `$missingEnv.yml` file in your config directory. " +
                    "Checkout the documentation to learn how to configure environments " +
                    "(section: ${DocUrl}/configuration/environments)"
            }
        } else {
            "Checkout the documentation to learn how to configure environments " +
                "in ${DocUrl}/configuration/environments"
        }
    }
}

private fun detectUsedConfigUrlProviders(configUrlProvider: ConfigUrlProvider): Set<String> =
    when (configUrlProvider) {
        is ConfigUrlProviderChain -> configUrlProvider.providers.flatMap { detectUsedConfigUrlProviders(it) }.toSet()
        else -> setOf(configUrlProvider::class.java.name)
    }
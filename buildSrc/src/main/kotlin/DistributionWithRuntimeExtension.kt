package com.adevinta.oss.gradle.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class DistributionWithRuntimeExtension @Inject constructor(objects: ObjectFactory) {
    internal val runtimeConfig: Property<RuntimeConfig> = objects.property(RuntimeConfig::class.java)
    internal val baseDistribution: Property<String> = objects.property(String::class.java).convention("main")

    fun runtime(configure: RuntimeConfig.() -> Unit) {
        val config = RuntimeConfig()
        config.configure()
        runtimeConfig.set(config)
    }

    fun distribution(configure: DistributionConfig.() -> Unit) {
        val config = DistributionConfig()
        config.configure()
        baseDistribution.set(config.base)
    }
}

class RuntimeConfig(
    var version: String = "11",
    var platform: Platform = Platform.Linux,
    var distribution: String = "AdoptOpenJDK"
)

class DistributionConfig(
    var base: String = "main"
)
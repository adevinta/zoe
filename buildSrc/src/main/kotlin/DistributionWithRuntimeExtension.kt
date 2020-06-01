package com.adevinta.oss.gradle.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.application.CreateStartScripts
import javax.inject.Inject

open class DistributionWithRuntimeExtension @Inject constructor(objects: ObjectFactory) {
    internal val runtimeConfig: Property<RuntimeConfig> = objects.property()
    internal val baseDistribution: Property<String> = objects.property<String>().convention("main")
    internal val startScriptCustomizer: Property<(CreateStartScripts) -> Unit> = objects.property()

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

    fun startScript(configure: (CreateStartScripts) -> Unit) = startScriptCustomizer.set(configure)
}

class RuntimeConfig(
    var version: String = "11",
    var platform: Platform = Platform.Linux,
    var distribution: String = "AdoptOpenJDK"
)

class DistributionConfig(
    var base: String = "main"
)

private inline fun <reified T> ObjectFactory.property(): Property<T> = property(T::class.java)
package com.adevinta.oss.gradle.plugins

import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class DistributionWithRuntimeExtension @Inject constructor(objects: ObjectFactory) {
    val dependencies: ListProperty<Task> = objects.listProperty(Task::class.java)
    val jreDir: DirectoryProperty = objects.directoryProperty()
}
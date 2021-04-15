// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli

import com.adevinta.oss.zoe.cli.commands.*
import com.adevinta.oss.zoe.cli.config.DefaultsProvider
import com.adevinta.oss.zoe.cli.config.HomeFileDefaultsProvider
import com.adevinta.oss.zoe.cli.config.ZoeDefaults
import com.adevinta.oss.zoe.cli.utils.useResource
import com.adevinta.oss.zoe.service.utils.userError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.system.exitProcess

val zoeHome: String by lazy {
    System.getenv("ZOE_HOME") ?: "${System.getenv("HOME") ?: userError("HOME not found")}/.zoe"
}

fun <T> withZoe(customizeContext: Context.Builder.() -> Unit = {}, action: (ZoeCommandLine) -> T): T =
    useResource(resource = startKoin { }, onClose = { stopKoin() }) { koin ->

        // Load defaults
        koin.modules(
            module {
                single<DefaultsProvider> { HomeFileDefaultsProvider() }
                single<ZoeDefaults>(createdAtStart = true) { runBlocking { get<DefaultsProvider>().defaults() } }
            }
        )

        val command =
            ZoeCommandLine()
                .subcommands(
                    topicsCommand(),
                    schemasCommand(),
                    groupsCommands(),
                    offsetsCommands(),
                    configCommands(),
                    lambdaCommands(),
                    versionCommands(),
                    aliasesCommand(),
                )
                .context(customizeContext)

        action(command)
    }

fun main(args: Array<String>) {
    val returnCode = withZoe { command ->
        command
            .runCatching { main(args) }
            .onFailure { command.printErr(it) }
            .fold(onSuccess = { 0 }, onFailure = { 1 })
    }
    exitProcess(returnCode)
}
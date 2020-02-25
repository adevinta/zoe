// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.ZoeService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinComponent
import org.koin.core.inject

class VersionCommand : CliktCommand(name = "version", help = "Zoe version info") {
    override fun run() {}
}

@ExperimentalCoroutinesApi
@FlowPreview
class VersionCheck : CliktCommand(name = "check", help = "Check zoe client and remote version"), KoinComponent {

    private val noCache: Boolean by option("--no-cache", help = "Do not use cache").flag(default = false)

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run(): Unit = runBlocking {
        val result = service.checkVersion(useCache = !noCache)
        ctx.term.output.format(result.toJsonNode()) { echo(it) }
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
fun versionCommands() = VersionCommand().subcommands(
    VersionCheck()
)

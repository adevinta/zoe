// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.cli.commands

import com.adevinta.oss.zoe.core.utils.toJsonNode
import com.adevinta.oss.zoe.service.GroupAliasOrRealName
import com.adevinta.oss.zoe.service.ZoeService
import com.adevinta.oss.zoe.service.utils.userError
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinComponent
import org.koin.core.inject


class GroupsCommand : CliktCommand(
    name = "groups",
    help = "Inspect consumer groups, their members and lag",
    printHelpOnEmptyArgs = true
) {

    override fun run() {}
}

class ListGroupsCommand : CliktCommand(name = "list"), KoinComponent {

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        ctx.term.output.format(service.listGroups(ctx.cluster, groups = emptyList()).toJsonNode()) { echo(it) }
    }
}

class DescribeGroupCommand : CliktCommand(name = "describe"), KoinComponent {
    private val group by argument("group", help = "Group to target").convert { GroupAliasOrRealName(it) }

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val group =
            service.listGroups(ctx.cluster, listOf(group)).groups.firstOrNull() ?: userError("group not found : $group")

        ctx.term.output.format(group.toJsonNode().apply { (this as ObjectNode).remove("members") }) { echo(it) }
    }
}

class GroupMembersCommand : CliktCommand(name = "members"), KoinComponent {
    private val group by argument("group", help = "Group to target").convert { GroupAliasOrRealName(it) }

    private val ctx by inject<CliContext>()
    private val service by inject<ZoeService>()

    override fun run() = runBlocking {
        val group =
            service.listGroups(ctx.cluster, listOf(group)).groups.firstOrNull() ?: userError("group not found : $group")

        ctx.term.output.format(group.members.toJsonNode()) { echo(it) }
    }
}

class GroupOffsetsCommand : CliktCommand(name = "offsets"), KoinComponent {
    private val group by argument("group", help = "Group to target").convert { GroupAliasOrRealName(it) }.optional()

    override fun run() = runBlocking {
        throw IllegalArgumentException("Command deprecated! Use 'zoe offsets read' instead")
    }
}

fun groupsCommands() = GroupsCommand().subcommands(
    ListGroupsCommand(),
    DescribeGroupCommand(),
    GroupMembersCommand(),
    GroupOffsetsCommand()
)

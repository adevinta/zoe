// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.config

import com.adevinta.oss.zoe.service.utils.userError

private val functionPattern = """^@([a-zA-Z_]+)\((.*)\)$""".toRegex()
private val argsPattern = """([^=,]*)=("(?:\\.|[^"\\]+)*"|[^,"]*)""".toRegex()
private val paramsPattern = """\{\{\s*([a-zA-Z_]+)\s*\}\}""".toRegex()

data class Param(val name: String, val original: String)

data class CalledExpression(val name: String, val args: Map<String, String>) {

    companion object {
        fun parse(expression: String): CalledExpression {
            val (name, args) = functionPattern.find(expression)?.destructured
                ?: userError("couldn't match expression : $expression")

            return when {
                args.isEmpty() -> CalledExpression(
                    name,
                    emptyMap()
                )
                !argsPattern.containsMatchIn(args) -> userError("cannot match arguments '$args' for function '$name'")
                else -> CalledExpression(
                    name = name,
                    args = argsPattern
                        .findAll(args)
                        .map { it.destructured }
                        .map { (argName, argValue) -> argName.trim() to argValue.trim('"') }
                        .toMap()
                )
            }
        }

        fun isCandidate(expression: String): Boolean = functionPattern.matches(expression)
    }
}


data class RegisteredExpression(val name: String, val expression: String) {

    val params: Set<Param> by lazy {
        paramsPattern
            .findAll(expression)
            .map {
                val (fullMatch, param) = it.groupValues
                Param(name = param, original = fullMatch)
            }
            .toSet()
    }

}

fun RegisteredExpression.call(args: Map<String, String>): String {
    return params.fold(expression) { acc, param ->
        val value = args[param.name] ?: userError("missing argument '${param.name}' for expression '$name'")
        acc.replace(param.original, value)
    }
}


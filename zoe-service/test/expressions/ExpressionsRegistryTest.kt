// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.expressions

import org.junit.Assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

private data class Expression(val exp: String, val candidate: Boolean, val parsed: CalledExpression?)

object ExpressionRegistryTest : Spek({

    val expressions = listOf(
        Expression(
            """@function(arg1="a1", arg2="a2")""",
            true,
            parsed = CalledExpression(
                "function",
                mapOf("arg1" to "a1", "arg2" to "a2")
            )
        ),
        Expression(
            """@function(arg1=a1, arg2=a2)""",
            true,
            parsed = CalledExpression(
                "function",
                mapOf("arg1" to "a1", "arg2" to "a2")
            )
        ),
        Expression(
            """function(arg1="a1", arg2="a2")""",
            false,
            parsed = null
        ),
        Expression(
            """function(arg1="a1", arg2""",
            false,
            parsed = null
        )
    )

    describe("CalledExpression logic") {

        it("detects candidates accurately") {

            expressions.forEach { (expr, isActuallyCandidate, _) ->
                Assert.assertEquals(
                    "wrong result for expression : '$expr'",
                    isActuallyCandidate,
                    CalledExpression.isCandidate(expr)
                )
            }
        }

        it("extracts args accurately") {
            expressions.filter { it.candidate }.forEach { (expr, _, expected) ->
                Assert.assertEquals(
                    "wrong result for expression : '$expr'",
                    expected,
                    CalledExpression.parse(expr)
                )
            }
        }
    }

    describe("RegisteredExpression logic") {

        it("parses expression") {
            val filter = RegisteredExpression(
                "exp",
                "moduleReason[?name == '{{ arg }}']"
            )
            Assert.assertEquals(
                setOf(Param("arg", "{{ arg }}")),
                filter.params
            )
        }
    }
})
#!/usr/bin/env kscript
// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

@file:MavenRepository("confluent", "https://packages.confluent.io/maven/")
@file:DependsOn("com.kjetland:mbknor-jackson-jsonschema_2.12:1.0.36")
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")
@file:DependsOn("com.adevinta.oss:zoe-core:1.1")

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator
import com.adevinta.oss.zoe.core.FunctionsRegistry
import com.adevinta.oss.zoe.core.functions.JsonZoeFunction

val mapper = ObjectMapper()
val schema = JsonSchemaGenerator(mapper)

val response: ArrayNode = mapper.createArrayNode().apply {
    for (function in FunctionsRegistry.list()) {
        if (function is JsonZoeFunction<*, *>) {
            val response = mapper.createObjectNode()
            println("resolving : ${function.name()}")
            response.put("function", function.name())
            response.set<ObjectNode>(
                    "payloadSchema",
                    schema.generateJsonSchema<Any>(mapper.typeFactory.constructType(function.payloadType().type))
            )
            response.set<ObjectNode>(
                    "responseSchema",
                    schema.generateJsonSchema<Any>(mapper.typeFactory.constructType(function.responseType().type))
            )
            add(response)
        }
    }
}

println(response.toString())

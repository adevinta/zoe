package com.adevinta.oss.zoe.core.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.NullNode
import io.burt.jmespath.Expression
import io.burt.jmespath.jackson.JacksonRuntime
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Versions
import java.util.concurrent.ConcurrentHashMap

interface JsonSearch {
    fun validate(expr: String)
    fun query(obj: JsonNode, expr: String): JsonNode
}

class JmespathImpl : JsonSearch {

    private val cache = ConcurrentHashMap<String, Expression<JsonNode>>()
    private val jmespath = JacksonRuntime()

    override fun validate(expr: String) {
        runCatching { getOrCompile(expr) }.onFailure { err ->
            throw IllegalArgumentException("Invalid jmespath expression '$expr'", err)
        }
    }

    override fun query(obj: JsonNode, expr: String): JsonNode {
        val compiled = getOrCompile(expr)
        return compiled.search(obj)
    }

    private fun getOrCompile(expr: String) = cache.computeIfAbsent(expr) { jmespath.compile(expr) }
}

class JqImpl : JsonSearch {

    private val cache = ConcurrentHashMap<String, JsonQuery>()
    private val scope = Scope.newEmptyScope().apply {
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, this)
    }

    override fun validate(expr: String) {
        runCatching { getOrCompile(expr) }.onFailure { err ->
            throw IllegalArgumentException("Invalid jq expression '$expr'", err)
        }
    }

    override fun query(obj: JsonNode, expr: String): JsonNode {
        val compiled = getOrCompile(expr)
        val results = ArrayList<JsonNode>().apply {
            compiled.apply(scope, obj) { add(it) }
        }
        return if (results.size <= 1) {
            results.firstOrNull() ?: NullNode.instance
        } else {
            json.createArrayNode().addAll(results)
        }
    }

    private fun getOrCompile(expr: String) = cache.computeIfAbsent(expr) { JsonQuery.compile(expr, Versions.JQ_1_6) }
}

fun JsonSearch.match(obj: JsonNode, filters: List<String>): Boolean =
    filters.all { expr -> query(obj, expr).asBoolean() }
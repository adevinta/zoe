// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.config

import com.adevinta.oss.zoe.service.expressions.RegisteredExpression
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import java.util.concurrent.CompletableFuture

val s3: AmazonS3 by lazy { AmazonS3ClientBuilder.defaultClient() }

interface ConfigStore {
    fun cluster(name: String): CompletableFuture<Cluster?>
    fun expression(name: String): CompletableFuture<RegisteredExpression?>
}

class InMemoryConfigStore(
    private val clusters: Map<String, Cluster>,
    private val filters: Map<String, RegisteredExpression>
) : ConfigStore {

    override fun cluster(name: String): CompletableFuture<Cluster?> =
        CompletableFuture.completedFuture(clusters[name])

    override fun expression(name: String): CompletableFuture<RegisteredExpression?> =
        CompletableFuture.completedFuture(filters[name])
}

data class Cluster(
    val registry: String?,
    val topics: Map<TopicAlias, Topic> = mapOf(),
    val props: Map<String, String>,
    val groups: Map<GroupAlias, ConsumerGroup> = mapOf()
)

data class Topic(val name: String, val subject: String?)
data class ConsumerGroup(val name: String)

typealias TopicAlias = String
typealias GroupAlias = String

// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.executors

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonString
import kotlinx.coroutines.future.await

suspend fun <T : Any> ZoeExecutor.launchAwait(function: String, config: T): String {
    logger.debug("launching function '${function}'")
    return launch(function, config.toJsonString()).await()
}

suspend fun ZoeExecutor.topics(config: AdminConfig): ListTopicsResponse {
    logger.info("requesting topics...")
    return launchAwait(listTopics.name(), config).parseJson()
}

suspend fun ZoeExecutor.poll(config: PollConfig): PollResponse {
    logger.info("polling topic '${config.topic}' (subscription : ${config.subscription})")
    return launchAwait(poll.name(), config).parseJson()
}

suspend fun ZoeExecutor.schemas(config: ListSchemasConfig): ListSchemasResponse {
    logger.info("requesting schemas...")
    return launchAwait(listSchemas.name(), config).parseJson()
}

suspend fun ZoeExecutor.produce(config: ProduceConfig): ProduceResponse {
    logger.info("producing '${config.data.size}' records to topic '${config.topic}'")
    return launchAwait(produce.name(), config).parseJson()
}

suspend fun ZoeExecutor.groups(config: GroupsConfig): ListGroupsResponse {
    logger.info("requesting groups : '${config.groups}'")
    return launchAwait(listGroups.name(), config).parseJson()
}

suspend fun ZoeExecutor.offsets(config: GroupConfig): GroupOffsetsResponse {
    logger.info("requesting offsets for group : ${config.group}")
    return launchAwait(offsets.name(), config).parseJson()
}

suspend fun ZoeExecutor.deploySchema(config: DeploySchemaConfig): DeploySchemaResponse {
    return launchAwait(deploySchema.name(), config).parseJson()
}

suspend fun ZoeExecutor.version(config: VersionCheckRequest): VersionCheckResponse {
    logger.info("checking zoe remote version...")
    return launchAwait(version.name(), config).parseJson()
}

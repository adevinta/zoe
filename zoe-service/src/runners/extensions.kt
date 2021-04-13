// Copyright (c) 2020 Adevinta.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package com.adevinta.oss.zoe.service.runners

import com.adevinta.oss.zoe.core.functions.*
import com.adevinta.oss.zoe.core.utils.logger
import com.adevinta.oss.zoe.core.utils.parseJson
import com.adevinta.oss.zoe.core.utils.toJsonString

suspend fun ZoeRunner.topics(config: AdminConfig): ListTopicsResponse {
    logger.info("requesting topics...")
    return launch(listTopics.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.createTopic(config: CreateTopicRequest): CreateTopicResponse {
    logger.info("creating topic: ${config.name}")
    return launch(createTopic.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.poll(config: PollConfig): PollResponse {
    logger.info("polling topic '${config.topic}' (subscription : ${config.subscription})")
    return launch(poll.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.listSchemas(config: ListSchemasConfig): ListSchemasResponse {
    logger.info("listing schemas ${config.regexFilter?.let { "matching pattern: $it" } ?: ""}")
    return launch(listSchemas.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.describeSchema(config: DescribeSchemaConfig): DescribeSchemaResponse {
    logger.info("describing schema: ${config.subject}")
    return launch(describeSchema.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.produce(config: ProduceConfig): ProduceResponse {
    logger.info("producing '${config.data.size}' records to topic '${config.topic}'")
    return launch(produce.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.groups(config: GroupsConfig): ListGroupsResponse {
    logger.info("requesting groups : '${config.groups}'")
    return launch(listGroups.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.offsets(config: GroupConfig): GroupOffsetsResponse {
    logger.info("requesting offsets for group : ${config.group}")
    return launch(offsets.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.queryOffsets(config: OffsetQueriesRequest): OffsetQueriesResponse {
    logger.info("querying offsets for topic: ${config.topic}")
    return launch(queryOffsets.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.setOffsets(config: SetOffsetRequest): SetOffsetResponse {
    logger.info("setting offsets for group: ${config.groupId} (${config.offsets})")
    return launch(setOffsets.name(), config.toJsonString()).parseJson()
}


suspend fun ZoeRunner.deploySchema(config: DeploySchemaConfig): DeploySchemaResponse {
    return launch(deploySchema.name(), config.toJsonString()).parseJson()
}

suspend fun ZoeRunner.version(config: VersionCheckRequest): VersionCheckResponse {
    logger.info("checking zoe remote version...")
    return launch(version.name(), config.toJsonString()).parseJson()
}

package com.example.hama.common.model

import kotlinx.serialization.json.JsonObject

/**
 * MCP 요청 데이터 모델
 */
data class McpRequest(
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val requestId: String
)
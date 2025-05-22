package com.example.hama.common.model

import kotlinx.serialization.json.JsonObject

/**
 * MCP 응답 데이터 모델
 */
data class McpResponse(
    val requestId: String,
    val data: JsonObject
)
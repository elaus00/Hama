package com.example.hama.common.model

import kotlinx.serialization.json.JsonObject

/**
 * MCP 도구 응답 데이터 모델
 */
data class ToolResponse(
    val content: List<ToolResponseContent>
)

sealed class ToolResponseContent {
    data class Text(val text: String) : ToolResponseContent()
    data class Image(val url: String) : ToolResponseContent()
    data class Json(val data: JsonObject) : ToolResponseContent()
}
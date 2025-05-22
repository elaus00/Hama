package com.example.hama.common.model

import kotlinx.serialization.json.JsonObject

/**
 * MCP 서버 정보 데이터 모델
 */
data class ServerInfo(
    val serverVersion: JsonObject?,
    val capabilities: JsonObject?,
    val instructions: String?
)
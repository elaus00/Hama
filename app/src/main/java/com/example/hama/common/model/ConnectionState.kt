package com.example.hama.common.model

/**
 * MCP 서버 연결 상태를 나타내는 sealed 클래스
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val clientId: String, val serverName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
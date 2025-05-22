package com.example.hama.common.model

/**
 * 로그 메시지 데이터 모델
 */
data class LogMessage(
    val type: String,  // log, error, stderr
    val level: String? = null,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
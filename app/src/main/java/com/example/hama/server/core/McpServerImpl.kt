package com.example.hama.server.core

import com.example.hama.common.util.LogUtils

/**
 * MCP 서버 구현체
 * SSE 방식으로 클라이언트와 통신하는 MCP 서버
 */
class McpServerImpl {
    
    companion object {
        private const val TAG = "McpServerImpl"
    }
    
    private var isRunning = false
    
    /**
     * MCP 서버 시작
     */
    fun start() {
        if (isRunning) {
            LogUtils.w(TAG, "MCP 서버가 이미 실행 중입니다")
            return
        }
        
        LogUtils.d(TAG, "MCP 서버 시작")
        isRunning = true
        
        // TODO: SSE 서버 시작 구현
    }
    
    /**
     * MCP 서버 중지
     */
    fun stop() {
        if (!isRunning) {
            LogUtils.w(TAG, "MCP 서버가 실행 중이 아닙니다")
            return
        }
        
        LogUtils.d(TAG, "MCP 서버 중지")
        isRunning = false
        
        // TODO: SSE 서버 중지 구현
    }
    
    /**
     * 서버 실행 상태 확인
     */
    fun isRunning(): Boolean {
        return isRunning
    }
}
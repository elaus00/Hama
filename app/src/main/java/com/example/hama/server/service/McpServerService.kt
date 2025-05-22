package com.example.hama.server.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.hama.common.util.LogUtils

/**
 * MCP 서버 백그라운드 서비스
 * 로컬 MCP 서버를 실행하여 모바일 컨텍스트를 제공
 */
class McpServerService : Service() {
    
    companion object {
        private const val TAG = "McpServerService"
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        LogUtils.d(TAG, "MCP 서버 서비스 생성")
        // TODO: MCP 서버 초기화
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(TAG, "MCP 서버 서비스 시작")
        // TODO: MCP 서버 시작
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d(TAG, "MCP 서버 서비스 종료")
        // TODO: MCP 서버 정리
    }
}
package com.example.hama.server.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.hama.MainActivity
import com.example.hama.server.core.McpServerImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MCP 서버 서비스
 * 백그라운드에서 MCP 서버를 실행하는 Android 서비스
 */
@AndroidEntryPoint
class McpServerService : Service() {

    companion object {
        private const val TAG = "McpServerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "hama_mcp_server_channel"
    }

    @Inject
    lateinit var mcpServer: McpServerImpl

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): McpServerService = this@McpServerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "McpServerService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "McpServerService started")
        startForeground(NOTIFICATION_ID, createNotification())
        startServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "McpServerService destroyed")
        stopServer()
    }

    /**
     * 서버 시작
     */
    private fun startServer() {
        Log.d(TAG, "Starting MCP server")
        serverJob = serviceScope.launch {
            try {
                mcpServer.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting MCP server", e)
            }
        }
    }

    /**
     * 서버 중지
     */
    private fun stopServer() {
        Log.d(TAG, "Stopping MCP server")
        serverJob?.cancel()
        mcpServer.stop()
    }

    /**
     * 알림 채널 생성
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MCP Server"
            val descriptionText = "MCP Server 실행 중"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 알림 생성
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server 실행 중")
            .setContentText("MCP 서버가 실행 중입니다")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }
}
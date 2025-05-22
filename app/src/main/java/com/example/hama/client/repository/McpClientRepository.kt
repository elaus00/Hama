package com.example.hama.client.repository

import android.util.Log
import com.example.hama.common.model.ConnectionState
import com.example.hama.common.model.LogMessage
import com.example.hama.common.model.ServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 클라이언트 리포지토리
 * WebSocket을 통해 MCP 프록시 서버와 통신
 */
@Singleton
class McpClientRepository @Inject constructor() {
    companion object {
        private const val TAG = "McpClientRepository"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // 연결 상태
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 서버 정보
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    // 로그 메시지
    private val _logMessages = MutableStateFlow<List<LogMessage>>(emptyList())
    val logMessages: StateFlow<List<LogMessage>> = _logMessages.asStateFlow()

    // 이벤트 채널
    private val _events = Channel<McpEvent>(Channel.BUFFERED)
    val events: Flow<McpEvent> = _events.receiveAsFlow()

    // 연결 중 상태 추적
    private val isConnecting = AtomicBoolean(false)

    // 클라이언트 ID
    private var clientId: String? = null

    // 현재 사용 중인 서버 이름
    private var currentServerName: String? = null

    // WebSocket 클라이언트
    private var webSocketClient: WebSocketClient? = null

    // 요청/응답 관리
    private val pendingRequests = ConcurrentHashMap<String, ((Result<JsonObject>) -> Unit)>()

    // MCP 이벤트 sealed 클래스
    sealed class McpEvent {
        data class ResourcesChanged(val resources: JsonObject) : McpEvent()
        data class ServerChanged(val serverName: String) : McpEvent()
    }

    /**
     * 서버에 연결
     */
    fun connect(serverUrl: String) {
        if (isConnecting.getAndSet(true)) {
            Log.d(TAG, "이미 연결 중입니다")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        addLog(LogMessage("log", "info", "서버에 연결 중..."))

        try {
            val uri = URI(serverUrl)
            webSocketClient = createWebSocketClient(uri)
            webSocketClient?.connect()
        } catch (e: Exception) {
            isConnecting.set(false)
            _connectionState.value = ConnectionState.Error("연결 실패: ${e.message}")
            addLog(LogMessage("error", "error", "연결 실패: ${e.message}"))
            Log.e(TAG, "WebSocket 연결 중 오류", e)
        }
    }

    /**
     * 연결 해제
     */
    fun disconnect() {
        addLog(LogMessage("log", "info", "연결 종료 중..."))
        webSocketClient?.close()
        webSocketClient = null
        clientId = null
        currentServerName = null
        _connectionState.value = ConnectionState.Disconnected
        isConnecting.set(false)

        // 대기 중인 모든 요청에 오류 응답
        val requests = pendingRequests.toMap()
        pendingRequests.clear()

        coroutineScope.launch {
            requests.forEach { (_, callback) ->
                callback(Result.failure(Exception("연결이 종료되었습니다")))
            }
        }
    }

    /**
     * 로그 메시지 추가
     */
    private fun addLog(message: LogMessage) {
        _logMessages.value = _logMessages.value + message
    }

    /**
     * MCP 요청 전송
     */
    fun sendRequest(method: String, params: JsonObject = JsonObject(emptyMap()), callback: (Result<JsonObject>) -> Unit) {
        if (_connectionState.value !is ConnectionState.Connected) {
            callback(Result.failure(IllegalStateException("연결되지 않은 상태에서 요청을 보낼 수 없습니다")))
            return
        }

        val requestId = UUID.randomUUID().toString()
        pendingRequests[requestId] = callback

        val requestObject = buildJsonObject {
            put("type", "mcp-request")
            put("requestId", requestId)
            put("data", buildJsonObject {
                put("method", method)
                put("params", params)
            })
        }

        webSocketClient?.send(requestObject.toString())
        addLog(LogMessage("log", "info", "요청 전송: $method"))
    }

    /**
     * 도구 목록 조회
     */
    fun listTools(callback: (Result<JsonObject>) -> Unit) {
        sendRequest("tools/list", callback = callback)
    }

    /**
     * 도구 호출
     */
    fun callTool(name: String, args: JsonObject, callback: (Result<JsonObject>) -> Unit) {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", args)
        }
        sendRequest("tools/call", params, callback)
    }

    /**
     * 프롬프트 목록 조회
     */
    fun listPrompts(callback: (Result<JsonObject>) -> Unit) {
        sendRequest("prompts/list", callback = callback)
    }

    /**
     * 프롬프트 조회
     */
    fun getPrompt(name: String, args: JsonObject, callback: (Result<JsonObject>) -> Unit) {
        val params = buildJsonObject {
            put("name", name)
            put("arguments", args)
        }
        sendRequest("prompts/get", params, callback)
    }

    /**
     * 리소스 목록 조회
     */
    fun listResources(callback: (Result<JsonObject>) -> Unit) {
        sendRequest("resources/list", callback = callback)
    }

    /**
     * 서버 관리 요청 전송
     */
    fun sendServerAdminRequest(action: String, serverName: String? = null, callback: (Result<JsonObject>) -> Unit) {
        if (_connectionState.value !is ConnectionState.Connected) {
            callback(Result.failure(IllegalStateException("연결되지 않은 상태에서 요청을 보낼 수 없습니다")))
            return
        }

        val requestId = UUID.randomUUID().toString()
        pendingRequests[requestId] = callback

        val requestObject = buildJsonObject {
            put("type", "server-admin")
            put("requestId", requestId)
            put("action", action)
            if (serverName != null) {
                put("serverName", serverName)
            }
        }

        webSocketClient?.send(requestObject.toString())
        addLog(LogMessage("log", "info", "서버 관리 요청 전송: $action"))
    }

    /**
     * 사용 가능한 서버 목록 조회
     */
    fun listServers(callback: (Result<JsonObject>) -> Unit) {
        sendServerAdminRequest("list-servers", callback = callback)
    }

    /**
     * 서버 전환
     */
    fun switchServer(serverName: String, callback: (Result<JsonObject>) -> Unit) {
        sendServerAdminRequest("switch-server", serverName, callback)
    }

    /**
     * WebSocket 클라이언트 생성
     */
    private fun createWebSocketClient(uri: URI): WebSocketClient {
        return object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "WebSocket 연결됨")
                addLog(LogMessage("log", "info", "WebSocket 연결됨"))
            }

            override fun onMessage(message: String?) {
                message?.let {
                    try {
                        val jsonElement = Json.parseToJsonElement(it)
                        val jsonObject = jsonElement.jsonObject
                        val type = jsonObject["type"]?.jsonPrimitive?.content

                        when (type) {
                            "connection-established" -> {
                                clientId = jsonObject["clientId"]?.jsonPrimitive?.content
                                currentServerName = jsonObject["serverName"]?.jsonPrimitive?.content ?: "default"
                                isConnecting.set(false)
                                _connectionState.value = ConnectionState.Connected(clientId!!, currentServerName!!)

                                // 서버 정보 설정
                                val serverVersion = jsonObject["serverInfo"]?.jsonObject
                                val capabilities = jsonObject["capabilities"]?.jsonObject
                                val instructions = jsonObject["instructions"]?.jsonPrimitive?.content

                                _serverInfo.value = ServerInfo(serverVersion, capabilities, instructions)

                                addLog(LogMessage("log", "info", "MCP 서버에 연결됨, 서버: $currentServerName"))
                                Log.d(TAG, "MCP 서버에 연결됨, 클라이언트 ID: $clientId, 서버: $currentServerName")
                            }
                            "mcp-response" -> {
                                val requestId = jsonObject["requestId"]?.jsonPrimitive?.content
                                val data = jsonObject["data"]?.jsonObject

                                // 요청 처리
                                if (requestId != null && data != null) {
                                    val callback = pendingRequests.remove(requestId)
                                    callback?.invoke(Result.success(data))
                                }
                            }
                            "mcp-error" -> {
                                val errorMessage = jsonObject["data"]?.jsonPrimitive?.content ?: "알 수 없는 오류"
                                val requestId = jsonObject["requestId"]?.jsonPrimitive?.content

                                if (requestId != null) {
                                    // 특정 요청에 대한 오류
                                    val callback = pendingRequests.remove(requestId)
                                    callback?.invoke(Result.failure(Exception(errorMessage)))
                                }

                                addLog(LogMessage("error", "error", errorMessage))
                                Log.e(TAG, "MCP 오류: $errorMessage")
                            }
                            "mcp-log" -> {
                                val logData = jsonObject["data"]?.jsonObject
                                val level = logData?.get("level")?.jsonPrimitive?.content
                                val logMessage = logData?.get("message")?.jsonPrimitive?.content ?: "로그 메시지 없음"

                                addLog(LogMessage("log", level, logMessage))
                                Log.d(TAG, "MCP 로그 [$level]: $logMessage")
                            }
                            "mcp-stderr" -> {
                                val stderrData = jsonObject["data"]?.jsonPrimitive?.content ?: "오류 메시지 없음"
                                addLog(LogMessage("stderr", "error", stderrData))
                                Log.e(TAG, "MCP 표준 오류: $stderrData")
                            }
                            "mcp-resources-changed" -> {
                                // 리소스 변경 알림 처리
                                val resources = jsonObject["data"]?.jsonObject ?: JsonObject(emptyMap())
                                addLog(LogMessage("log", "info", "리소스 변경됨"))

                                coroutineScope.launch {
                                    _events.send(McpEvent.ResourcesChanged(resources))
                                }
                            }
                            "server-admin-response" -> {
                                val requestId = jsonObject["requestId"]?.jsonPrimitive?.content
                                val data = jsonObject["data"]?.jsonObject

                                // 요청 처리
                                if (requestId != null && data != null) {
                                    val callback = pendingRequests.remove(requestId)
                                    callback?.invoke(Result.success(data))

                                    // 서버 전환 응답 처리
                                    data["message"]?.jsonPrimitive?.content?.let { msg ->
                                        if (msg.contains("서버 전환 요청됨")) {
                                            addLog(LogMessage("log", "info", msg))
                                        }
                                    }
                                }
                            }
                            "server-admin-error" -> {
                                val errorMessage = jsonObject["data"]?.jsonPrimitive?.content ?: "알 수 없는 오류"
                                val requestId = jsonObject["requestId"]?.jsonPrimitive?.content

                                if (requestId != null) {
                                    // 특정 요청에 대한 오류
                                    val callback = pendingRequests.remove(requestId)
                                    callback?.invoke(Result.failure(Exception(errorMessage)))
                                }

                                addLog(LogMessage("error", "error", "서버 관리 오류: $errorMessage"))
                                Log.e(TAG, "서버 관리 오류: $errorMessage")
                            }
                        }
                    } catch (e: Exception) {
                        addLog(LogMessage("error", "error", "메시지 처리 중 오류: ${e.message}"))
                        Log.e(TAG, "메시지 처리 중 오류", e)
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "WebSocket 연결 종료: $code, $reason, remote: $remote")
                addLog(LogMessage("log", "info", "연결 종료됨: $reason"))

                isConnecting.set(false)
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "WebSocket 오류", ex)
                addLog(LogMessage("error", "error", "WebSocket 오류: ${ex?.message}"))
                isConnecting.set(false)
                _connectionState.value = ConnectionState.Error("WebSocket 오류: ${ex?.message}")
            }
        }
    }
}
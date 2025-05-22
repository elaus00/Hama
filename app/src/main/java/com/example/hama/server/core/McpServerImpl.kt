package com.example.hama.server.core

import android.content.Context
import android.util.Log
import com.example.hama.server.context.AppContextProvider
import com.example.hama.server.context.DeviceContextProvider
import com.example.hama.server.tool.AccessibilityTool
import com.example.hama.server.tool.ScreenCaptureTool
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * MCP 서버 구현
 * SSE 방식으로 클라이언트와 통신하고 MCP 도구를 관리
 */
@Singleton
class McpServerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenCaptureTool: ScreenCaptureTool,
    private val accessibilityTool: AccessibilityTool,
    private val appContextProvider: AppContextProvider,
    private val deviceContextProvider: DeviceContextProvider
) : CoroutineScope {

    companion object {
        private const val TAG = "McpServerImpl"
        private const val SERVER_PORT = 8080
        private const val PING_INTERVAL_MS = 30000L // 30초마다 핑
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    private var server: ApplicationEngine? = null
    private val isRunning = AtomicBoolean(false)
    private val clients = ConcurrentHashMap<String, WebSocketSession>()
    private var pingJob: Job? = null

    /**
     * 서버 시작
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Server already running")
            return
        }

        try {
            Log.d(TAG, "Starting MCP server on port $SERVER_PORT")
            server = embeddedServer(Netty, port = SERVER_PORT) {
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(30)
                    maxFrameSize = Long.MAX_VALUE
                }
                install(CORS) {
                    method(HttpMethod.Options)
                    method(HttpMethod.Get)
                    method(HttpMethod.Post)
                    method(HttpMethod.Put)
                    method(HttpMethod.Delete)
                    method(HttpMethod.Patch)
                    header(HttpHeaders.AccessControlAllowHeaders)
                    header(HttpHeaders.ContentType)
                    header(HttpHeaders.AccessControlAllowOrigin)
                    anyHost()
                }
                configureSseRouting()
            }
            server?.start(wait = false)
            startPingJob()
            Log.d(TAG, "MCP server started")
        } catch (e: Exception) {
            isRunning.set(false)
            Log.e(TAG, "Error starting MCP server", e)
            throw e
        }
    }

    /**
     * 서버 중지
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            Log.d(TAG, "Server already stopped")
            return
        }

        try {
            pingJob?.cancel()
            pingJob = null
            clients.clear()
            server?.stop(500, 1000)
            server = null
            Log.d(TAG, "MCP server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MCP server", e)
        }
    }

    /**
     * SSE 라우팅 설정
     */
    private fun Application.configureSseRouting() {
        routing {
            // 상태 확인용 엔드포인트
            get("/") {
                call.respondText("MCP Server Running", ContentType.Text.Plain)
            }

            // WebSocket 연결 엔드포인트
            webSocket("/mcp") {
                val clientId = UUID.randomUUID().toString()
                try {
                    clients[clientId] = this
                    Log.d(TAG, "Client connected: $clientId")
                    
                    // 연결 성공 메시지 전송
                    val connectionMessage = buildJsonObject {
                        put("type", "connection-established")
                        put("clientId", clientId)
                        put("serverName", "hama-android-mcp")
                        put("serverInfo", buildJsonObject {
                            put("version", "0.1.0")
                            put("platform", "android")
                        })
                        put("capabilities", buildJsonObject {
                            put("screenCapture", true)
                            put("accessibility", true)
                            put("deviceInfo", true)
                        })
                    }
                    send(Frame.Text(connectionMessage.toString()))

                    // 메시지 처리
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val message = frame.readText()
                                handleClientMessage(clientId, message)
                            }
                            else -> {
                                Log.d(TAG, "Received other frame: $frame")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in WebSocket connection", e)
                } finally {
                    clients.remove(clientId)
                    Log.d(TAG, "Client disconnected: $clientId")
                }
            }
        }
    }

    /**
     * 클라이언트 메시지 처리
     */
    private suspend fun handleClientMessage(clientId: String, message: String) {
        try {
            val session = clients[clientId] ?: return
            val jsonElement = Json.parseToJsonElement(message)
            val jsonObject = jsonElement.jsonObject
            
            // 메시지 타입에 따라 처리
            when (val type = jsonObject["type"]?.toString()?.trim('"')) {
                "mcp-request" -> {
                    val requestId = jsonObject["requestId"]?.toString()?.trim('"')
                    val data = jsonObject["data"]?.jsonObject
                    
                    if (requestId != null && data != null) {
                        val method = data["method"]?.toString()?.trim('"')
                        val params = data["params"]?.jsonObject ?: JsonObject(emptyMap())
                        
                        if (method != null) {
                            handleMcpRequest(session, requestId, method, params)
                        } else {
                            sendErrorResponse(session, requestId, "Method is required")
                        }
                    } else {
                        sendErrorResponse(session, requestId ?: "", "Invalid request format")
                    }
                }
                else -> {
                    Log.d(TAG, "Received unsupported message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client message", e)
        }
    }

    /**
     * MCP 요청 처리
     */
    private suspend fun handleMcpRequest(
        session: WebSocketSession,
        requestId: String,
        method: String,
        params: JsonObject
    ) {
        Log.d(TAG, "Handling MCP request: $method")
        
        try {
            when (method) {
                "tools/list" -> {
                    val toolsResponse = buildJsonObject {
                        put("tools", json.parseToJsonElement("[
                            {
                                \"name\": \"screenCapture\",
                                \"description\": \"화면 캡처\",
                                \"version\": \"0.1.0\"
                            },
                            {
                                \"name\": \"accessibility\",
                                \"description\": \"접근성 정보\",
                                \"version\": \"0.1.0\"
                            },
                            {
                                \"name\": \"deviceInfo\",
                                \"description\": \"장치 정보\",
                                \"version\": \"0.1.0\"
                            },
                            {
                                \"name\": \"appContext\",
                                \"description\": \"앱 컨텍스트\",
                                \"version\": \"0.1.0\"
                            }
                        ]"))
                    }
                    sendResponse(session, requestId, toolsResponse)
                }
                "tools/call" -> {
                    val toolName = params["name"]?.toString()?.trim('"')
                    val args = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                    
                    if (toolName != null) {
                        handleToolCall(session, requestId, toolName, args)
                    } else {
                        sendErrorResponse(session, requestId, "Tool name is required")
                    }
                }
                "resources/list" -> {
                    // 리소스 목록 반환 (아직 구현X)
                    val resourcesResponse = buildJsonObject {
                        put("resources", json.parseToJsonElement("[]"))
                    }
                    sendResponse(session, requestId, resourcesResponse)
                }
                else -> {
                    sendErrorResponse(session, requestId, "Unsupported method: $method")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling MCP request: $method", e)
            sendErrorResponse(session, requestId, "Error: ${e.message}")
        }
    }

    /**
     * 도구 호출 처리
     */
    private suspend fun handleToolCall(
        session: WebSocketSession,
        requestId: String,
        toolName: String,
        args: JsonObject
    ) {
        Log.d(TAG, "Handling tool call: $toolName")
        
        try {
            val result = when (toolName) {
                "screenCapture" -> {
                    val imageData = screenCaptureTool.captureScreen()
                    buildJsonObject {
                        put("content", json.parseToJsonElement("[
                            {
                                \"type\": \"image\",
                                \"data\": \"$imageData\"
                            },
                            {
                                \"type\": \"text\",
                                \"text\": \"화면 캡처 완료\"
                            }
                        ]"))
                    }
                }
                "accessibility" -> {
                    val nodeInfo = accessibilityTool.getAccessibilityInfo()
                    buildJsonObject {
                        put("content", json.parseToJsonElement("[
                            {
                                \"type\": \"text\",
                                \"text\": \"$nodeInfo\"
                            }
                        ]"))
                    }
                }
                "deviceInfo" -> {
                    val deviceInfo = deviceContextProvider.getDeviceInfo()
                    buildJsonObject {
                        put("content", json.parseToJsonElement("[
                            {
                                \"type\": \"text\",
                                \"text\": \"$deviceInfo\"
                            }
                        ]"))
                    }
                }
                "appContext" -> {
                    val appInfo = appContextProvider.getAppInfo()
                    buildJsonObject {
                        put("content", json.parseToJsonElement("[
                            {
                                \"type\": \"text\",
                                \"text\": \"$appInfo\"
                            }
                        ]"))
                    }
                }
                else -> {
                    sendErrorResponse(session, requestId, "Unsupported tool: $toolName")
                    return
                }
            }
            
            sendResponse(session, requestId, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tool call: $toolName", e)
            sendErrorResponse(session, requestId, "Error: ${e.message}")
        }
    }

    /**
     * 응답 전송
     */
    private suspend fun sendResponse(
        session: WebSocketSession,
        requestId: String,
        data: JsonObject
    ) {
        val response = buildJsonObject {
            put("type", "mcp-response")
            put("requestId", requestId)
            put("data", data)
        }
        session.send(Frame.Text(response.toString()))
    }

    /**
     * 오류 응답 전송
     */
    private suspend fun sendErrorResponse(
        session: WebSocketSession,
        requestId: String,
        message: String
    ) {
        val response = buildJsonObject {
            put("type", "mcp-error")
            put("requestId", requestId)
            put("data", message)
        }
        session.send(Frame.Text(response.toString()))
    }

    /**
     * 핑 작업 시작
     * 주기적으로 핑을 보내 연결 유지
     */
    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = launch {
            while (isRunning.get()) {
                delay(PING_INTERVAL_MS)
                
                clients.forEach { (clientId, session) ->
                    try {
                        // 핑 메시지 전송
                        val pingMessage = buildJsonObject {
                            put("type", "ping")
                            put("timestamp", System.currentTimeMillis())
                        }
                        session.send(Frame.Text(pingMessage.toString()))
                        Log.d(TAG, "Ping sent to client: $clientId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending ping to client: $clientId", e)
                        // 오류 발생 시 클라이언트 제거
                        clients.remove(clientId)
                    }
                }
            }
        }
    }
}
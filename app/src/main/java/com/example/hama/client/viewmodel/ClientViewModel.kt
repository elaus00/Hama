package com.example.hama.client.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hama.client.repository.McpClientRepository
import com.example.hama.common.model.ConnectionState
import com.example.hama.common.model.LogMessage
import com.example.hama.common.model.ServerInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * MCP 클라이언트 뷰모델
 * MCP 프록시 서버와의 통신 및 UI 상태를 관리
 */
@HiltViewModel
class ClientViewModel @Inject constructor(
    private val mcpClientRepository: McpClientRepository
) : ViewModel() {
    private val TAG = "ClientViewModel"
    private val json = Json { ignoreUnknownKeys = true }

    // 서버 URL
    private val _serverUrl = MutableStateFlow("ws://10.0.2.2:3000")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // 연결 상태
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 서버 정보
    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    // 로그 메시지
    private val _logMessages = MutableStateFlow<List<LogMessage>>(emptyList())
    val logMessages: StateFlow<List<LogMessage>> = _logMessages.asStateFlow()

    // 사용 가능한 도구 목록
    private val _availableTools = MutableStateFlow<List<String>>(emptyList())
    val availableTools: StateFlow<List<String>> = _availableTools.asStateFlow()

    // 사용 가능한 서버 목록
    private val _availableServers = MutableStateFlow<List<String>>(emptyList())
    val availableServers: StateFlow<List<String>> = _availableServers.asStateFlow()

    // 현재 사용 중인 서버
    private val _currentServer = MutableStateFlow<String?>(null)
    val currentServer: StateFlow<String?> = _currentServer.asStateFlow()

    // 도구 호출 결과
    private val _toolResult = MutableStateFlow("")
    val toolResult: StateFlow<String> = _toolResult.asStateFlow()

    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 오류 메시지
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 마지막 로그 시간 형식
    private val logDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        observeRepositoryFlow()
    }

    /**
     * 레포지토리 Flow 관찰
     */
    private fun observeRepositoryFlow() {
        viewModelScope.launch {
            // 연결 상태 관찰
            mcpClientRepository.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        Log.d(TAG, "서버 연결 성공: ${state.serverName}")
                        _isConnected.value = true
                        _currentServer.value = state.serverName
                        _isLoading.value = false
                        loadInitialData()
                    }
                    is ConnectionState.Disconnected -> {
                        Log.d(TAG, "서버 연결 해제")
                        _isConnected.value = false
                        _isLoading.value = false
                    }
                    is ConnectionState.Error -> {
                        Log.e(TAG, "서버 연결 오류: ${state.message}")
                        _isConnected.value = false
                        _errorMessage.value = "연결 오류: ${state.message}"
                        _isLoading.value = false
                    }
                    else -> {
                        // Connecting 상태는 처리하지 않음
                    }
                }
            }
        }

        viewModelScope.launch {
            // 서버 정보 관찰
            mcpClientRepository.serverInfo.collect { info ->
                _serverInfo.value = info
                Log.d(TAG, "서버 정보 수신: $info")
            }
        }

        viewModelScope.launch {
            // 로그 메시지 관찰
            mcpClientRepository.logMessages.collect { logs ->
                _logMessages.value = logs
                if (logs.isNotEmpty()) {
                    val lastLog = logs.last()
                    val timestamp = logDateFormat.format(Date(lastLog.timestamp))
                    Log.d(TAG, "[$timestamp] 로그: ${lastLog.type}/${lastLog.level} - ${lastLog.message}")
                }
            }
        }

        viewModelScope.launch {
            // 이벤트 관찰
            mcpClientRepository.events.collect { event ->
                when (event) {
                    is McpClientRepository.McpEvent.ResourcesChanged -> {
                        Log.d(TAG, "리소스 변경 이벤트: ${event.resources}")
                    }
                    is McpClientRepository.McpEvent.ServerChanged -> {
                        Log.d(TAG, "서버 변경 이벤트: ${event.serverName}")
                        _currentServer.value = event.serverName
                        loadToolsList()
                    }
                }
            }
        }
    }

    /**
     * 서버 URL 설정
     * @param url MCP 프록시 서버 URL
     */
    fun setServerUrl(url: String) {
        _serverUrl.value = url
    }

    /**
     * MCP 프록시 서버에 연결
     * WebSocket 연결을 수립하고 상태 변화를 관찰
     */
    fun connect() {
        _isLoading.value = true

        val url = _serverUrl.value
        if (url.isEmpty()) {
            _errorMessage.value = "서버 URL이 비어있습니다"
            _isLoading.value = false
            return
        }

        Log.d(TAG, "서버 연결 시도: $url")
        mcpClientRepository.connect(url)
    }

    /**
     * 연결 해제
     * 현재 WebSocket 연결을 종료
     */
    fun disconnect() {
        _isLoading.value = true
        Log.d(TAG, "서버 연결 해제 요청")
        mcpClientRepository.disconnect()
        _isLoading.value = false
    }

    /**
     * 초기 데이터 로드
     * 연결 성공 후 초기 데이터(도구 목록, 서버 목록)를 로드
     */
    private fun loadInitialData() {
        Log.d(TAG, "초기 데이터 로드 시작")
        loadToolsList()
        loadServersList()
    }

    /**
     * 도구 목록 로드
     * 현재 연결된 MCP 서버에서 사용 가능한 도구 목록을 가져옴
     */
    private fun loadToolsList() {
        _isLoading.value = true
        Log.d(TAG, "도구 목록 로드 요청")

        mcpClientRepository.listTools { result ->
            result.fold(
                onSuccess = { response ->
                    try {
                        val tools = response["tools"]?.jsonArray
                        val toolNames = mutableListOf<String>()

                        tools?.forEach { jsonElement ->
                            val tool = jsonElement.jsonObject
                            val name = tool["name"]?.jsonPrimitive?.content
                            if (name != null) {
                                toolNames.add(name)
                            }
                        }

                        _availableTools.value = toolNames
                        Log.d(TAG, "도구 목록 로드 성공: ${toolNames.size}개")
                        _isLoading.value = false
                    } catch (e: Exception) {
                        Log.e(TAG, "도구 목록 파싱 오류", e)
                        _errorMessage.value = "도구 목록 파싱 오류: ${e.message}"
                        _isLoading.value = false
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "도구 목록 조회 실패", error)
                    _errorMessage.value = "도구 목록 조회 실패: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * 서버 목록 로드
     * 사용 가능한 MCP 서버 목록을 가져옴
     */
    private fun loadServersList() {
        _isLoading.value = true
        Log.d(TAG, "서버 목록 로드 요청")

        mcpClientRepository.listServers { result ->
            result.fold(
                onSuccess = { response ->
                    try {
                        val availableServers = response["availableServers"]?.jsonArray
                        val serverNames = mutableListOf<String>()

                        availableServers?.forEach { jsonElement ->
                            val serverName = jsonElement.jsonPrimitive.content
                            serverNames.add(serverName)
                        }

                        _availableServers.value = serverNames
                        Log.d(TAG, "서버 목록 로드 성공: ${serverNames.joinToString()}")

                        response["currentServer"]?.jsonPrimitive?.content?.let { current ->
                            _currentServer.value = current
                            Log.d(TAG, "현재 서버: $current")
                        }

                        _isLoading.value = false
                    } catch (e: Exception) {
                        Log.e(TAG, "서버 목록 파싱 오류", e)
                        _errorMessage.value = "서버 목록 파싱 오류: ${e.message}"
                        _isLoading.value = false
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "서버 목록 조회 실패", error)
                    _errorMessage.value = "서버 목록 조회 실패: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * 서버 전환
     * 다른 MCP 서버로 전환
     * @param serverName 전환할 서버 이름
     */
    fun switchServer(serverName: String) {
        _isLoading.value = true
        Log.d(TAG, "서버 전환 요청: $serverName")

        mcpClientRepository.switchServer(serverName) { result ->
            result.fold(
                onSuccess = { response ->
                    Log.d(TAG, "서버 전환 성공: $serverName")

                    // 서버 전환 후 재연결 필요
                    _isLoading.value = false
                    disconnect()

                    // 잠시 대기 후 재연결
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(1000)
                        connect()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "서버 전환 실패", error)
                    _errorMessage.value = "서버 전환 실패: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * 도구 호출
     * MCP 서버의 특정 도구를 인자와 함께 호출
     * @param toolName 호출할 도구 이름
     * @param args 도구 호출 인자 (JSON 문자열)
     */
    fun callTool(toolName: String, args: String) {
        _isLoading.value = true
        _toolResult.value = ""
        Log.d(TAG, "도구 호출 요청: $toolName, 인자: $args")

        try {
            val argsObject = Json.parseToJsonElement(args).jsonObject

            mcpClientRepository.callTool(toolName, argsObject) { result ->
                result.fold(
                    onSuccess = { response ->
                        try {
                            val content = response["content"]?.jsonArray
                            val resultBuilder = StringBuilder()

                            content?.forEach { jsonElement ->
                                val item = jsonElement.jsonObject
                                if (item["type"]?.jsonPrimitive?.content == "text") {
                                    resultBuilder.append(item["text"]?.jsonPrimitive?.content)
                                    resultBuilder.append("\n")
                                }
                            }

                            val resultText = resultBuilder.toString().trim()
                            _toolResult.value = resultText
                            Log.d(TAG, "도구 호출 성공, 결과: ${resultText.take(100)}${if(resultText.length > 100) "..." else ""}")
                            _isLoading.value = false
                        } catch (e: Exception) {
                            Log.e(TAG, "도구 결과 파싱 오류", e)
                            _errorMessage.value = "도구 결과 파싱 오류: ${e.message}"
                            _isLoading.value = false
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "도구 호출 실패", error)
                        _errorMessage.value = "도구 호출 실패: ${error.message}"
                        _isLoading.value = false
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "잘못된 인자 형식", e)
            _errorMessage.value = "잘못된 인자 형식: ${e.message}"
            _isLoading.value = false
        }
    }

    /**
     * 도구 호출 결과 초기화
     */
    fun clearToolResult() {
        _toolResult.value = ""
    }

    /**
     * 오류 메시지 초기화
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 로그 메시지 지우기
     */
    fun clearLogs() {
        _logMessages.value = emptyList()
    }

    /**
     * 뷰모델 해제 시 리소스 정리
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "뷰모델 해제")
        disconnect()
    }
}
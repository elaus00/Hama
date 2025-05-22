package com.example.hama.client.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.hama.client.viewmodel.ClientViewModel
import com.example.hama.common.model.LogMessage
import com.example.hama.ui.theme.LogError
import com.example.hama.ui.theme.LogInfo
import com.example.hama.ui.theme.LogStdErr
import com.example.hama.ui.theme.LogWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(viewModel: ClientViewModel) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()
    val availableTools by viewModel.availableTools.collectAsState()
    val availableServers by viewModel.availableServers.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val toolResult by viewModel.toolResult.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var serverUrlInput by remember { mutableStateOf(serverUrl) }
    var selectedTool by remember { mutableStateOf<String?>(null) }
    var selectedServer by remember { mutableStateOf<String?>(null) }
    var showToolArgsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val logListState = rememberLazyListState()

    // 오류 메시지 표시
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 로그 목록이 업데이트될 때마다 스크롤을 맨 아래로 이동
    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            logListState.animateScrollToItem(logMessages.size - 1)
        }
    }

    // 현재 서버가 변경될 때 선택된 서버를 업데이트
    LaunchedEffect(currentServer) {
        currentServer?.let {
            selectedServer = it
        }
    }

    // 도구 목록이 로드될 때 첫 번째 도구를 선택
    LaunchedEffect(availableTools) {
        if (availableTools.isNotEmpty() && selectedTool == null) {
            selectedTool = availableTools.first()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 서버 URL 및 연결 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("서버 URL") },
                        enabled = !isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Button(
                        onClick = {
                            if (isConnected) {
                                viewModel.disconnect()
                            } else {
                                viewModel.setServerUrl(serverUrlInput)
                                viewModel.connect()
                            }
                        }
                    ) {
                        Text(if (isConnected) "연결 해제" else "연결")
                    }
                }

                // 연결된 경우에만 표시
                if (isConnected) {
                    // 현재 서버 및 서버 선택 드롭다운
                    Text(
                        text = "현재 서버: $currentServer",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var serverExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = serverExpanded,
                            onExpandedChange = { serverExpanded = !serverExpanded },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedServer ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = serverExpanded,
                                onDismissRequest = { serverExpanded = false }
                            ) {
                                availableServers.forEach { server ->
                                    DropdownMenuItem(
                                        text = { Text(server) },
                                        onClick = {
                                            selectedServer = server
                                            serverExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                selectedServer?.let {
                                    if (it != currentServer) {
                                        viewModel.switchServer(it)
                                    }
                                }
                            },
                            enabled = selectedServer != null && selectedServer != currentServer
                        ) {
                            Text("서버 변경")
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 도구 목록 및 도구 호출 버튼
                    Text(
                        text = "사용 가능한 도구",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var toolExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = toolExpanded,
                            onExpandedChange = { toolExpanded = !toolExpanded },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedTool ?: "",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toolExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = toolExpanded,
                                onDismissRequest = { toolExpanded = false }
                            ) {
                                availableTools.forEach { tool ->
                                    DropdownMenuItem(
                                        text = { Text(tool) },
                                        onClick = {
                                            selectedTool = tool
                                            toolExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                selectedTool?.let {
                                    showToolArgsDialog = true
                                }
                            },
                            enabled = selectedTool != null
                        ) {
                            Text("도구 호출")
                        }
                    }

                    // 도구 호출 결과
                    if (toolResult.isNotEmpty()) {
                        Text(
                            text = "도구 호출 결과:",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = toolResult,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 로그 목록
                    Text(
                        text = "로그",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(4.dp)
                            ),
                        state = logListState
                    ) {
                        items(logMessages) { log ->
                            LogItem(log = log)
                        }
                    }
                }

                // 연결되지 않은 경우 안내 메시지
                if (!isConnected && !isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("서버에 연결해주세요")
                    }
                }
            }

            // 로딩 인디케이터
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 도구 인자 입력 다이얼로그
            if (showToolArgsDialog) {
                ToolArgsDialog(
                    toolName = selectedTool ?: "",
                    onDismiss = { showToolArgsDialog = false },
                    onCall = { args ->
                        viewModel.callTool(selectedTool!!, args)
                        showToolArgsDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun LogItem(log: LogMessage) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = dateFormat.format(Date(log.timestamp))

    val textColor = when (log.type) {
        "error" -> LogError
        "stderr" -> LogStdErr
        else -> {
            when (log.level) {
                "error" -> LogError
                "warning" -> LogWarning
                else -> LogInfo
            }
        }
    }

    val prefix = when (log.type) {
        "log" -> "[${log.level?.uppercase() ?: "INFO"}] "
        "error" -> "[ERROR] "
        "stderr" -> "[STDERR] "
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = prefix + log.message,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolArgsDialog(
    toolName: String,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    var argsText by remember { mutableStateOf("{\"name\": \"Android User\"}") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "$toolName 도구 호출",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text("도구 인자 (JSON 형식)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }

                    Button(
                        onClick = { onCall(argsText) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("호출")
                    }
                }
            }
        }
    }
}
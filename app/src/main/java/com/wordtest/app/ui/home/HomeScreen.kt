package com.wordtest.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.BuildConfig
import com.wordtest.app.data.UpdateChecker
import com.wordtest.app.data.db.WordSessionEntity
import com.wordtest.app.data.repository.WordRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: WordRepository,
    updateChecker: UpdateChecker,
    onNewSession: () -> Unit,
    onStartTest: (Long, Boolean) -> Unit,
    onEditWords: (Long) -> Unit,
    onApiKeySetting: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, updateChecker) as T
        }
    })
    val sessions by vm.sessions.collectAsState()
    val updateInfo by vm.updateInfo.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val isCheckingUpdate by vm.isCheckingUpdate.collectAsState()
    val sessionCounts by vm.sessionCounts.collectAsState()
    var deleteTarget by remember { mutableStateOf<WordSessionEntity?>(null) }
    var testTarget by remember { mutableStateOf<WordSessionEntity?>(null) }
    var showUpdateResult by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("엄마아빠 찾지마라", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            vm.checkForUpdate()
                            showUpdateResult = true
                        },
                        enabled = !isCheckingUpdate
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "업데이트 확인")
                        }
                    }
                    IconButton(onClick = onApiKeySetting) {
                        Icon(Icons.Default.Key, contentDescription = "API 키 설정")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewSession) {
                Icon(Icons.Default.Add, contentDescription = "새 단어 추가")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("단어 목록이 없습니다", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("+ 버튼으로 이미지에서 단어를 추가하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions) { session ->
                        SessionCard(
                            session = session,
                            onStartTest = {
                                testTarget = session
                                vm.loadSessionCounts(session.id)
                            },
                            onEdit = { onEditWords(session.id) },
                            onDelete = { deleteTarget = session }
                        )
                    }
                }
            }
        }
    }

    // 테스트 모드 선택
    testTarget?.let { session ->
        ModeSelectDialog(
            counts = sessionCounts,
            onStart = { silent ->
                onStartTest(session.id, silent)
                testTarget = null
                vm.clearSessionCounts()
            },
            onDismiss = {
                testTarget = null
                vm.clearSessionCounts()
            }
        )
    }

    // 업데이트 결과 다이얼로그
    if (showUpdateResult && !isCheckingUpdate) {
        val info = updateInfo
        if (info != null) {
            AlertDialog(
                onDismissRequest = { showUpdateResult = false; vm.dismissUpdate() },
                title = { Text("새 버전 사용 가능") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("최신 버전: ${info.versionName}")
                        Text("현재 버전: v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgress!! / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("다운로드 중... ${downloadProgress}%",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    if (downloadProgress == null) {
                        TextButton(onClick = { vm.startUpdate(info.downloadUrl) }) {
                            Text("업데이트")
                        }
                    }
                },
                dismissButton = {
                    if (downloadProgress == null) {
                        TextButton(onClick = { showUpdateResult = false; vm.dismissUpdate() }) {
                            Text("나중에")
                        }
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showUpdateResult = false },
                title = { Text("업데이트 확인") },
                text = { Text("현재 최신 버전입니다. (v${BuildConfig.VERSION_NAME})") },
                confirmButton = {
                    TextButton(onClick = { showUpdateResult = false }) { Text("확인") }
                }
            )
        }
    }

    // 삭제 확인
    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("삭제 확인") },
            text = { Text("'${session.name}' 단어 목록을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSession(session.id); deleteTarget = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun ModeSelectDialog(
    counts: Pair<Int, Int>?,  // (enabled, total)
    onStart: (silent: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("테스트 모드 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                counts?.let { (enabled, total) ->
                    Text(
                        "선택된 단어: $enabled / ${total}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                }
                OutlinedButton(onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎤 말하기 모드", fontWeight = FontWeight.Bold)
                        Text("앱이 한글 뜻을 말하면 영어로 말하기",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⌨️ 타이핑 모드", fontWeight = FontWeight.Bold)
                        Text("한글 뜻을 보고 영어 단어 타이핑",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun SessionCard(
    session: WordSessionEntity,
    onStartTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(dateFormat.format(Date(session.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("편집")
                }
                Button(onClick = onStartTest, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("테스트")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

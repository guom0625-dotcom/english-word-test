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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.db.WordSessionEntity
import com.wordtest.app.data.repository.WordRepository
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: WordRepository,
    onNewSession: () -> Unit,
    onStartTest: (Long, Boolean, Boolean, Boolean) -> Unit,
    onEditWords: (Long) -> Unit,
    onApiKeySetting: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
    })
    val sessions by vm.sessions.collectAsState()
    var deleteTarget by remember { mutableStateOf<WordSessionEntity?>(null) }
    var testTarget by remember { mutableStateOf<WordSessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("영단어 테스트", fontWeight = FontWeight.Bold) },
                actions = {
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
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    SessionCard(
                        session = session,
                        onStartTest = { testTarget = session },
                        onEdit = { onEditWords(session.id) },
                        onDelete = { deleteTarget = session }
                    )
                }
            }
        }
    }

    // 테스트 모드 선택
    testTarget?.let { session ->
        ModeSelectDialog(
            onStart = { silent, synonyms, antonyms ->
                onStartTest(session.id, silent, synonyms, antonyms)
                testTarget = null
            },
            onDismiss = { testTarget = null }
        )
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
    onStart: (silent: Boolean, synonyms: Boolean, antonyms: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var includeSynonyms by remember { mutableStateOf(false) }
    var includeAntonyms by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("테스트 모드 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onStart(false, includeSynonyms, includeAntonyms) },
                    modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎤 유음 모드", fontWeight = FontWeight.Bold)
                        Text("앱이 한글 뜻을 말하면 영어로 말하기",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { onStart(true, includeSynonyms, includeAntonyms) },
                    modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⌨️ 무음 모드", fontWeight = FontWeight.Bold)
                        Text("한글 뜻을 보고 영어 단어 타이핑",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("유의어 포함", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includeSynonyms, onCheckedChange = { includeSynonyms = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("반대어 포함", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includeAntonyms, onCheckedChange = { includeAntonyms = it })
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

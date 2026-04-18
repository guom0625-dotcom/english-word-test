package com.wordtest.app.ui.wordlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    sessionId: Long,
    repository: WordRepository,
    onStartTest: () -> Unit,
    onBack: () -> Unit
) {
    val vm: WordListViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WordListViewModel(sessionId, repository) as T
        }
    })
    val words by vm.words.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단어 목록 (${words.size}개)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "단어 추가")
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onStartTest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = words.isNotEmpty()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("테스트 시작")
                }
            }
        }
    ) { padding ->
        if (words.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("단어가 없습니다. + 버튼으로 추가하세요.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words, key = { it.id }) { word ->
                    WordItem(
                        word = word,
                        onUpdate = { vm.updateWord(it) },
                        onDelete = { vm.deleteWord(it) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddWordDialog(
            onAdd = { eng, kor ->
                vm.addWord(eng, kor)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun WordItem(
    word: WordEntity,
    onUpdate: (WordEntity) -> Unit,
    onDelete: (WordEntity) -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var english by remember(word) { mutableStateOf(word.english) }
    var korean by remember(word) { mutableStateOf(word.korean) }

    Card(modifier = Modifier.fillMaxWidth()) {
        if (editMode) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = english,
                    onValueChange = { english = it },
                    label = { Text("영단어") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = korean,
                    onValueChange = { korean = it },
                    label = { Text("한글 뜻") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { editMode = false; english = word.english; korean = word.korean }) {
                        Text("취소")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onUpdate(word.copy(english = english, korean = korean))
                        editMode = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("저장")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(word.english, style = MaterialTheme.typography.titleSmall)
                    Text(word.korean, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editMode = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "편집")
                }
                IconButton(onClick = { onDelete(word) }) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddWordDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var english by remember { mutableStateOf("") }
    var korean by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("단어 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = english, onValueChange = { english = it },
                    label = { Text("영단어") }, singleLine = true)
                OutlinedTextField(value = korean, onValueChange = { korean = it },
                    label = { Text("한글 뜻") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (english.isNotBlank() && korean.isNotBlank()) onAdd(english.trim(), korean.trim()) },
                enabled = english.isNotBlank() && korean.isNotBlank()
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

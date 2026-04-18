package com.wordtest.app.ui.wordlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    sessionId: Long,
    repository: WordRepository,
    onStartTest: (Boolean, Boolean, Boolean) -> Unit,
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
    var showModeDialog by remember { mutableStateOf(false) }
    var includeSynonyms by remember { mutableStateOf(false) }
    var includeAntonyms by remember { mutableStateOf(false) }

    val enabledCount = words.count { it.isEnabled }
    val totalCount = words.size
    val selectAllState = when {
        enabledCount == totalCount && totalCount > 0 -> ToggleableState.On
        enabledCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단어 목록 (선택 $enabledCount / ${totalCount}개)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Check, contentDescription = "완료")
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
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 유의어/반대어 옵션
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("유의어 포함", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = includeSynonyms, onCheckedChange = { includeSynonyms = it })
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("반대어 포함", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = includeAntonyms, onCheckedChange = { includeAntonyms = it })
                        }
                    }
                }
                Button(
                    onClick = { showModeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledCount > 0
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(
                            state = selectAllState,
                            onClick = {
                                val enable = selectAllState != ToggleableState.On
                                vm.toggleAll(enable)
                            }
                        )
                        Text(
                            when (selectAllState) {
                                ToggleableState.On -> "전체 선택 해제"
                                else -> "전체 선택"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider()
                }
                items(words, key = { it.id }) { word ->
                    WordItem(
                        word = word,
                        onUpdate = { vm.updateWord(it) },
                        onDelete = { vm.deleteWord(it) },
                        onToggleEnabled = { vm.toggleEnabled(word) }
                    )
                }
            }
        }
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("테스트 모드 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onStartTest(false, includeSynonyms, includeAntonyms); showModeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎤 유음 모드", fontWeight = FontWeight.Bold)
                            Text("앱이 한글 뜻을 말하면 영어로 말하기",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { onStartTest(true, includeSynonyms, includeAntonyms); showModeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⌨️ 무음 모드", fontWeight = FontWeight.Bold)
                            Text("한글 뜻을 보고 영어 단어 타이핑",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showModeDialog = false }) { Text("취소") } }
        )
    }

    if (showAddDialog) {
        AddWordDialog(
            onAdd = { eng, kor, pos -> vm.addWord(eng, kor, pos); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun WordItem(
    word: WordEntity,
    onUpdate: (WordEntity) -> Unit,
    onDelete: (WordEntity) -> Unit,
    onToggleEnabled: () -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var english by remember(word) { mutableStateOf(word.english) }
    var korean by remember(word) { mutableStateOf(word.korean) }
    var partOfSpeech by remember(word) { mutableStateOf(word.partOfSpeech) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (word.isEnabled) MaterialTheme.colorScheme.surface
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        if (editMode) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = partOfSpeech, onValueChange = { partOfSpeech = it },
                        label = { Text("품사") }, modifier = Modifier.width(80.dp), singleLine = true)
                    OutlinedTextField(value = english, onValueChange = { english = it },
                        label = { Text("영단어") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = korean, onValueChange = { korean = it },
                    label = { Text("한글 뜻") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        editMode = false; english = word.english; korean = word.korean; partOfSpeech = word.partOfSpeech
                    }) { Text("취소") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onUpdate(word.copy(english = english, korean = korean, partOfSpeech = partOfSpeech))
                        editMode = false
                    }) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("저장")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = word.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (word.isSynonym || word.isAntonym) {
                        Surface(
                            color = if (word.isAntonym) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                if (word.isAntonym) "반대어" else "유의어",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (word.isAntonym) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(word.english, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                    Text(
                        buildAnnotatedString {
                            if (word.partOfSpeech.isNotBlank()) {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary,
                                    fontStyle = FontStyle.Italic, fontSize = 12.sp)) {
                                    append(word.partOfSpeech); append(" ")
                                }
                            }
                            append(word.korean)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
                Row {
                    IconButton(onClick = { editMode = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDelete(word) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddWordDialog(onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var english by remember { mutableStateOf("") }
    var korean by remember { mutableStateOf("") }
    var partOfSpeech by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("단어 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = partOfSpeech, onValueChange = { partOfSpeech = it },
                        label = { Text("품사") }, modifier = Modifier.width(80.dp), singleLine = true)
                    OutlinedTextField(value = english, onValueChange = { english = it },
                        label = { Text("영단어") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(value = korean, onValueChange = { korean = it },
                    label = { Text("한글 뜻") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (english.isNotBlank() && korean.isNotBlank()) onAdd(english.trim(), korean.trim(), partOfSpeech.trim()) },
                enabled = english.isNotBlank() && korean.isNotBlank()
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

package com.wordtest.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(sessionId: Long, wrongIds: List<Int>, repository: WordRepository) : ViewModel() {
    private val _wrongWords = MutableStateFlow<List<WordEntity>>(emptyList())
    val wrongWords = _wrongWords.asStateFlow()

    init {
        viewModelScope.launch {
            if (wrongIds.isEmpty()) return@launch
            val all = repository.getWordsBySessionOnce(sessionId)
            _wrongWords.value = all.filter { it.id in wrongIds }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    score: Int,
    total: Int,
    sessionId: Long,
    wrongIds: List<Int>,
    repository: WordRepository,
    onHome: () -> Unit,
    onRetry: (silent: Boolean, autoMic: Boolean, ordered: Boolean, mcOnly: Boolean, reverseMode: Boolean) -> Unit
) {
    val percentage = if (total > 0) (score * 100 / total) else 0
    val grade = when {
        percentage >= 90 -> "🏆 완벽해요!"
        percentage >= 70 -> "👍 잘했어요!"
        percentage >= 50 -> "💪 조금 더 연습해요"
        else -> "📚 다시 공부해봐요"
    }
    val wrongCount = total - score

    val vm: ResultViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ResultViewModel(sessionId, wrongIds, repository) as T
        }
    })
    val wrongWords by vm.wrongWords.collectAsState()
    var showModeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("테스트 결과") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (percentage >= 70)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(grade, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text("$score / $total", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text("정답률 $percentage%", style = MaterialTheme.typography.titleMedium)
                        if (wrongCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("틀린 단어 ${wrongCount}개",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("홈으로")
                    }
                    Button(onClick = { showModeDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("다시 테스트")
                    }
                }
            }

            if (wrongWords.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("틀린 단어 복습", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth())
                }
                items(wrongWords) { word ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(word.english, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (word.partOfSpeech.isNotBlank()) {
                                Text(word.partOfSpeech,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(word.korean,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (wrongCount == 0) {
                item {
                    Text("모든 단어를 맞혔습니다! 🎉",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showModeDialog) {
        var autoMic by remember { mutableStateOf(false) }
        var ordered by remember { mutableStateOf(false) }
        var reverseMode by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("테스트 모드 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("순서대로", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = ordered, onCheckedChange = { ordered = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("자동 마이크 (말하기 전용)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = autoMic, onCheckedChange = { autoMic = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("영어→한글 모드", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = reverseMode, onCheckedChange = { reverseMode = it })
                    }
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { onRetry(false, autoMic, ordered, false, reverseMode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎤 말하기 모드", fontWeight = FontWeight.Bold)
                            Text(if (reverseMode) "영어 단어를 듣고 한글 뜻 말하기" else "앱이 한글 뜻을 말하면 영어로 말하기",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { onRetry(true, false, ordered, false, reverseMode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⌨️ 타이핑 모드", fontWeight = FontWeight.Bold)
                            Text(if (reverseMode) "영어 단어를 보고 한글 뜻 타이핑" else "한글 뜻을 보고 영어 단어 타이핑",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { onRetry(false, false, ordered, true, reverseMode) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📝 객관식 모드", fontWeight = FontWeight.Bold)
                            Text("4개 보기 중 정답 선택, 재시도 없음",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showModeDialog = false }) { Text("취소") } }
        )
    }
}

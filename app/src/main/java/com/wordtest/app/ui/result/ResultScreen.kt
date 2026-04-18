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

class ResultViewModel(sessionId: Long, repository: WordRepository) : ViewModel() {
    private val _wrongWords = MutableStateFlow<List<WordEntity>>(emptyList())
    val wrongWords = _wrongWords.asStateFlow()

    init {
        viewModelScope.launch {
            _wrongWords.value = repository.getWordsBySessionOnce(sessionId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    score: Int,
    total: Int,
    sessionId: Long,
    repository: WordRepository,
    onHome: () -> Unit,
    onRetry: (Boolean) -> Unit
) {
    val percentage = if (total > 0) (score * 100 / total) else 0
    val grade = when {
        percentage >= 90 -> "🏆 완벽해요!"
        percentage >= 70 -> "👍 잘했어요!"
        percentage >= 50 -> "💪 조금 더 연습해요"
        else -> "📚 다시 공부해봐요"
    }

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
                    }
                }
            }

            if (total - score > 0) {
                item {
                    Text(
                        "틀린 단어 (${total - score}개)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items((0 until (total - score)).toList()) { index ->
                    // Placeholder - actual wrong words tracked in TestViewModel
                    // In a full implementation, pass wrongWords through navigation
                }
            }

            item {
                Text(
                    if (total - score == 0) "모든 단어를 맞혔습니다! 🎉"
                    else "틀린 단어를 다시 복습해보세요.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Button(onClick = { onRetry(false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("다시 테스트")
                    }
                }
            }
        }
    }
}

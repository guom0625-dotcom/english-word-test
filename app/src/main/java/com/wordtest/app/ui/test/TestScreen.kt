package com.wordtest.app.ui.test

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository
import java.util.*

@Composable
fun TestScreen(
    sessionId: Long,
    repository: WordRepository,
    onFinished: (score: Int, total: Int) -> Unit
) {
    val context = LocalContext.current
    val vm: TestViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TestViewModel(sessionId, repository) as T
        }
    })
    val uiState by vm.uiState.collectAsState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                ttsReady = true
            }
        }
        onDispose { tts?.shutdown() }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull() ?: ""
            vm.onVoiceResult(spoken)
        }
    }

    fun speakKorean(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_id")
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "영어로 말해주세요")
        }
        speechLauncher.launch(intent)
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TestUiState.Voice -> speakKorean(state.word.entity.korean)
            is TestUiState.MultipleChoice -> speakKorean(state.word.entity.korean)
            is TestUiState.Finished -> onFinished(state.score, state.total)
            else -> {}
        }
    }

    when (val state = uiState) {
        is TestUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is TestUiState.Voice -> {
            VoiceTestScreen(
                korean = state.word.entity.korean,
                wrongCount = state.wrongCount,
                onMicClick = { startListening() },
                onSpeak = { speakKorean(state.word.entity.korean) }
            )
        }
        is TestUiState.MultipleChoice -> {
            MultipleChoiceScreen(
                korean = state.word.entity.korean,
                choices = state.choices,
                onSelected = { vm.onMultipleChoiceSelected(it) },
                onSpeak = { speakKorean(state.word.entity.korean) }
            )
        }
        is TestUiState.Finished -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun VoiceTestScreen(
    korean: String,
    wrongCount: Int,
    onMicClick: () -> Unit,
    onSpeak: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (wrongCount > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "틀린 횟수: $wrongCount / 2",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("이 단어의 영어는?", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text(korean, fontSize = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))

        TextButton(onClick = onSpeak) {
            Text("다시 듣기")
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onMicClick,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Mic, contentDescription = "말하기", modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("마이크를 눌러 영어로 말하세요", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MultipleChoiceScreen(
    korean: String,
    choices: List<WordEntity>,
    onSelected: (WordEntity) -> Unit,
    onSpeak: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                "2번 틀렸습니다. 보기에서 고르세요",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("이 단어의 영어는?", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(korean, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSpeak) { Text("다시 듣기") }
        Spacer(Modifier.height(32.dp))

        choices.forEach { choice ->
            OutlinedButton(
                onClick = { onSelected(choice) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(choice.english, fontSize = 18.sp)
            }
        }
    }
}

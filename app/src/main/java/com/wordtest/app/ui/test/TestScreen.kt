package com.wordtest.app.ui.test

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    silentMode: Boolean,
    initialAutoMic: Boolean = false,
    ordered: Boolean = false,
    multipleChoiceOnly: Boolean = false,
    reverseMode: Boolean = false,
    repository: WordRepository,
    onFinished: (score: Int, total: Int) -> Unit
) {
    val context = LocalContext.current
    val vm: TestViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TestViewModel(sessionId, repository, ordered, multipleChoiceOnly, reverseMode) as T
        }
    })
    val uiState by vm.uiState.collectAsState()

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var autoListen by remember { mutableStateOf(initialAutoMic) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            vm.onAnswerSubmitted(matches ?: listOf(""))
        } else {
            autoListen = false
        }
    }

    fun startListeningFn() {
        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (reverseMode) Locale.KOREAN.toString() else Locale.ENGLISH.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (reverseMode) "한글로 말해주세요" else "영어로 말해주세요")
        }
        speechLauncher.launch(intent)
    }

    fun speakQuestion(text: String) {
        val cleaned = if (reverseMode) text
            else text.replace(Regex("^[a-z]+\\./?[a-z]*\\.?\\s*"), "")
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "tts_auto")
    }

    DisposableEffect(Unit) {
        if (!silentMode) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = if (reverseMode) Locale.ENGLISH else Locale.KOREAN
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (autoListen && vm.uiState.value is TestUiState.Voice) {
                                mainHandler.post { startListeningFn() }
                            }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {}
                    })
                    ttsReady = true
                }
            }
        }
        onDispose { tts?.shutdown() }
    }

    fun questionText(entity: com.wordtest.app.data.db.WordEntity) =
        if (reverseMode) entity.english else entity.korean

    // TTS 준비 완료 시 현재 단어 읽기 (첫 단어 처리)
    LaunchedEffect(ttsReady) {
        if (!silentMode && ttsReady) {
            val state = uiState
            if (state is TestUiState.Voice) speakQuestion(questionText(state.word.entity))
        }
    }

    // 단어가 바뀔 때마다 읽기
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TestUiState.Voice -> if (!silentMode && ttsReady) speakQuestion(questionText(state.word.entity))
            is TestUiState.Finished -> onFinished(state.score, state.total)
            else -> Unit
        }
    }

    // 단어 바뀔 때 autoListen 복원 (Voice일 때만, initialAutoMic 기준)
    LaunchedEffect(uiState) {
        if (uiState is TestUiState.Voice) autoListen = initialAutoMic
    }

    when (val state = uiState) {
        is TestUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is TestUiState.Voice -> {
            val question = questionText(state.word.entity)
            if (silentMode) {
                SilentTestScreen(
                    question = question,
                    partOfSpeech = state.word.entity.partOfSpeech,
                    wrongCount = state.wrongCount,
                    reverseMode = reverseMode,
                    onSubmit = { vm.onAnswerSubmitted(listOf(it)) }
                )
            } else {
                VoiceTestScreen(
                    question = question,
                    partOfSpeech = state.word.entity.partOfSpeech,
                    wrongCount = state.wrongCount,
                    reverseMode = reverseMode,
                    waitingForMic = !autoListen,
                    onMicClick = { autoListen = true; startListeningFn() },
                    onSpeak = { speakQuestion(question) }
                )
            }
        }
        is TestUiState.MultipleChoice -> {
            val question = questionText(state.word.entity)
            MultipleChoiceScreen(
                question = question,
                partOfSpeech = state.word.entity.partOfSpeech,
                choices = state.choices,
                silentMode = silentMode,
                reverseMode = reverseMode,
                isFallback = state.word.wrongCount >= 2,
                onSelected = { vm.onMultipleChoiceSelected(it) },
                onSpeak = { speakQuestion(question) }
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
    question: String,
    partOfSpeech: String,
    wrongCount: Int,
    reverseMode: Boolean,
    waitingForMic: Boolean,
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
                Text("틀린 횟수: $wrongCount / 2",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(if (reverseMode) "이 단어의 한글 뜻은?" else "이 단어의 영어는?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        if (!reverseMode && partOfSpeech.isNotBlank()) {
            Text(partOfSpeech, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(question, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 42.sp)
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onSpeak) { Text("다시 듣기") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onMicClick, modifier = Modifier.size(80.dp),
            shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.Mic, contentDescription = "말하기", modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (waitingForMic) "마이크를 눌러 말하세요" else "듣는 중... (취소하면 수동 전환)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SilentTestScreen(
    question: String,
    partOfSpeech: String,
    wrongCount: Int,
    reverseMode: Boolean,
    onSubmit: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(question) {
        input = ""
        focusRequester.requestFocus()
    }

    fun submit() {
        if (input.isBlank()) return
        val answer = input.trim()
        input = ""
        onSubmit(answer)
    }

    // 키보드 위로 내용이 올라오도록 imePadding 사용
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 단어 표시 영역
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (wrongCount > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("틀린 횟수: $wrongCount / 2",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(if (reverseMode) "이 단어의 한글 뜻은?" else "이 단어의 영어는?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (!reverseMode && partOfSpeech.isNotBlank()) {
                Text(partOfSpeech, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(question, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, lineHeight = 42.sp)
        }

        // 입력 영역 (키보드 위에 고정)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (reverseMode) "한글 뜻 입력" else "영단어 입력") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = { submit() }, enabled = input.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = "제출")
                }
            }
        )
    }
}

@Composable
private fun MultipleChoiceScreen(
    question: String,
    partOfSpeech: String,
    choices: List<WordEntity>,
    silentMode: Boolean,
    reverseMode: Boolean,
    isFallback: Boolean = false,
    onSelected: (WordEntity) -> Unit,
    onSpeak: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isFallback) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("2번 틀렸습니다. 보기에서 고르세요",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(24.dp))
        }
        Text(if (reverseMode) "이 단어의 한글 뜻은?" else "이 단어의 영어는?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (!reverseMode && partOfSpeech.isNotBlank()) {
            Text(partOfSpeech, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(question, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 38.sp)
        if (!silentMode) {
            TextButton(onClick = onSpeak) { Text("다시 듣기") }
        }
        Spacer(Modifier.height(24.dp))
        choices.forEach { choice ->
            OutlinedButton(
                onClick = { onSelected(choice) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(if (reverseMode) choice.korean else choice.english, fontSize = 18.sp)
            }
        }
    }
}

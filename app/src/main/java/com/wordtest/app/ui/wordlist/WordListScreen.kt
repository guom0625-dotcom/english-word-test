package com.wordtest.app.ui.wordlist

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.data.api.GeminiService
import com.wordtest.app.data.db.WordEntity
import com.wordtest.app.data.repository.WordRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    sessionId: Long,
    repository: WordRepository,
    geminiService: GeminiService,
    onStartTest: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: WordListViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WordListViewModel(sessionId, repository, geminiService) as T
        }
    })
    val words by vm.words.collectAsState()
    val sessionName by vm.sessionName.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()
    val processProgress by vm.progress.collectAsState()
    val imageError by vm.imageError.collectAsState()

    var showAddChoice by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // 사진 추가용 상태
    var pendingImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var showImageConfirm by remember { mutableStateOf(false) }

    // 카메라 촬영용 상태
    var cameraCaptures by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraNextChoice by remember { mutableStateOf(false) }

    fun createCameraUri(): Uri {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = currentCameraUri
            if (uri != null) {
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }.getOrNull()
                if (bitmap != null) {
                    cameraCaptures = cameraCaptures + bitmap
                    showCameraNextChoice = true
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val bitmaps = uris.mapNotNull { uri ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
        }
        if (bitmaps.isNotEmpty()) {
            pendingImages = bitmaps
            showImageConfirm = true
        }
    }

    val synonymWords = words.filter { it.isSynonym }
    val antonymWords = words.filter { it.isAntonym }
    val includeSynonyms = synonymWords.isNotEmpty() && synonymWords.all { it.isEnabled }
    val includeAntonyms = antonymWords.isNotEmpty() && antonymWords.all { it.isEnabled }

    val inScopeWords = words.filter { word ->
        when {
            word.isSynonym -> includeSynonyms
            word.isAntonym -> includeAntonyms
            else -> true
        }
    }
    val enabledCount = words.count { it.isEnabled }
    val totalCount = words.size
    val selectAllState = when {
        inScopeWords.isNotEmpty() && inScopeWords.all { it.isEnabled } -> ToggleableState.On
        inScopeWords.none { it.isEnabled } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$sessionName (선택 $enabledCount / ${totalCount}개)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Check, contentDescription = "완료")
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "이름 변경")
                    }
                    IconButton(onClick = { showAddChoice = true }) {
                        Icon(Icons.Default.Add, contentDescription = "단어 추가")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("유의어 포함", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = includeSynonyms, onCheckedChange = { vm.setSynonymsEnabled(it) })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("반대어 포함", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = includeAntonyms, onCheckedChange = { vm.setAntonymsEnabled(it) })
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
                                vm.toggleAll(enable, includeSynonyms, includeAntonyms)
                            }
                        )
                        Text(
                            if (selectAllState == ToggleableState.On) "전체 선택 해제" else "전체 선택",
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

    // 처리 중 오버레이
    if (isProcessing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp).width(240.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("AI가 단어 인식 중...")
                    processProgress?.let { (current, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$current / $total 장", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { current.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } ?: CircularProgressIndicator()
                }
            }
        }
    }

    // 단어 추가 방식 선택
    if (showAddChoice) {
        AlertDialog(
            onDismissRequest = { showAddChoice = false },
            title = { Text("단어 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showAddChoice = false; showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("직접 입력")
                    }
                    OutlinedButton(
                        onClick = { showAddChoice = false; imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("갤러리에서 추가")
                    }
                    OutlinedButton(
                        onClick = {
                            showAddChoice = false
                            cameraCaptures = emptyList()
                            val uri = createCameraUri()
                            currentCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("카메라로 찍기")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddChoice = false }) { Text("취소") } }
        )
    }

    // 카메라 촬영 후 한 장 더 / 완료
    if (showCameraNextChoice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("${cameraCaptures.size}장 촬영됨") },
            text = { Text("한 장 더 찍을까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraNextChoice = false
                    val uri = createCameraUri()
                    currentCameraUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("한 장 더") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCameraNextChoice = false
                    pendingImages = cameraCaptures
                    cameraCaptures = emptyList()
                    showImageConfirm = true
                }) { Text("완료 (${cameraCaptures.size}장 처리)") }
            }
        )
    }

    // 사진 선택 후 확인
    if (showImageConfirm && pendingImages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showImageConfirm = false; pendingImages = emptyList() },
            title = { Text("사진 확인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("선택된 이미지 ${pendingImages.size}장에서 단어를 추출합니다.")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(pendingImages) { _, bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addWordsFromImages(pendingImages)
                    pendingImages = emptyList()
                    showImageConfirm = false
                }) { Text("추출 시작") }
            },
            dismissButton = {
                TextButton(onClick = { showImageConfirm = false; pendingImages = emptyList() }) { Text("취소") }
            }
        )
    }

    // 이름 변경 다이얼로그
    if (showRenameDialog) {
        var nameInput by remember(sessionName) { mutableStateOf(sessionName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("이름 변경") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("단어 목록 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank()) vm.renameSession(nameInput.trim())
                        showRenameDialog = false
                    },
                    enabled = nameInput.isNotBlank()
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("취소") } }
        )
    }

    // 테스트 모드 선택
    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("테스트 모드 선택") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "선택된 단어: $enabledCount / ${totalCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { onStartTest(false); showModeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎤 말하기 모드", fontWeight = FontWeight.Bold)
                            Text("앱이 한글 뜻을 말하면 영어로 말하기",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(
                        onClick = { onStartTest(true); showModeDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⌨️ 타이핑 모드", fontWeight = FontWeight.Bold)
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

    // 이미지 처리 오류
    imageError?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearImageError() },
            title = { Text("오류") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearImageError() }) { Text("확인") } }
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
                Checkbox(checked = word.isEnabled, onCheckedChange = { onToggleEnabled() })
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

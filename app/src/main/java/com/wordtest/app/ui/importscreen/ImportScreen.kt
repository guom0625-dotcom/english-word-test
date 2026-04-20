package com.wordtest.app.ui.importscreen

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordtest.app.WordTestApplication
import com.wordtest.app.data.api.GeminiService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    geminiService: GeminiService,
    onWordsExtracted: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as WordTestApplication
    val vm: ImportViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ImportViewModel(geminiService, app.repository) as T
        }
    })

    val uiState by vm.uiState.collectAsState()
    val progress by vm.progress.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val images by vm.selectedImages.collectAsState()
    var sessionName by remember {
        mutableStateOf("단어목록_${SimpleDateFormat("MMdd_HHmm", Locale.getDefault()).format(Date())}")
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            vm.addImage(bitmap)
        }
    }

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
            val uri = currentCameraUri ?: return@rememberLauncherForActivityResult
            val bitmap = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull() ?: return@rememberLauncherForActivityResult
            vm.addImage(bitmap)
            showCameraNextChoice = true
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ImportUiState.Done) {
            onWordsExtracted((uiState as ImportUiState.Done).sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이미지에서 단어 추출") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("단어 목록 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("갤러리")
                }
                OutlinedButton(
                    onClick = {
                        val uri = createCameraUri()
                        currentCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("카메라")
                }
            }

            if (images.isNotEmpty()) {
                Text("선택된 이미지 ${images.size}장", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(images) { index, bitmap ->
                        Box {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { vm.removeImage(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, contentDescription = "제거",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (progress != null) {
                val (current, total, imageProgress) = progress!!
                val percent = (imageProgress * 100).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (total > 1) "$current / ${total}장" else "분석 중",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text("$percent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { imageProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        statusMessage ?: "AI 모델 연결 중...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = { vm.processImages(sessionName) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = images.isNotEmpty()
                ) {
                    Text("단어 추출 시작")
                }
            }
        }
    }

    if (showCameraNextChoice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("${images.size}장 추가됨") },
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
                TextButton(onClick = { showCameraNextChoice = false }) {
                    Text("완료 (${images.size}장)")
                }
            }
        )
    }

    if (uiState is ImportUiState.Error) {
        AlertDialog(
            onDismissRequest = { vm.resetError() },
            title = { Text("오류") },
            text = { Text((uiState as ImportUiState.Error).message) },
            confirmButton = { TextButton(onClick = { vm.resetError() }) { Text("확인") } }
        )
    }
}


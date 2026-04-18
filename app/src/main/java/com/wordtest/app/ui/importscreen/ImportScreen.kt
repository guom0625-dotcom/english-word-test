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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
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
    val images by vm.selectedImages.collectAsState()
    val includeSynonyms by vm.includeSynonyms.collectAsState()
    val includeAntonyms by vm.includeAntonyms.collectAsState()
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

            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("이미지 선택 (갤러리)")
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

            // 추출 옵션
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("추출 옵션", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                    OptionRow(
                        label = "유의어 포함 (= 기호)",
                        description = "= 기호가 붙은 유의어 함께 추출",
                        checked = includeSynonyms,
                        onCheckedChange = { vm.toggleSynonyms(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OptionRow(
                        label = "반대어 포함 (<-> 기호)",
                        description = "<-> 기호가 붙은 반대어 함께 추출",
                        checked = includeAntonyms,
                        onCheckedChange = { vm.toggleAntonyms(it) }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { vm.processImages(sessionName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = images.isNotEmpty() && uiState !is ImportUiState.Processing
            ) {
                if (uiState is ImportUiState.Processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("AI가 단어 인식 중...")
                } else {
                    Text("단어 추출 시작")
                }
            }
        }
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

@Composable
private fun OptionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

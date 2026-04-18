package com.wordtest.app.ui.apikey

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wordtest.app.WordTestApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyScreen(
    isFirstLaunch: Boolean,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val apiKeyStore = (context.applicationContext as WordTestApplication).apiKeyStore

    var apiKey by remember { mutableStateOf(if (isFirstLaunch) "" else apiKeyStore.getApiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun save() {
        if (apiKey.isBlank()) {
            error = "API 키를 입력해주세요."
            return
        }
        if (!apiKey.startsWith("AIza")) {
            error = "올바른 Gemini API 키 형식이 아닙니다."
            return
        }
        apiKeyStore.saveApiKey(apiKey.trim())
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFirstLaunch) "API 키 설정" else "API 키 변경") },
                navigationIcon = {
                    if (!isFirstLaunch && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isFirstLaunch) {
                Text("Gemini API 키 입력", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "이미지에서 단어를 인식하기 위해\nGemini API 키가 필요합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        "aistudio.google.com → Get API key\n→ Create API key in new project",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(32.dp))
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; error = "" },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "숨기기" else "보기"
                        )
                    }
                },
                isError = error.isNotBlank(),
                supportingText = { if (error.isNotBlank()) Text(error) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() })
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank()
            ) {
                Text(if (isFirstLaunch) "시작하기" else "저장")
            }
        }
    }
}

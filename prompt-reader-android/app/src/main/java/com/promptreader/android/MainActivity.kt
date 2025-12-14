package com.promptreader.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.promptreader.android.parser.PromptReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var selectedUri by remember { mutableStateOf<Uri?>(null) }
                var tool by remember { mutableStateOf("") }
                var positive by remember { mutableStateOf("") }
                var negative by remember { mutableStateOf("") }
                var setting by remember { mutableStateOf("") }
                var raw by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }

                val scope = rememberCoroutineScope()

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    selectedUri = uri
                }

                LaunchedEffect(selectedUri) {
                    val uri = selectedUri ?: return@LaunchedEffect

                    error = null
                    positive = ""
                    negative = ""
                    setting = ""
                    raw = ""
                    tool = ""

                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }

                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { PromptReader.parse(this@MainActivity, uri) }
                        }
                        result.fold(
                            onSuccess = {
                                tool = it.tool
                                positive = it.positive
                                negative = it.negative
                                setting = it.setting
                                raw = it.raw
                            },
                            onFailure = {
                                error = it.message ?: it.toString()
                            }
                        )
                    }
                }

                fun copy(text: String) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                            Text("选择图片")
                        }
                        if (selectedUri != null) {
                            Button(onClick = { if (positive.isNotBlank()) copy(positive) }) {
                                Text("复制正向")
                            }
                            Button(onClick = { if (negative.isNotBlank()) copy(negative) }) {
                                Text("复制反向")
                            }
                        }
                    }

                    if (!tool.isNullOrBlank()) {
                        Text("工具: $tool")
                    }

                    if (error != null) {
                        Text("错误: $error")
                    }

                    OutlinedTextField(
                        value = positive,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Positive prompt") },
                        minLines = 3,
                        readOnly = true,
                    )

                    OutlinedTextField(
                        value = negative,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Negative prompt") },
                        minLines = 3,
                        readOnly = true,
                    )

                    OutlinedTextField(
                        value = setting,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Setting") },
                        minLines = 2,
                        readOnly = true,
                    )

                    Button(onClick = { if (raw.isNotBlank()) copy(raw) }, enabled = raw.isNotBlank()) {
                        Text("复制 Raw")
                    }

                    OutlinedTextField(
                        value = raw,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Raw") },
                        minLines = 6,
                        readOnly = true,
                    )
                }
            }
        }
    }
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appSettings by viewModel.appSettings.collectAsState()
    val pairedConversation by viewModel.pairedConversation.collectAsState()

    var apiKeyInput1 by remember { mutableStateOf("") }
    var apiKeyInput2 by remember { mutableStateOf("") }
    var apiKeyInput3 by remember { mutableStateOf("") }
    var isApiKeyMasked by remember { mutableStateOf(true) }
    var hasExistingKey by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val testResult by viewModel.modelTestState.collectAsState()
    val isTesting by viewModel.isTestingModel.collectAsState()

    LaunchedEffect(appSettings) {
        hasExistingKey = !appSettings.apiKeyCiphertext.isNullOrBlank()
        if (hasExistingKey && apiKeyInput1.isBlank() && apiKeyInput2.isBlank() && apiKeyInput3.isBlank()) {
            val decryptedKeys = viewModel.getDecryptedApiKeys()
            if (decryptedKeys.isNotEmpty()) {
                apiKeyInput1 = decryptedKeys.getOrElse(0) { "" }
                apiKeyInput2 = decryptedKeys.getOrElse(1) { "" }
                apiKeyInput3 = decryptedKeys.getOrElse(2) { "" }
            }
        }
        if (modelInput.isBlank() && appSettings.groqModel.isNotBlank()) {
            modelInput = appSettings.groqModel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("nav_back_settings")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Groq API Key Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Groq Cloud Classifier Keys", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Configure up to 3 Groq API keys. The app automatically rotates across them if rate limits (HTTP 429) or quota errors occur. All keys are encrypted in Android Keystore using hardware AES-GCM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput1,
                        onValueChange = { apiKeyInput1 = it },
                        label = { Text("Primary Key (Key 1)") },
                        placeholder = { Text("gsk_...") },
                        visualTransformation = if (isApiKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyMasked = !isApiKeyMasked }) {
                                Icon(
                                    if (isApiKeyMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle key visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_groq_api_key_1")
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyInput2,
                        onValueChange = { apiKeyInput2 = it },
                        label = { Text("Backup Key 2 (Optional)") },
                        placeholder = { Text("gsk_...") },
                        visualTransformation = if (isApiKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_groq_api_key_2")
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKeyInput3,
                        onValueChange = { apiKeyInput3 = it },
                        label = { Text("Backup Key 3 (Optional)") },
                        placeholder = { Text("gsk_...") },
                        visualTransformation = if (isApiKeyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_groq_api_key_3")
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveApiKeys(listOf(apiKeyInput1, apiKeyInput2, apiKeyInput3))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_api_key")
                        ) {
                            Text("Save All Keys")
                        }

                        if (hasExistingKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyInput1 = ""
                                    apiKeyInput2 = ""
                                    apiKeyInput3 = ""
                                    viewModel.saveApiKeys(emptyList())
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                                modifier = Modifier.testTag("btn_clear_api_key")
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // AI Model & Consent
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Model Selection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select or enter your preferred Groq model ID. Recommended: openai/gpt-oss-120b for zero reasoning leaks and accurate classification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Groq Model ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_groq_model")
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val trimmed = modelInput.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.setGroqModel(trimmed)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_groq_model")
                        ) {
                            Text("Save Model")
                        }

                        OutlinedButton(
                            onClick = {
                                val targetModel = modelInput.trim().ifBlank { "openai/gpt-oss-120b" }
                                val testKey = apiKeyInput1.trim().ifBlank { apiKeyInput2.trim().ifBlank { apiKeyInput3.trim() } }
                                viewModel.testModel(targetModel, testKey)
                            },
                            enabled = !isTesting && modelInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_test_groq_model")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Testing...")
                            } else {
                                Text("Test Model")
                            }
                        }
                    }

                    // Test Model Status Display
                    if (testResult != null) {
                        Spacer(Modifier.height(8.dp))
                        val (bgColor, textColor, icon) = when (testResult) {
                            is com.example.ai.TestModelResult.Success -> Triple(
                                ExpenseGreen.copy(alpha = 0.15f),
                                ExpenseGreen,
                                Icons.Default.CheckCircle
                            )
                            is com.example.ai.TestModelResult.UnsupportedModel -> Triple(
                                AlertRed.copy(alpha = 0.15f),
                                AlertRed,
                                Icons.Default.Warning
                            )
                            is com.example.ai.TestModelResult.AuthError -> Triple(
                                AlertRed.copy(alpha = 0.15f),
                                AlertRed,
                                Icons.Default.Lock
                            )
                            is com.example.ai.TestModelResult.RateLimit -> Triple(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.onTertiaryContainer,
                                Icons.Default.HourglassTop
                            )
                            is com.example.ai.TestModelResult.NetworkError -> Triple(
                                AlertRed.copy(alpha = 0.15f),
                                AlertRed,
                                Icons.Default.WifiOff
                            )
                            else -> Triple(
                                AlertRed.copy(alpha = 0.15f),
                                AlertRed,
                                Icons.Default.ErrorOutline
                            )
                        }

                        val resultMsg = when (val res = testResult) {
                            is com.example.ai.TestModelResult.Success -> res.message
                            is com.example.ai.TestModelResult.UnsupportedModel -> "Unsupported Model: ${res.message}"
                            is com.example.ai.TestModelResult.AuthError -> "Authentication Error: ${res.message}"
                            is com.example.ai.TestModelResult.RateLimit -> "Rate Limit: ${res.message}"
                            is com.example.ai.TestModelResult.NetworkError -> "Network Error: ${res.message}"
                            is com.example.ai.TestModelResult.Error -> "Error: ${res.message}"
                            null -> ""
                        }

                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("banner_model_test_result")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = resultMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearModelTestState() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = textColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "openai/gpt-oss-120b",
                            "openai/gpt-oss-20b",
                            "groq/compound",
                            "qwen/qwen3.8-27b",
                            "qwen/qwen3.6-27b"
                        ).forEach { preset ->
                            FilterChip(
                                selected = modelInput == preset,
                                onClick = {
                                    modelInput = preset
                                },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("chip_preset_${preset.replace('/', '_').replace('.', '_')}")
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Cloud Processing Consent", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "When enabled, incoming paired text is sent to Groq for natural language classification. When off, local regex parser is used.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = appSettings.aiConsentGranted,
                            onCheckedChange = { viewModel.setAiConsent(it) },
                            modifier = Modifier.testTag("switch_ai_consent")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Confirmation Mode", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Require a 10-minute confirmation code (e.g. 'confirm C123') for every single expense before writing to the ledger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = appSettings.strictMode,
                            onCheckedChange = { viewModel.setStrictMode(it) },
                            modifier = Modifier.testTag("switch_strict_mode")
                        )
                    }
                }
            }

            // WhatsApp Direct Conversation Connection
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("WhatsApp Direct Pair Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (pairedConversation != null) {
                        Text("Active: ${pairedConversation?.displayLabel}", fontWeight = FontWeight.SemiBold, color = ExpenseGreen)
                        Text("ID: ${pairedConversation?.conversationId}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onNavigateToSetup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Manage / Re-pair Connection")
                        }
                    } else {
                        Text("No verified paired conversation.", color = AlertRed)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToSetup,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Pairing Wizard")
                        }
                    }
                }
            }

            // Data Export & Reset
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data Management", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val csv = viewModel.exportExpensesCsv(context)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Trip Budget CSV", csv)
                            clipboard.setPrimaryClip(clip)
                            viewModel.showMessage("Expenses copied as CSV to clipboard (${csv.lines().size - 1} rows)")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_export_csv")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Expenses as CSV")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_reset_all_data")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset All Data")
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Trip Data?") },
            text = { Text("This will permanently clear all recorded expenses, snapshots, and inbox events. Places and default budget will be re-seeded.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

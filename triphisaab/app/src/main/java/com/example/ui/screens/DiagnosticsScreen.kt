package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.LocationTrackingService
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val diagnostics by viewModel.diagnosticsState.collectAsState()
    val isTracking by viewModel.isTrackingActive.collectAsState()
    val lastFix by LocationTrackingService.lastFix.collectAsState()
    val paired by viewModel.pairedConversation.collectAsState()

    var testMessageText by remember { mutableStateOf("petrol 2200") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("nav_back_diag")) {
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
            // Milestone M0 Capabilities Matrix
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Milestone M0 Capability Matrix", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    CapabilityRow("NotificationListenerService Connected", diagnostics.isListenerConnected)
                    CapabilityRow("WhatsApp Direct Chat Paired", paired != null)
                    CapabilityRow("Shortcut ID Exposed (android.shortcut.id)", diagnostics.hasShortcutId)
                    CapabilityRow("Sender Person Key/Metadata", diagnostics.hasPersonMetadata)
                    CapabilityRow("MessagingStyle Bundle Present", diagnostics.hasMessagingStyle)
                    CapabilityRow("RemoteInput Reply Action Available", diagnostics.hasRemoteInputReply)
                    CapabilityRow("Location Tracking Session Active", isTracking)

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testSendReply(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_diag_test_reply")
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Invoke RemoteInput Direct Reply")
                    }
                }
            }

            // Last Received Metadata Dump
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last Received Metadata Dump", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (diagnostics.lastMetadataDump != null) {
                        Text(
                            text = diagnostics.lastMetadataDump!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(diagnostics.lastEventTime ?: 0L))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text("No notifications received yet from WhatsApp Business.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Location Snapshot Diagnostics
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Location Telemetry", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    if (lastFix != null) {
                        val fix = lastFix!!
                        val ageSec = (System.currentTimeMillis() - fix.time) / 1000
                        Text("Latest Fix: Lat ${String.format("%.4f", fix.latitude)}, Lng ${String.format("%.4f", fix.longitude)}")
                        Text("Accuracy: ±${fix.accuracy.toInt()} meters")
                        Text("Age: ${ageSec}s ago (Limit: 120s)")
                    } else {
                        Text("No location fixes buffered. Start tracking session on Home screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Emulator Test Fixtures (Clearly marked)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Emulator Test Fixtures", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Clearly labeled test runner to verify parsing, deduplication, and ledger updates inside AI Studio without needing physical notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = testMessageText,
                        onValueChange = { testMessageText = it },
                        label = { Text("Candidate message text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.injectSyntheticCandidate(testMessageText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_inject_test_expense")
                    ) {
                        Text("[Demo Fixture] Inject Candidate Message")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.injectSyntheticCandidate("Gilgit mein total kharcha ktna hua") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Query Gilgit", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.injectSyntheticCandidate("undo") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Undo", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityRow(label: String, isSupported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Icon(
            if (isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isSupported) ExpenseGreen else AlertRed,
            modifier = Modifier.size(18.dp)
        )
    }
}

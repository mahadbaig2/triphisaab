package com.example.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.notifications.TripBudgetNotificationListener
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: MainViewModel,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val pairedConversation by viewModel.pairedConversation.collectAsState()
    val activeCode by viewModel.activePairingCode.collectAsState()
    val codeExpiresAt by viewModel.codeExpiresAt.collectAsState()
    val discoveredMeta by viewModel.discoveredMetadata.collectAsState()
    val diagnostics by viewModel.diagnosticsState.collectAsState()

    var isListenerPermissionGranted by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        )
    }

    // Refresh permission status on resume/delay
    LaunchedEffect(Unit) {
        while (true) {
            isListenerPermissionGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            delay(2000L)
        }
    }

    // Countdown timer for pairing code
    var remainingSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(codeExpiresAt) {
        while (codeExpiresAt > System.currentTimeMillis()) {
            remainingSeconds = (codeExpiresAt - System.currentTimeMillis()) / 1000L
            delay(1000L)
        }
        remainingSeconds = 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp Direct Connection") },
                navigationIcon = {
                    IconButton(onClick = onFinish, modifier = Modifier.testTag("nav_back")) {
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Device-Only WhatsApp Isolation",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Only incoming text from your verified Personal WhatsApp conversation in WhatsApp Business is eligible for budget processing. Normal customer chats are strictly ignored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // STEP 1: Notification Listener Access
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Step 1: Notification Access",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(if (isListenerPermissionGranted) "Granted" else "Required") },
                            leadingIcon = {
                                Icon(
                                    if (isListenerPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isListenerPermissionGranted) ExpenseGreen else AlertRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Trip Budget requires Notification Listener access to read incoming WhatsApp Business notifications and trigger RemoteInput direct replies without accessibility scraping.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_grant_notif_access")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isListenerPermissionGranted) "Re-check Notification Access" else "Open Notification Listener Settings")
                    }
                }
            }

            // STEP 2: Random Expiring Pairing Code
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step 2: Expiring Pairing Code",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Generate a single-use 6-character code. Send this code from your Personal WhatsApp to your Business number on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    if (activeCode != null && remainingSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = activeCode ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Expires in ${remainingSeconds / 60}m ${remainingSeconds % 60}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.generatePairingCode() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generate_pairing_code")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (activeCode == null) "Generate Code" else "New Code")
                        }

                        if (activeCode != null) {
                            OutlinedButton(
                                onClick = { viewModel.cancelPairing() },
                                modifier = Modifier.testTag("btn_cancel_pairing")
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    // Demo fixture injection for browser emulator testing
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val code = activeCode ?: viewModel.generatePairingCode()
                            viewModel.injectSyntheticCandidate("PAIR $code", simulatePairing = true)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_demo_pairing_fixture")
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("[Demo Fixture] Simulate Incoming Code Candidate")
                    }
                }
            }

            // STEP 3: Capability Diagnostics & Discovered Metadata
            if (discoveredMeta != null) {
                val meta = discoveredMeta!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Step 3: Discovered Metadata",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (meta.hasIsolatingKey) {
                                Badge(containerColor = ExpenseGreen) { Text("ISOLATED", color = Color.White) }
                            } else {
                                Badge(containerColor = AlertRed) { Text("UNSUPPORTED", color = Color.White) }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Metadata details table
                        Text("Package: ${meta.pkg}", style = MaterialTheme.typography.bodySmall)
                        Text("Sender Name: ${meta.senderName ?: "None"}", style = MaterialTheme.typography.bodySmall)
                        Text("Conversation: ${meta.conversationTitle ?: "None"}", style = MaterialTheme.typography.bodySmall)
                        Text("Shortcut ID: ${meta.shortcutId ?: "Not exposed"}", style = MaterialTheme.typography.bodySmall)
                        Text("Person Key: ${meta.senderPersonKey ?: "None"}", style = MaterialTheme.typography.bodySmall)
                        Text("Fingerprint: ${meta.fingerprint}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                        Spacer(Modifier.height(12.dp))

                        if (!meta.hasIsolatingKey) {
                            Text(
                                text = "⚠ WARNING: This notification does not expose an isolating conversation key or shortcut ID. Trip Budget will fail closed to prevent accidental processing of customer chats.",
                                color = AlertRed,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "✓ Metadata contains a unique conversation identifier. Ready to approve pairing.",
                                color = ExpenseGreen,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.approvePairing() },
                                colors = ButtonDefaults.buttonColors(containerColor = ExpenseGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_approve_pairing")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Approve Pairing")
                            }
                        }
                    }
                }
            }

            // STEP 4: Active Paired State & Test Reply
            if (pairedConversation != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Currently Paired & Verified",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Direct Conversation: ${pairedConversation?.displayLabel}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Target ID: ${pairedConversation?.conversationId}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.testSendReply(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_test_reply")
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Send Test Notification Reply")
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.unpairConversation() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_unpair")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unpair Conversation")
                        }
                    }
                }
            }
        }
    }
}

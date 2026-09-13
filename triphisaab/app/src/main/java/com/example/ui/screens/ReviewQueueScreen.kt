package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InboxEvent
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val reviewItems by viewModel.reviewEvents.collectAsState()
    var selectedEventForApproval by remember { mutableStateOf<InboxEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Queue") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("nav_back_review")) {
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
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Candidate messages that were ambiguous or required manual confirmation before writing to the ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (reviewItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = ExpenseGreen, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Queue is empty", fontWeight = FontWeight.Bold)
                        Text("All incoming messages are classified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(reviewItems, key = { it.id }) { event ->
                        ReviewItemCard(
                            event = event,
                            onApprove = { selectedEventForApproval = event },
                            onIgnore = { viewModel.resolveReviewItem(event, approveAsExpense = false) }
                        )
                    }
                }
            }
        }
    }

    selectedEventForApproval?.let { event ->
        ApproveReviewDialog(
            event = event,
            onDismiss = { selectedEventForApproval = null },
            onConfirm = { desc, amt ->
                viewModel.resolveReviewItem(event, approveAsExpense = true, description = desc, amountDecimal = amt)
                selectedEventForApproval = null
            }
        )
    }
}

@Composable
fun ReviewItemCard(
    event: InboxEvent,
    onApprove: () -> Unit,
    onIgnore: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("review_card_${event.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(
                    containerColor = if (event.classificationState == "WAITING_CONFIRMATION") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(event.classificationState)
                }
                Text(
                    text = sdf.format(Date(event.detectedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(10.dp))

            Text("Message Text:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(
                text = "\"${event.originalText}\"",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )

            if (!event.proposedCommandJson.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Proposed: ${event.proposedCommandJson}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onIgnore,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ignore")
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Record...")
                }
            }
        }
    }
}

@Composable
fun ApproveReviewDialog(
    event: InboxEvent,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amountDecimal: String) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Original text: \"${event.originalText}\"", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (e.g. Petrol)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amt,
                    onValueChange = { amt = it },
                    label = { Text("Amount in PKR (e.g. 2200)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (desc.isNotBlank() && amt.isNotBlank()) {
                    onConfirm(desc.trim(), amt.trim())
                }
            }) {
                Text("Confirm & Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

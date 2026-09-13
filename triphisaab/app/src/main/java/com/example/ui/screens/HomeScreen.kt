package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Expense
import com.example.engine.MoneyFormatter
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToExpenses: () -> Unit,
    onNavigateToLocations: () -> Unit,
    onNavigateToReviewQueue: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val budget by viewModel.activeBudget.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val recentExpenses by viewModel.recentExpenses.collectAsState()
    val places by viewModel.allPlaces.collectAsState()
    val isTracking by viewModel.isTrackingActive.collectAsState()
    val pairedConversation by viewModel.pairedConversation.collectAsState()
    val diagnostics by viewModel.diagnosticsState.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val pendingReviewCount by viewModel.pendingReviewCount.collectAsState()

    var showManualAddDialog by remember { mutableStateOf(false) }
    var showSetBudgetDialog by remember { mutableStateOf(false) }

    val placesMap = remember(places) { places.associateBy { it.id } }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.startTrackingSession(context)
        } else {
            viewModel.showMessage("Location permission is required for travel route attribution")
        }
    }

    val totalSpentPaisa = totalSpent ?: 0L
    val limitPaisa = budget?.limitPaisa
    val remainingPaisa = limitPaisa?.let { it - totalSpentPaisa }
    val isOverBudget = remainingPaisa != null && remainingPaisa < 0
    val progressRatio = if (limitPaisa != null && limitPaisa > 0) {
        (totalSpentPaisa.toFloat() / limitPaisa.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Trip Budget", fontWeight = FontWeight.Bold)
                        Text("Islamabad–Khunjerab Expedition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics, modifier = Modifier.testTag("btn_top_diagnostics")) {
                        Icon(Icons.Default.Dns, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("btn_top_settings")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_expense")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Unpaired Warning Banner
            if (pairedConversation == null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSetup() }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = AlertRed)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WhatsApp Not Paired", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Tap to run M0 Connection Spike & pair Personal number safely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AlertRed)
                        }
                    }
                }
            }

            // PRIMARY BALANCE CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL EXPENSES",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { showSetBudgetDialog = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Budget", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = MoneyFormatter.formatPaisa(totalSpentPaisa),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        // Progress bar towards budget limit
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isOverBudget) AlertRed else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Trip Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = limitPaisa?.let { MoneyFormatter.formatPaisa(it) } ?: "Not set",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (isOverBudget) "Over Budget" else "Remaining", style = MaterialTheme.typography.labelSmall, color = if (isOverBudget) AlertRed else MaterialTheme.colorScheme.outline)
                                Text(
                                    text = when {
                                        limitPaisa == null -> "—"
                                        isOverBudget -> "-${MoneyFormatter.formatPaisa(Math.abs(remainingPaisa ?: 0L))}"
                                        else -> MoneyFormatter.formatPaisa(remainingPaisa ?: 0L)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOverBudget) AlertRed else ExpenseGreen
                                )
                            }
                        }
                    }
                }
            }

            // LOCATION TRACKING SESSION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTracking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isTracking) ExpenseGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isTracking) ExpenseGreen else MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTracking) "Location Session Active" else "Location Tracking Idle",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isTracking) "Buffering fixes to attribute expenses to Northern cities" else "Tap to start continuous route fix buffer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (isTracking) {
                                    viewModel.stopTrackingSession(context)
                                } else {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine) {
                                        viewModel.startTrackingSession(context)
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTracking) AlertRed else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("btn_toggle_location_session")
                        ) {
                            Text(if (isTracking) "Stop" else "Start")
                        }
                    }
                }
            }

            // STATUS CHIPS ROW
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        title = "Listener",
                        subtitle = if (diagnostics.isListenerConnected) "Active" else "Offline",
                        isGood = diagnostics.isListenerConnected,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSetup
                    )
                    StatusChip(
                        title = "AI Parser",
                        subtitle = if (appSettings.aiConsentGranted && !appSettings.apiKeyCiphertext.isNullOrBlank()) "Groq" else "Local",
                        isGood = true,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSettings
                    )
                    StatusChip(
                        title = "Reply",
                        subtitle = if (diagnostics.hasRemoteInputReply) "Ready" else "Idle",
                        isGood = diagnostics.hasRemoteInputReply,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDiagnostics
                    )
                }
            }

            // NAVIGATION SHORTCUTS: Locations & Review Queue
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedCard(
                        onClick = onNavigateToLocations,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_nav_locations")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text("By Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Gilgit, Hunza, Sost...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    OutlinedCard(
                        onClick = onNavigateToReviewQueue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_nav_review")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                if (pendingReviewCount > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge(containerColor = AlertRed) { Text("$pendingReviewCount") }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Review Queue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Ambiguous & pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // RECENT EXPENSES HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Expenses",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (recentExpenses.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.undoLastExpense() },
                                modifier = Modifier.testTag("btn_undo_last")
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo")
                            }
                        }
                        TextButton(onClick = onNavigateToExpenses, modifier = Modifier.testTag("btn_view_all_expenses")) {
                            Text("View All")
                        }
                    }
                }
            }

            // RECENT EXPENSES LIST
            if (recentExpenses.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("No expenses logged yet", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Text("Send 'petrol 2200' via WhatsApp or tap + below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                items(recentExpenses) { expense ->
                    ExpenseListItem(
                        expense = expense,
                        placeName = placesMap[expense.effectivePlaceId]?.canonicalName ?: "Unassigned"
                    )
                }
            }
        }
    }

    // Manual Add Expense Dialog
    if (showManualAddDialog) {
        ManualAddExpenseDialog(
            places = places,
            onDismiss = { showManualAddDialog = false },
            onConfirm = { desc, amt, placeId ->
                viewModel.recordManualExpense(desc, amt, placeId)
                showManualAddDialog = false
            }
        )
    }

    // Set Budget Limit Dialog
    if (showSetBudgetDialog) {
        SetBudgetDialog(
            currentLimitPaisa = limitPaisa,
            onDismiss = { showSetBudgetDialog = false },
            onConfirm = { newLimitPaisa ->
                viewModel.setBudgetLimit(newLimitPaisa)
                showSetBudgetDialog = false
            }
        )
    }
}

@Composable
fun StatusChip(
    title: String,
    subtitle: String,
    isGood: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isGood) ExpenseGreen else AlertRed, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ExpenseListItem(
    expense: Expense,
    placeName: String,
    onClick: (() -> Unit)? = null
) {
    val displayDate = remember(expense.expenseTime, expense.timeCertainty) {
        com.example.engine.TripDateParser.formatDisplayDate(expense.expenseTime, expense.timeCertainty)
    }

    val categoryDisplay = remember(expense.category, expense.subcategory) {
        val cat = expense.category ?: "Miscellaneous"
        val sub = expense.subcategory
        if (!sub.isNullOrBlank()) "$cat / $sub" else cat
    }

    Card(
        onClick = { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_item_${expense.id}")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = categoryDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(4.dp))
                    val locText = if (expense.locationCertainty == "UNCERTAIN") "Uncertain (retrospective)" else placeName
                    Text(locText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(8.dp))
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(8.dp))
                    Text(displayDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                text = MoneyFormatter.formatPaisa(expense.amountPaisa),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ManualAddExpenseDialog(
    places: List<com.example.data.model.Place>,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amountDecimal: String, placeId: String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedPlaceId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (e.g. Petrol, Chai)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_expense_desc")
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (PKR, e.g. 2200)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_expense_amount")
                )
                // Place dropdown/choice
                Text("Location (Optional)", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickPlaces = places.take(4)
                    quickPlaces.forEach { place ->
                        FilterChip(
                            selected = selectedPlaceId == place.id,
                            onClick = { selectedPlaceId = if (selectedPlaceId == place.id) null else place.id },
                            label = { Text(place.canonicalName, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (description.isNotBlank() && amount.isNotBlank()) {
                        onConfirm(description, amount, selectedPlaceId)
                    }
                },
                modifier = Modifier.testTag("btn_confirm_add_expense")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SetBudgetDialog(
    currentLimitPaisa: Long?,
    onDismiss: () -> Unit,
    onConfirm: (newLimitPaisa: Long?) -> Unit
) {
    var amountText by remember {
        mutableStateOf(currentLimitPaisa?.let { (it / 100).toString() } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Trip Budget Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the total budget for your motorcycle expedition in PKR.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total Limit (e.g. 100000)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_set_budget")
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val clean = amountText.trim()
                if (clean.isBlank()) {
                    onConfirm(null)
                } else {
                    try {
                        val paisa = MoneyFormatter.parseToPaisa(clean)
                        onConfirm(paisa)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }) {
                Text("Save Limit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

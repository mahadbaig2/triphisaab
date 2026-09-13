package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.Expense
import com.example.engine.MoneyFormatter
import com.example.ui.MainViewModel
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ExpenseGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val expenses by viewModel.allActiveExpenses.collectAsState()
    val places by viewModel.allPlaces.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val placesMap = remember(places) { places.associateBy { it.id } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    val filteredExpenses = remember(expenses, searchQuery, placesMap) {
        if (searchQuery.isBlank()) expenses
        else {
            val q = searchQuery.trim().lowercase()
            expenses.filter { exp ->
                exp.description.lowercase().contains(q) ||
                        (exp.category?.lowercase()?.contains(q) == true) ||
                        (placesMap[exp.effectivePlaceId]?.canonicalName?.lowercase()?.contains(q) == true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Expenses") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("nav_back_expenses")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undoLastExpense() }, modifier = Modifier.testTag("btn_undo_top")) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo Last")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_expense_screen")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by description, category, or place...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("input_search_expenses")
            )

            Text(
                text = "${filteredExpenses.size} ${if (filteredExpenses.size == 1) "expense" else "expenses"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (filteredExpenses.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching expenses found", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        com.example.ui.screens.ExpenseListItem(
                            expense = expense,
                            placeName = placesMap[expense.effectivePlaceId]?.canonicalName ?: "Unassigned",
                            onClick = { selectedExpense = expense }
                        )
                    }
                }
            }
        }
    }

    // Detail & Correction Dialog
    selectedExpense?.let { exp ->
        ExpenseDetailDialog(
            expense = exp,
            placeName = placesMap[exp.effectivePlaceId]?.canonicalName ?: "Unassigned",
            places = places,
            categories = categories,
            onDismiss = { selectedExpense = null },
            onReverse = {
                viewModel.reverseExpense(exp.id)
                selectedExpense = null
            },
            onSaveCorrection = { desc, amtPaisa, placeId, cat, sub ->
                viewModel.correctExpense(exp.id, desc, amtPaisa, placeId, cat, sub)
                selectedExpense = null
            }
        )
    }

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
}

@Composable
fun ExpenseDetailDialog(
    expense: Expense,
    placeName: String,
    places: List<com.example.data.model.Place>,
    categories: List<com.example.data.model.Category>,
    onDismiss: () -> Unit,
    onReverse: () -> Unit,
    onSaveCorrection: (newDesc: String, newAmtPaisa: Long, newPlaceId: String?, newCat: String?, newSub: String?) -> Unit
) {
    val displayDate = remember(expense.expenseTime, expense.timeCertainty) {
        com.example.engine.TripDateParser.formatDisplayDate(expense.expenseTime, expense.timeCertainty)
    }
    var isEditing by remember { mutableStateOf(false) }
    var editDesc by remember { mutableStateOf(expense.description) }
    var editAmt by remember { mutableStateOf(MoneyFormatter.toDecimalString(expense.amountPaisa)) }
    var editPlaceId by remember { mutableStateOf(expense.effectivePlaceId) }
    var editCategory by remember { mutableStateOf(expense.category ?: "Miscellaneous") }
    var editSubcategory by remember { mutableStateOf(expense.subcategory ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Expense" else "Expense Details") },
        text = {
            if (isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAmt,
                        onValueChange = { editAmt = it },
                        label = { Text("Amount (PKR)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSubcategory,
                        onValueChange = { editSubcategory = it },
                        label = { Text("Subcategory (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Place", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        places.take(4).forEach { p ->
                            FilterChip(
                                selected = editPlaceId == p.id,
                                onClick = { editPlaceId = if (editPlaceId == p.id) null else p.id },
                                label = { Text(p.canonicalName) }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = MoneyFormatter.formatPaisa(expense.amountPaisa),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Description: ${expense.description}", fontWeight = FontWeight.SemiBold)
                    val catText = expense.category ?: "Miscellaneous"
                    val subText = expense.subcategory?.let { " / $it" } ?: ""
                    Text("Category: $catText$subText", fontWeight = FontWeight.Medium)
                    val locCertaintyText = if (expense.locationCertainty == "UNCERTAIN") " (retrospective entry)" else ""
                    Text("Location: $placeName$locCertaintyText")
                    Text("Date (Pakistan): $displayDate", style = MaterialTheme.typography.bodySmall)
                    Text("Certainty: ${expense.timeCertainty} · Source: ${expense.timeSource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("ID: ${expense.id}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    if (expense.sourceEventId != null) {
                        Text("Source WhatsApp Event: ${expense.sourceEventId.take(8)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Button(onClick = {
                    val paisa = MoneyFormatter.parseToPaisa(editAmt)
                    onSaveCorrection(editDesc.trim(), paisa, editPlaceId, editCategory.trim(), editSubcategory.trim().ifBlank { null })
                }) {
                    Text("Save Changes")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReverse,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reverse")
                    }
                    Button(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isEditing) isEditing = false else onDismiss()
            }) {
                Text(if (isEditing) "Cancel" else "Close")
            }
        }
    )
}

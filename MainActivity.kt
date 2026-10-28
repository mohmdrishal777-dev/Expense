package com.razal.expenses

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { MaterialTheme { App() } }
    }
}

fun aed(v: Double): String = "AED " + String.format(Locale.US, "%,.2f", v)

fun hasSms(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

fun exportCsv(ctx: Context, list: List<Expense>, ym: YearMonth) {
    val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("date,amount_aed,where,category,purpose,source\n")
    list.forEach {
        sb.append(f.format(Date(it.ts))).append(',')
            .append(String.format(Locale.US, "%.2f", it.amount)).append(',')
            .append(q(it.merchant)).append(',')
            .append(q(it.category)).append(',')
            .append(q(it.purpose)).append(',')
            .append(it.source).append('\n')
    }
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Expenses $ym")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(i, "Export CSV"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var smsOk by remember { mutableStateOf(hasSms(ctx)) }

    val list = remember(Store.version, ym) { Store.forMonth(ym) }
    val total = list.sumOf { it.amount }
    val budget = Store.budget
    val byCat = list.groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r ->
        smsOk = r.values.all { it }
        val msg = if (smsOk) "Imported ${SmsParser.importInbox(ctx, 60)} expenses from SMS"
        else "SMS permission is needed for automatic capture"
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }
    val askSms = {
        permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    TextButton(onClick = { exportCsv(ctx, list, ym) }) { Text("Export") }
                    TextButton(onClick = { settings = true }) { Text("Settings") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }) { Text("+ Add expense") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("<") }
                    Text(
                        ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.year,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { ym = ym.plusMonths(1) }) { Text(">") }
                }
            }
            if (!smsOk) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Automatic capture is off. Allow SMS access to log bank messages as they arrive.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { askSms() }) { Text("Enable SMS capture") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Spent this month", style = MaterialTheme.typography.labelMedium)
                        Text(aed(total), style = MaterialTheme.typography.headlineMedium)
                        if (budget > 0) {
                            val frac = (total / budget).toFloat().coerceIn(0f, 1f)
                            val left = budget - total
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (left >= 0) aed(left) + " left of " + aed(budget)
                                else "Over budget by " + aed(-left)
                            )
                        } else {
                            Text("Set a monthly budget in Settings")
                        }
                    }
                }
            }
            if (byCat.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("By category", style = MaterialTheme.typography.titleSmall)
                            byCat.forEach { (cat, amt) ->
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat)
                                        Text(aed(amt))
                                    }
                                    LinearProgressIndicator(
                                        progress = { if (total > 0) (amt / total).toFloat() else 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item { Text("No expenses this month yet.") }
            }
            items(list, key = { it.id }) { e ->
                ListItem(
                    modifier = Modifier.clickable { editing = e },
                    headlineContent = { Text(e.merchant) },
                    supportingContent = {
                        Text(
                            listOf(e.category, e.purpose).filter { it.isNotBlank() }.joinToString(" · ") +
                                " · " + dateFmt.format(Date(e.ts))
                        )
                    },
                    trailingContent = { Text(aed(e.amount)) }
                )
            }
        }
    }

    if (adding) {
        ExpenseDialog(null, { adding = false }, null) { Store.add(it); adding = false }
    }
    editing?.let { e ->
        ExpenseDialog(
            e,
            { editing = null },
            { Store.delete(e.id); editing = null }
        ) { Store.update(it); editing = null }
    }
    if (settings) {
        SettingsDialog({ settings = false }) { askSms() }
    }
}

@Composable
fun ExpenseDialog(
    initial: Expense?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (Expense) -> Unit
) {
    var amount by remember { mutableStateOf(initial?.let { String.format(Locale.US, "%.2f", it.amount) } ?: "") }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var purpose by remember { mutableStateOf(initial?.purpose ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Uncategorized") }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add expense" else "Edit expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount (AED)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it },
                    label = { Text("Where") }, singleLine = true
                )
                OutlinedTextField(
                    value = purpose, onValueChange = { purpose = it },
                    label = { Text("Purpose") }, singleLine = true
                )
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Category: $category") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Store.CATEGORIES.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = { category = c; menu = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.replace(",", ".").toDoubleOrNull()
                if (a != null && a > 0) {
                    onSave(
                        Expense(
                            initial?.id ?: Store.newId(),
                            initial?.ts ?: System.currentTimeMillis(),
                            a,
                            merchant.ifBlank { "Unknown" },
                            category,
                            purpose,
                            initial?.source ?: "manual",
                            initial?.key ?: ""
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onSms: () -> Unit) {
    var b by remember {
        mutableStateOf(if (Store.budget > 0) Store.budget.toLong().toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = b, onValueChange = { b = it },
                    label = { Text("Monthly budget (AED)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedButton(onClick = onSms) { Text("Enable SMS capture / import last 60 days") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Store.setBudget(b.replace(",", "").toDoubleOrNull() ?: 0.0)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

package com.hoang.moneytrack.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.Reminder
import com.hoang.moneytrack.data.db.ReminderKind
import com.hoang.moneytrack.ui.common.TieredProgressBar
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.MoneyStyle
import com.hoang.moneytrack.ui.theme.Primary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(app: MoneyTrackApp) {
    var tab by remember { mutableIntStateOf(0) }
    var paying by remember { mutableStateOf<Reminder?>(null) }
    var payingDebt by remember { mutableStateOf<Reminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val reminders by app.repo.dao.reminders().collectAsState(emptyList())
    val bills = reminders.filter { it.kind == ReminderKind.BILL }
    val debts = reminders.filter { it.kind != ReminderKind.BILL }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.rem_bills)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.rem_debts)) })
        }
        Spacer(Modifier.height(12.dp))

        val visible = if (tab == 0) bills else debts
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.isEmpty()) item {
                Text(
                    stringResource(R.string.rem_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(visible.size, key = { visible[it].id }) { i ->
                val r = visible[i]
                if (r.kind == ReminderKind.BILL) BillCard(r, onPay = { paying = r })
                else DebtCard(r, onPay = { payingDebt = r })
            }
            item {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.rem_add))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    paying?.let { r ->
        ModalBottomSheet(onDismissRequest = { paying = null }) { PayBillForm(app, r) { paying = null } }
    }
    payingDebt?.let { r ->
        ModalBottomSheet(onDismissRequest = { payingDebt = null }) { PayDebtForm(app, r) { payingDebt = null } }
    }
    if (adding) ModalBottomSheet(onDismissRequest = { adding = false }) {
        AddReminderForm(app, if (tab == 0) ReminderKind.BILL else ReminderKind.DEBT_LEND) { adding = false }
    }
}

@Composable
private fun StatusChip(r: Reminder) {
    val (label, color) = when {
        r.paid -> R.string.rem_paid to Primary
        r.dueDate.isBefore(LocalDate.now()) -> R.string.rem_overdue to MaterialTheme.colorScheme.error
        else -> R.string.rem_unpaid to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun BillCard(r: Reminder, onPay: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(r)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.amount.toVnd(), style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle))
                    Text(
                        stringResource(R.string.rem_due_day, r.dueDate.format(DateTimeFormatter.ofPattern("dd/MM"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!r.paid) AssistChip(onClick = onPay, label = { Text(stringResource(R.string.rem_mark_paid)) })
            }
        }
    }
}

@Composable
private fun DebtCard(r: Reminder, onPay: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (r.kind == ReminderKind.DEBT_LEND) "↗ " else "↙ ") + r.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(r)
            }
            Text(
                "${r.paidAmount.toVnd()} / ${r.amount.toVnd()}",
                style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
            )
            TieredProgressBar(if (r.amount > 0) r.paidAmount.toFloat() / r.amount else 0f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.rem_due_day, r.dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!r.paid) AssistChip(onClick = onPay, label = { Text(stringResource(R.string.rem_record_payment)) })
            }
        }
    }
}

@Composable
private fun PayBillForm(app: MoneyTrackApp, r: Reminder, onDone: () -> Unit) {
    val wallets by app.repo.dao.wallets().collectAsState(emptyList())
    val categories by app.repo.dao.categories().collectAsState(emptyList())
    var walletId by remember { mutableStateOf<Long?>(null) }
    if (walletId == null && wallets.isNotEmpty()) walletId = wallets.first().id
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${r.title} — ${r.amount.toVnd()}", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wallets.size, key = { wallets[it].id }) { i ->
                val w = wallets[i]
                FilterChip(walletId == w.id, { walletId = w.id }, { Text(w.name) })
            }
        }
        Button(
            onClick = {
                scope.launch {
                    val homeCat = categories.firstOrNull { it.name == "Nhà ở" }?.id
                    app.repo.payReminder(r.id, walletId!!, homeCat)
                    onDone()
                }
            },
            enabled = walletId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.rem_mark_paid)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PayDebtForm(app: MoneyTrackApp, r: Reminder, onDone: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(r.title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            amountText, { amountText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.amount)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val amount = amountText.toLongOrNull() ?: 0L
        Button(
            onClick = { scope.launch { app.repo.recordDebtPayment(r, amount); onDone() } },
            enabled = amount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.rem_record_payment)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AddReminderForm(app: MoneyTrackApp, defaultKind: ReminderKind, onDone: () -> Unit) {
    var kind by remember { mutableStateOf(defaultKind) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.rem_add), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == ReminderKind.BILL, { kind = ReminderKind.BILL }, { Text(stringResource(R.string.rem_bills)) })
            FilterChip(kind == ReminderKind.DEBT_LEND, { kind = ReminderKind.DEBT_LEND }, { Text(stringResource(R.string.rem_lend)) })
            FilterChip(kind == ReminderKind.DEBT_BORROW, { kind = ReminderKind.DEBT_BORROW }, { Text(stringResource(R.string.rem_borrow)) })
        }
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.budget_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            amountText, { amountText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.amount)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            dayText, { dayText = it.filter(Char::isDigit).take(2) },
            label = { Text("Ngày (1-28)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val amount = amountText.toLongOrNull() ?: 0L
        val day = dayText.toIntOrNull()?.coerceIn(1, 28)
        Button(
            onClick = {
                scope.launch {
                    val today = LocalDate.now()
                    val due = day?.let { d ->
                        today.withDayOfMonth(d).let { if (it.isBefore(today)) it.plusMonths(1) else it }
                    } ?: today.plusDays(7)
                    app.repo.dao.insertReminder(
                        Reminder(
                            kind = kind, title = title.trim(), amount = amount, dueDate = due,
                            recurDayOfMonth = if (kind == ReminderKind.BILL) day else null,
                        ),
                    )
                    onDone()
                }
            },
            enabled = title.isNotBlank() && amount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

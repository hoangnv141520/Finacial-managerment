package com.hoang.moneytrack.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hoang.moneytrack.data.db.TxnRow
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.common.EmojiBadge
import com.hoang.moneytrack.ui.common.MonthPicker
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.MoneyStyle
import com.hoang.moneytrack.ui.theme.Primary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun TransactionsScreen(app: MoneyTrackApp) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var filter by remember { mutableStateOf<TxnType?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<TxnRow?>(null) }
    val scope = rememberCoroutineScope()

    val rows by remember(month, filter, query) {
        app.repo.dao.txnRows(month.atDay(1), month.atEndOfMonth(), filter, query.trim())
    }.collectAsState(emptyList())

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MonthPicker(month, { month = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, { filter = null }, { Text(stringResource(R.string.txn_all)) })
            FilterChip(filter == TxnType.INCOME, { filter = TxnType.INCOME }, { Text(stringResource(R.string.txn_income)) })
            FilterChip(filter == TxnType.EXPENSE, { filter = TxnType.EXPENSE }, { Text(stringResource(R.string.txn_expense)) })
        }
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text(stringResource(R.string.txn_search)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            singleLine = true,
        )

        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.txn_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally),
            )
        }

        val grouped = rows.groupBy { it.date }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            grouped.forEach { (date, dayRows) ->
                item(key = "h$date") {
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val net = dayRows.sumOf {
                            when (it.type) {
                                TxnType.INCOME -> it.amount
                                TxnType.EXPENSE -> -it.amount
                                TxnType.TRANSFER -> 0L
                            }
                        }
                        Text(
                            net.toVnd(),
                            style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(dayRows.size, key = { dayRows[it].id }) { i ->
                    TxnRowItem(dayRows[i], onClick = { selected = dayRows[i] })
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    selected?.let { row ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            TxnDetail(app, row, onDeleted = { selected = null })
        }
    }
}

@Composable
private fun TxnRowItem(t: TxnRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge(t.emoji ?: "↔️")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.categoryName ?: stringResource(R.string.txn_transfer), style = MaterialTheme.typography.bodyLarge)
                if (t.recurringId != null) Text(" 🔁", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                listOf(t.walletName, t.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (t.type == TxnType.INCOME) "+" else if (t.type == TxnType.EXPENSE) "-" else "") + t.amount.toVnd(),
            style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle),
            color = when (t.type) {
                TxnType.INCOME -> Primary
                TxnType.EXPENSE -> MaterialTheme.colorScheme.error
                TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun TxnDetail(app: MoneyTrackApp, row: TxnRow, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nextDate by remember { mutableStateOf<LocalDate?>(null) }
    if (row.recurringId != null) {
        androidx.compose.runtime.LaunchedEffect(row.recurringId) {
            nextDate = app.repo.dao.recurring(row.recurringId)?.nextDate
        }
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(row.emoji ?: "↔️")
            Spacer(Modifier.width(12.dp))
            Column {
                Text(row.categoryName ?: stringResource(R.string.txn_transfer), style = MaterialTheme.typography.titleMedium)
                Text(row.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(row.amount.toVnd(), style = MaterialTheme.typography.headlineSmall.merge(MoneyStyle))
        if (row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodyMedium)
        nextDate?.let {
            Text(
                "🔁 " + stringResource(R.string.txn_next_date, it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Button(
            onClick = { scope.launch { app.repo.deleteTxn(row.id); onDeleted() } },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.txn_delete)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.txn_today)
        today.minusDays(1) -> stringResource(R.string.txn_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}

package com.hoang.moneytrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.common.AddWalletForm
import com.hoang.moneytrack.ui.common.EmojiBadge
import com.hoang.moneytrack.ui.common.TieredProgressBar
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.LocalHideBalance
import com.hoang.moneytrack.ui.theme.MoneyStyle
import com.hoang.moneytrack.ui.theme.Primary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: MoneyTrackApp,
    onQuickAdd: (TxnType) -> Unit,
    onOpenReminders: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAllTxns: () -> Unit,
) {
    val dao = app.repo.dao
    val month = remember { YearMonth.now() }
    val total by dao.totalBalance().collectAsState(0L)
    val income by dao.sumByType(TxnType.INCOME, month.atDay(1), month.atEndOfMonth()).collectAsState(0L)
    val expense by dao.sumByType(TxnType.EXPENSE, month.atDay(1), month.atEndOfMonth()).collectAsState(0L)
    val wallets by dao.wallets().collectAsState(emptyList())
    val due by dao.dueReminders(LocalDate.now().plusDays(3), 2).collectAsState(emptyList())
    val recent by dao.recentTxns(5).collectAsState(emptyList())
    val budgets by dao.budgetRows(month.year * 100 + month.monthValue, month.atDay(1), month.atEndOfMonth())
        .collectAsState(emptyList())
    val hide = LocalHideBalance.current
    val scope = rememberCoroutineScope()
    var addingWallet by remember { mutableStateOf(false) }

    fun money(v: Long) = if (hide) "••••••" else v.toVnd()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(Primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("H", style = MaterialTheme.typography.titleMedium, color = Primary) }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.home_greeting, "Hoàng"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenReminders) {
                    BadgedBox(badge = { if (due.isNotEmpty()) Badge { Text("${due.size}") } }) {
                        Icon(Icons.Outlined.Notifications, stringResource(R.string.nav_reminders))
                    }
                }
                IconButton(onClick = onOpenReports) { Icon(Icons.Outlined.BarChart, stringResource(R.string.nav_reports)) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings)) }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.home_total_balance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { scope.launch { app.settings.setHideBalance(!hide) } }) {
                            Icon(
                                if (hide) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(money(total), style = MaterialTheme.typography.displaySmall.merge(MoneyStyle))
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatChip(stringResource(R.string.home_income_month), money(income), Primary, Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        StatChip(stringResource(R.string.home_expense_month), money(expense), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onQuickAdd(TxnType.INCOME) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_add_income), maxLines = 1)
                }
                OutlinedButton(onClick = { onQuickAdd(TxnType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_add_expense), maxLines = 1)
                }
                OutlinedButton(onClick = { onQuickAdd(TxnType.TRANSFER) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_transfer), maxLines = 1)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(wallets.size, key = { wallets[it].id }) { i ->
                    val w = wallets[i]
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp).width(120.dp)) {
                            Box(Modifier.size(10.dp).background(parseColor(w.colorHex), CircleShape))
                            Spacer(Modifier.height(8.dp))
                            Text(w.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(money(w.balance), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.clickable { addingWallet = true },
                    ) {
                        Column(
                            Modifier.padding(14.dp).width(60.dp).height(64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { Text("+", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        if (due.isNotEmpty()) item {
            SectionHeader(stringResource(R.string.home_upcoming), null)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                modifier = Modifier.clickable(onClick = onOpenReminders),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    due.forEach { r ->
                        Row {
                            Text("⏰ ${r.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                r.amount.toVnd() + " · " + r.dueDate.format(DateTimeFormatter.ofPattern("dd/MM")),
                                style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.home_recent), onSeeAllTxns) }
        items(recent.size, key = { recent[it].id }) { i ->
            val t = recent[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(t.emoji ?: "↔️")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.categoryName ?: stringResource(R.string.txn_transfer), style = MaterialTheme.typography.bodyLarge)
                    Text(t.walletName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    (if (t.type == TxnType.INCOME) "+" else if (t.type == TxnType.EXPENSE) "-" else "") + money(t.amount),
                    style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle),
                    color = when (t.type) {
                        TxnType.INCOME -> Primary
                        TxnType.EXPENSE -> MaterialTheme.colorScheme.error
                        TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        if (budgets.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_budget_overview), null) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    budgets.take(3).forEach { b ->
                        Column {
                            Row {
                                Text("${b.emoji} ${b.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${money(b.spent)} / ${money(b.limitAmount)}",
                                    style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            TieredProgressBar(if (b.limitAmount > 0) b.spent.toFloat() / b.limitAmount else 0f)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (addingWallet) ModalBottomSheet(onDismissRequest = { addingWallet = false }) {
        AddWalletForm(app) { addingWallet = false }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(MoneyStyle), color = color)
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text(
                stringResource(R.string.home_see_all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onSeeAll),
            )
        }
    }
}

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: IllegalArgumentException) {
    Primary
}

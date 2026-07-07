package com.hoang.moneytrack.ui.budget

import com.hoang.moneytrack.ui.common.ThousandsVisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.Budget
import com.hoang.moneytrack.data.db.SavingGoal
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.common.MonthPicker
import com.hoang.moneytrack.ui.common.TieredProgressBar
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.MoneyStyle
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(app: MoneyTrackApp) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showCreateBudget by remember { mutableStateOf(false) }
    var showCreateGoal by remember { mutableStateOf(false) }
    var depositGoal by remember { mutableStateOf<SavingGoal?>(null) }

    val budgets by remember(month) {
        app.repo.dao.budgetRows(month.year * 100 + month.monthValue, month.atDay(1), month.atEndOfMonth())
    }.collectAsState(emptyList())
    val goals by app.repo.dao.goals().collectAsState(emptyList())

    val totalLimit = budgets.sumOf { it.limitAmount }
    val totalSpent = budgets.sumOf { it.spent }
    val over80 = budgets.filter { it.limitAmount > 0 && it.spent * 100 / it.limitAmount >= 80 }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthPicker(month, { month = it }, Modifier.fillMaxWidth()) }

        if (over80.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    over80.forEach { b ->
                        Text(
                            stringResource(R.string.budget_warning, b.name, (b.spent * 100 / b.limitAmount).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    SummaryCol(stringResource(R.string.budget_total), totalLimit, Modifier.weight(1f))
                    SummaryCol(stringResource(R.string.budget_used), totalSpent, Modifier.weight(1f))
                    SummaryCol(stringResource(R.string.budget_left), totalLimit - totalSpent, Modifier.weight(1f))
                }
            }
        }

        items(budgets.size, key = { "b${budgets[it].id}" }) { i ->
            val b = budgets[i]
            Column {
                Row {
                    Text("${b.emoji} ${b.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${b.spent.toVnd()} / ${b.limitAmount.toVnd()}",
                        style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                TieredProgressBar(if (b.limitAmount > 0) b.spent.toFloat() / b.limitAmount else 0f)
            }
        }

        item {
            OutlinedButton(onClick = { showCreateBudget = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.budget_create))
            }
        }

        item { Text(stringResource(R.string.budget_goals), style = MaterialTheme.typography.titleMedium) }
        items(goals.size, key = { "g${goals[it].id}" }) { i ->
            val g = goals[i]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(g.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${g.savedAmount.toVnd()} / ${g.targetAmount.toVnd()}",
                            style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                        )
                    }
                    TieredProgressBar(if (g.targetAmount > 0) g.savedAmount.toFloat() / g.targetAmount else 0f)
                    OutlinedButton(onClick = { depositGoal = g }) { Text(stringResource(R.string.budget_goal_add)) }
                }
            }
        }
        item {
            OutlinedButton(onClick = { showCreateGoal = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.budget_goal_create))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showCreateBudget) ModalBottomSheet(onDismissRequest = { showCreateBudget = false }) {
        CreateBudgetForm(app, month) { showCreateBudget = false }
    }
    if (showCreateGoal) ModalBottomSheet(onDismissRequest = { showCreateGoal = false }) {
        CreateGoalForm(app) { showCreateGoal = false }
    }
    depositGoal?.let { goal ->
        ModalBottomSheet(onDismissRequest = { depositGoal = null }) {
            DepositForm(app, goal) { depositGoal = null }
        }
    }
}

@Composable
private fun SummaryCol(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toVnd(), style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
    }
}

@Composable
private fun CreateBudgetForm(app: MoneyTrackApp, month: YearMonth, onDone: () -> Unit) {
    val categories by app.repo.dao.categories().collectAsState(emptyList())
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var limitText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val expenseCats = categories.filter { it.type == TxnType.EXPENSE }

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.budget_create), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenseCats.size, key = { expenseCats[it].id }) { i ->
                val c = expenseCats[i]
                FilterChip(categoryId == c.id, { categoryId = c.id }, { Text("${c.emoji} ${c.name}") })
            }
        }
        OutlinedTextField(
            limitText, { limitText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.budget_limit)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = ThousandsVisualTransformation,
        )
        val limit = limitText.toLongOrNull() ?: 0L
        Button(
            onClick = {
                scope.launch {
                    app.repo.dao.insertBudget(
                        Budget(categoryId = categoryId!!, month = month.year * 100 + month.monthValue, limitAmount = limit),
                    )
                    onDone()
                }
            },
            enabled = categoryId != null && limit > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CreateGoalForm(app: MoneyTrackApp, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.budget_goal_create), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.budget_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            targetText, { targetText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.budget_target)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = ThousandsVisualTransformation,
        )
        val target = targetText.toLongOrNull() ?: 0L
        Button(
            onClick = {
                scope.launch {
                    app.repo.dao.insertGoal(SavingGoal(name = name.trim(), targetAmount = target))
                    onDone()
                }
            },
            enabled = name.isNotBlank() && target > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DepositForm(app: MoneyTrackApp, goal: SavingGoal, onDone: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val wallets by app.repo.dao.wallets().collectAsState(emptyList())
    val categories by app.repo.dao.categories().collectAsState(emptyList())
    var walletId by remember { mutableStateOf<Long?>(null) }
    if (walletId == null && wallets.isNotEmpty()) walletId = wallets.first().id
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${goal.name} — ${stringResource(R.string.budget_goal_add)}", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            amountText, { amountText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.amount)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = ThousandsVisualTransformation,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wallets.size, key = { wallets[it].id }) { i ->
                val w = wallets[i]
                FilterChip(walletId == w.id, { walletId = w.id }, { Text(w.name) })
            }
        }
        val amount = amountText.toLongOrNull() ?: 0L
        Button(
            onClick = {
                scope.launch {
                    val savingCat = categories.firstOrNull { it.name == "Tiết kiệm" }?.id
                    app.repo.addToGoal(goal.id, amount, walletId!!, savingCat)
                    onDone()
                }
            },
            enabled = amount > 0 && walletId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

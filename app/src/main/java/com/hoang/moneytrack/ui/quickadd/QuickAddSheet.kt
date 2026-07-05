package com.hoang.moneytrack.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.RecurUnit
import com.hoang.moneytrack.data.db.Recurring
import com.hoang.moneytrack.data.db.Txn
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.MoneyStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(app: MoneyTrackApp, preset: TxnType, onDone: () -> Unit) {
    var type by remember { mutableStateOf(preset) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var toWalletId by remember { mutableStateOf<Long?>(null) }
    var repeat by remember { mutableStateOf(false) }
    var repeatDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth.coerceAtMost(28)) }

    val categories by app.repo.dao.categories().collectAsState(emptyList())
    val wallets by app.repo.dao.wallets().collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    val amount = amountText.toLongOrNull() ?: 0L
    val visibleCategories = categories.filter { it.type == type }
    if (walletId == null && wallets.isNotEmpty()) walletId = wallets.first().id

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                TxnType.EXPENSE to R.string.qa_expense,
                TxnType.INCOME to R.string.qa_income,
                TxnType.TRANSFER to R.string.qa_transfer,
            ).forEachIndexed { i, (t, label) ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t; categoryId = null },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(stringResource(label)) }
            }
        }

        Text(
            if (amount > 0) amount.toVnd() else "0",
            style = MaterialTheme.typography.displaySmall.merge(MoneyStyle),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = if (type == TxnType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )

        NumberPad(onKey = { key ->
            amountText = when (key) {
                "⌫" -> amountText.dropLast(1)
                "000" -> if (amountText.isNotEmpty() && amountText.length <= 12) amountText + "000" else amountText
                else -> if (amountText.length < 12) amountText + key else amountText
            }
        })

        if (type != TxnType.TRANSFER) {
            Text(stringResource(R.string.qa_category), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleCategories.size, key = { visibleCategories[it].id }) { i ->
                    val c = visibleCategories[i]
                    FilterChip(categoryId == c.id, { categoryId = c.id }, { Text("${c.emoji} ${c.name}") })
                }
            }
        }

        Text(stringResource(R.string.qa_wallet), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wallets.size, key = { wallets[it].id }) { i ->
                val w = wallets[i]
                FilterChip(walletId == w.id, { walletId = w.id }, { Text(w.name) })
            }
        }

        if (type == TxnType.TRANSFER) {
            Text(stringResource(R.string.qa_to_wallet), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val targets = wallets.filter { it.id != walletId }
                items(targets.size, key = { targets[it].id }) { i ->
                    val w = targets[i]
                    FilterChip(toWalletId == w.id, { toWalletId = w.id }, { Text(w.name) })
                }
            }
        }

        OutlinedTextField(
            note, { note = it },
            placeholder = { Text(stringResource(R.string.qa_note)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (type != TxnType.TRANSFER) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qa_repeat), modifier = Modifier.weight(1f))
                Switch(repeat, { repeat = it })
            }
            if (repeat) {
                Text(stringResource(R.string.qa_monthly_day, repeatDay), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = repeatDay.toFloat(),
                    onValueChange = { repeatDay = it.toInt().coerceIn(1, 28) },
                    valueRange = 1f..28f,
                )
            }
        }

        val valid = amount > 0 && walletId != null &&
            (type == TxnType.TRANSFER && toWalletId != null || type != TxnType.TRANSFER && categoryId != null)
        Button(
            onClick = {
                scope.launch {
                    val today = LocalDate.now()
                    val recurring = if (repeat && type != TxnType.TRANSFER) Recurring(
                        amount = amount, type = type, categoryId = categoryId, walletId = walletId!!,
                        note = note, unit = RecurUnit.MONTHLY, dayOfUnit = repeatDay,
                        nextDate = today, // repo advances it past today on insert
                    ) else null
                    app.repo.addTxn(
                        Txn(
                            amount = amount, type = type, categoryId = categoryId,
                            walletId = walletId!!, toWalletId = toWalletId.takeIf { type == TxnType.TRANSFER },
                            note = note, date = today,
                        ),
                        recurring,
                    )
                    onDone()
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) { Text(key, style = MaterialTheme.typography.titleMedium.merge(MoneyStyle)) }
                }
            }
        }
    }
}

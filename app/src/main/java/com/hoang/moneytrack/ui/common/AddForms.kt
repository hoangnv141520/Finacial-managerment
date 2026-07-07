package com.hoang.moneytrack.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hoang.moneytrack.data.db.Category
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.data.db.Wallet
import com.hoang.moneytrack.data.db.WalletType
import kotlinx.coroutines.launch

private val walletColors = listOf("#00C896", "#3B82F6", "#F59E0B", "#8B5CF6", "#FF4757", "#14B8A6")

@Composable
fun AddWalletForm(app: MoneyTrackApp, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WalletType.BANK) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.wallet_add), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.budget_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == WalletType.CASH, { type = WalletType.CASH }, { Text(stringResource(R.string.wallet_cash)) })
            FilterChip(type == WalletType.BANK, { type = WalletType.BANK }, { Text(stringResource(R.string.wallet_bank)) })
            FilterChip(type == WalletType.EWALLET, { type = WalletType.EWALLET }, { Text(stringResource(R.string.wallet_ewallet)) })
        }
        OutlinedTextField(
            balanceText, { balanceText = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.wallet_initial_balance)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = ThousandsVisualTransformation,
        )
        Button(
            onClick = {
                scope.launch {
                    val count = app.repo.dao.walletCount()
                    app.repo.dao.insertWallet(
                        Wallet(
                            name = name.trim(), type = type,
                            balance = balanceText.toLongOrNull() ?: 0L,
                            colorHex = walletColors[count % walletColors.size],
                            sortOrder = count,
                        ),
                    )
                    onDone()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AddCategoryForm(app: MoneyTrackApp, type: TxnType, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.category_add), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.budget_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            emoji, { emoji = it.take(4) },
            label = { Text("Emoji") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    app.repo.dao.insertCategory(
                        Category(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, type = type),
                    )
                    onDone()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.qa_save)) }
        Spacer(Modifier.height(16.dp))
    }
}

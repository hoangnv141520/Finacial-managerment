package com.hoang.moneytrack.ui.settings

import com.hoang.moneytrack.ui.common.ThousandsVisualTransformation
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.Category
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.data.db.Wallet
import com.hoang.moneytrack.data.db.WalletType
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.home.parseColor
import com.hoang.moneytrack.ui.theme.MoneyStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsScreen(app: MoneyTrackApp) {
    val wallets by app.repo.dao.wallets().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Wallet?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(wallets.size, key = { wallets[it].id }) { i ->
            val w = wallets[i]
            Card(Modifier.fillMaxWidth().clickable { editing = w }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(parseColor(w.colorHex), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                when (w.type) {
                                    WalletType.CASH -> R.string.wallet_cash
                                    WalletType.BANK -> R.string.wallet_bank
                                    WalletType.EWALLET -> R.string.wallet_ewallet
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(w.balance.toVnd(), style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle))
                }
            }
        }
    }

    if (editing != null) ModalBottomSheet(onDismissRequest = { editing = null }) {
        EditWalletForm(app, editing!!) { editing = null }
    }
}

@Composable
fun EditWalletForm(app: MoneyTrackApp, wallet: Wallet, onDone: () -> Unit) {
    var name by remember { mutableStateOf(wallet.name) }
    var balanceText by remember { mutableStateOf(wallet.balance.toString()) }
    var type by remember { mutableStateOf(wallet.type) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.wallet_edit), style = MaterialTheme.typography.titleMedium)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        app.repo.dao.updateWallet(wallet.copy(name = name.trim(), type = type, balance = balanceText.toLongOrNull() ?: 0L))
                        onDone()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.qa_save)) }
            
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val count = app.repo.dao.countTxnsForWallet(wallet.id)
                        if (count > 0) {
                            Toast.makeText(ctx, ctx.getString(R.string.wallet_delete_blocked, count), Toast.LENGTH_SHORT).show()
                        } else {
                            app.repo.dao.deleteWallet(wallet)
                            onDone()
                        }
                    }
                },
            ) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(app: MoneyTrackApp) {
    val categories by app.repo.dao.categories().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Category?>(null) }
    var filterType by remember { mutableStateOf(TxnType.EXPENSE) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            FilterChip(filterType == TxnType.EXPENSE, { filterType = TxnType.EXPENSE }, { Text(stringResource(R.string.qa_expense)) })
            Spacer(Modifier.width(8.dp))
            FilterChip(filterType == TxnType.INCOME, { filterType = TxnType.INCOME }, { Text(stringResource(R.string.qa_income)) })
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val items = categories.filter { it.type == filterType }
            items(items.size, key = { items[it].id }) { i ->
                val c = items[i]
                Card(Modifier.fillMaxWidth().clickable { editing = c }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(16.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (editing != null) ModalBottomSheet(onDismissRequest = { editing = null }) {
        EditCategoryForm(app, editing!!) { editing = null }
    }
}

@Composable
fun EditCategoryForm(app: MoneyTrackApp, category: Category, onDone: () -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    var emoji by remember { mutableStateOf(category.emoji) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.category_edit), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.budget_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            emoji, { emoji = it.take(4) },
            label = { Text("Emoji") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        app.repo.dao.updateCategory(category.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }))
                        onDone()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.qa_save)) }
            
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val count = app.repo.dao.countTxnsForCategory(category.id)
                        if (count > 0) {
                            Toast.makeText(ctx, ctx.getString(R.string.category_delete_blocked, count), Toast.LENGTH_SHORT).show()
                        } else {
                            app.repo.dao.deleteCategory(category)
                            onDone()
                        }
                    }
                },
            ) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

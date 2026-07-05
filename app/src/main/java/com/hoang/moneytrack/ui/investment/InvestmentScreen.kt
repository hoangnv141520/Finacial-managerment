package com.hoang.moneytrack.ui.investment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.hoang.moneytrack.data.db.AssetType
import com.hoang.moneytrack.data.db.Holding
import com.hoang.moneytrack.ui.common.LineChart
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.MoneyStyle
import com.hoang.moneytrack.ui.theme.Primary
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun Holding.value(): Long = (quantity * currentPrice).toLong()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(app: MoneyTrackApp) {
    val holdings by app.repo.dao.holdings().collectAsState(emptyList())
    val snapshots by remember { app.repo.dao.snapshots(LocalDate.now().minusDays(30)) }.collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Holding?>(null) }
    var adding by remember { mutableStateOf(false) }

    val tabs = listOf(
        AssetType.STOCK to R.string.invest_stock,
        AssetType.GOLD to R.string.invest_gold,
        AssetType.CRYPTO to R.string.invest_crypto,
        AssetType.SAVINGS to R.string.invest_savings,
    )
    val totalValue = holdings.sumOf { it.value() }
    val totalCost = holdings.sumOf { it.costBasis }
    val pnl = totalValue - totalCost
    val pnlColor = if (pnl >= 0) Primary else MaterialTheme.colorScheme.error

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.invest_total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(totalValue.toVnd(), style = MaterialTheme.typography.headlineSmall.merge(MoneyStyle))
                    val pct = if (totalCost > 0) pnl * 100.0 / totalCost else 0.0
                    Text(
                        (if (pnl >= 0) "+" else "") + pnl.toVnd() + String.format(" (%.1f%%)", pct),
                        style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                        color = pnlColor,
                    )
                }
            }
        }

        if (snapshots.size >= 2) item {
            LineChart(snapshots.map { it.totalValue }, Primary)
        }

        item {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, (_, label) ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(label), maxLines = 1) })
                }
            }
        }

        val visible = holdings.filter { it.assetType == tabs[tab].first }
        if (visible.isEmpty()) item {
            Text(
                stringResource(R.string.invest_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(visible.size, key = { visible[it].id }) { i ->
            val h = visible[i]
            val gain = h.value() - h.costBasis
            val pct = if (h.costBasis > 0) gain * 100.0 / h.costBasis else 0.0
            Row(
                Modifier.fillMaxWidth().clickable { editing = h },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(h.symbol, style = MaterialTheme.typography.titleSmall.merge(MoneyStyle))
                    Text(
                        "${stringResource(R.string.invest_qty)} ${h.quantity} · ${stringResource(R.string.invest_cost)} ${h.costBasis.toVnd()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(h.value().toVnd(), style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle))
                    Text(
                        String.format("%+.1f%%", pct),
                        style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                        color = if (gain >= 0) Primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.invest_add))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (adding) ModalBottomSheet(onDismissRequest = { adding = false }) {
        HoldingForm(app, null, tabs[tab].first) { adding = false }
    }
    editing?.let { h ->
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            HoldingForm(app, h, h.assetType) { editing = null }
        }
    }
}

@Composable
private fun HoldingForm(app: MoneyTrackApp, existing: Holding?, assetType: AssetType, onDone: () -> Unit) {
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var qty by remember { mutableStateOf(existing?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(existing?.costBasis?.toString() ?: "") }
    var price by remember { mutableStateOf(existing?.currentPrice?.toString() ?: "") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(existing?.symbol ?: stringResource(R.string.invest_add), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(symbol, { symbol = it }, label = { Text("Symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qty, { qty = it }, label = { Text(stringResource(R.string.invest_qty)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.invest_cost)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.invest_price)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        val valid = symbol.isNotBlank() && qty.toDoubleOrNull() != null && cost.toLongOrNull() != null && price.toLongOrNull() != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val h = Holding(
                            id = existing?.id ?: 0, assetType = assetType, symbol = symbol.trim(),
                            quantity = qty.toDouble(), costBasis = cost.toLong(), currentPrice = price.toLong(),
                        )
                        if (existing == null) app.repo.dao.insertHolding(h) else app.repo.dao.updateHolding(h)
                        onDone()
                    }
                },
                enabled = valid,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.qa_save)) }
            if (existing != null) {
                Button(
                    onClick = { scope.launch { app.repo.dao.deleteHolding(existing); onDone() } },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.txn_delete)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

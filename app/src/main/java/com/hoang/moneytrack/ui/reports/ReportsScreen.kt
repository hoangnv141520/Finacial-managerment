package com.hoang.moneytrack.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.common.DonutChart
import com.hoang.moneytrack.ui.common.LineChart
import com.hoang.moneytrack.ui.common.PairedBarChart
import com.hoang.moneytrack.ui.common.toVnd
import com.hoang.moneytrack.ui.theme.Amber
import com.hoang.moneytrack.ui.theme.ChartBlue
import com.hoang.moneytrack.ui.theme.ChartViolet
import com.hoang.moneytrack.ui.theme.MoneyStyle
import com.hoang.moneytrack.ui.theme.Primary
import java.time.LocalDate

private enum class Period { WEEK, MONTH, YEAR }

private fun Period.range(end: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
    Period.WEEK -> end.minusDays(6) to end
    Period.MONTH -> end.withDayOfMonth(1) to end
    Period.YEAR -> end.withDayOfYear(1) to end
}

private fun Period.previousRange(end: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
    Period.WEEK -> end.minusDays(13) to end.minusDays(7)
    Period.MONTH -> end.minusMonths(1).withDayOfMonth(1) to end.minusMonths(1).let { it.withDayOfMonth(it.lengthOfMonth()) }
    Period.YEAR -> end.minusYears(1).withDayOfYear(1) to end.minusYears(1).let { it.withDayOfYear(it.lengthOfYear()) }
}

@Composable
fun ReportsScreen(app: MoneyTrackApp) {
    var tab by remember { mutableIntStateOf(1) }
    val period = Period.entries[tab]
    val today = LocalDate.now()
    val (from, to) = period.range(today)
    val (prevFrom, prevTo) = period.previousRange(today)

    val income by app.repo.dao.sumByType(TxnType.INCOME, from, to).collectAsState(0L)
    val expense by app.repo.dao.sumByType(TxnType.EXPENSE, from, to).collectAsState(0L)
    val byCategory by app.repo.dao.expenseByCategory(from, to).collectAsState(emptyList())

    var prevIncome by remember { mutableStateOf(0L) }
    var prevExpense by remember { mutableStateOf(0L) }
    var dailyNet by remember { mutableStateOf(listOf<Long>()) }
    var bars by remember { mutableStateOf(Pair(listOf<Long>(), listOf<Long>())) }

    LaunchedEffect(period) {
        prevIncome = app.repo.dao.sumByTypeOnce(TxnType.INCOME, prevFrom, prevTo)
        prevExpense = app.repo.dao.sumByTypeOnce(TxnType.EXPENSE, prevFrom, prevTo)

        // cumulative savings line over the period
        val days = app.repo.dao.netByDay(from, to)
        var acc = 0L
        dailyNet = days.map { acc += it.total; acc }

        // bar buckets: WEEK=7 days, MONTH=weeks, YEAR=months (aggregated in SQL by day, bucketed here)
        val buckets = when (period) {
            Period.WEEK -> 7
            Period.MONTH -> 5
            Period.YEAR -> 12
        }
        val inc = MutableList(buckets) { 0L }
        val exp = MutableList(buckets) { 0L }
        val perDay = app.repo.dao.netByDay(from, to) // net; need separate — reuse via two sums below
        // simple: bucket by fraction of range
        val totalDays = (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(1)
        // fetch raw txns per day via netByDay is net-only; income/expense split needs the rows —
        // ponytail: reuse txnRows once for the period, bucket in memory (fine for personal data volume)
        app.repo.dao.txnRows(from, to, null, "").collect { rows ->
            inc.fill(0); exp.fill(0)
            rows.forEach { r ->
                val idx = ((r.date.toEpochDay() - from.toEpochDay()) * buckets / totalDays).toInt().coerceIn(0, buckets - 1)
                when (r.type) {
                    TxnType.INCOME -> inc[idx] += r.amount
                    TxnType.EXPENSE -> exp[idx] += r.amount
                    TxnType.TRANSFER -> {}
                }
            }
            bars = inc.toList() to exp.toList()
        }
    }

    val palette = listOf(Primary, MaterialTheme.colorScheme.error, ChartBlue, Amber, ChartViolet)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.rep_week, R.string.rep_month, R.string.rep_year).forEachIndexed { i, label ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(label)) })
                }
            }
        }

        item {
            Text(stringResource(R.string.rep_income_vs_expense), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PairedBarChart(bars.first, bars.second, Primary, MaterialTheme.colorScheme.error)
        }

        if (byCategory.isNotEmpty()) item {
            Text(stringResource(R.string.rep_by_category), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DonutChart(byCategory.take(5).mapIndexed { i, c -> c.total to palette[i % palette.size] })
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byCategory.take(5).forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(palette[i % palette.size], CircleShape))
                        Text("  ${c.emoji} ${c.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(c.total.toVnd(), style = MaterialTheme.typography.bodySmall.merge(MoneyStyle))
                    }
                }
            }
        }

        if (dailyNet.size >= 2) item {
            Text(stringResource(R.string.rep_savings), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LineChart(dailyNet, Primary)
        }

        item {
            Text(stringResource(R.string.rep_compare), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompareRow(stringResource(R.string.home_income_month), income, prevIncome, higherIsBetter = true)
                    CompareRow(stringResource(R.string.home_expense_month), expense, prevExpense, higherIsBetter = false)
                    CompareRow(stringResource(R.string.rep_savings_label), income - expense, prevIncome - prevExpense, higherIsBetter = true)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CompareRow(label: String, current: Long, previous: Long, higherIsBetter: Boolean) {
    val delta = current - previous
    val pct = if (previous != 0L) delta * 100.0 / previous else 0.0
    val good = if (higherIsBetter) delta >= 0 else delta <= 0
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(current.toVnd(), style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle))
            Text(
                String.format("%+.0f%%", pct),
                style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                color = if (good) Primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

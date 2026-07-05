package com.hoang.moneytrack.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.TxnType
import com.hoang.moneytrack.ui.budget.BudgetScreen
import com.hoang.moneytrack.ui.home.HomeScreen
import com.hoang.moneytrack.ui.investment.InvestmentScreen
import com.hoang.moneytrack.ui.quickadd.QuickAddSheet
import com.hoang.moneytrack.ui.reminders.RemindersScreen
import com.hoang.moneytrack.ui.reports.ReportsScreen
import com.hoang.moneytrack.ui.settings.SettingsScreen
import com.hoang.moneytrack.ui.transactions.TransactionsScreen

object Routes {
    const val HOME = "home"
    const val TXNS = "txns"
    const val BUDGET = "budget"
    const val INVEST = "invest"
    const val REMINDERS = "reminders"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(app: MoneyTrackApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var quickAddType by remember { mutableStateOf<TxnType?>(null) }

    val tabs = listOf(
        Triple(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
        Triple(Routes.TXNS, R.string.nav_transactions, Icons.Outlined.SwapHoriz),
        Triple(Routes.BUDGET, R.string.nav_budget, Icons.Outlined.PieChart),
        Triple(Routes.INVEST, R.string.nav_investment, Icons.Outlined.TrendingUp),
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, (r, label, icon) ->
                    if (i == 2) {
                        // gap under the FAB
                        NavigationBarItem(selected = false, onClick = {}, enabled = false, icon = {})
                    }
                    NavigationBarItem(
                        selected = route == r,
                        onClick = {
                            nav.navigate(r) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { quickAddType = TxnType.EXPENSE },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Filled.Add, null) }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    app,
                    onQuickAdd = { quickAddType = it },
                    onOpenReminders = { nav.navigate(Routes.REMINDERS) },
                    onOpenReports = { nav.navigate(Routes.REPORTS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onSeeAllTxns = { nav.navigate(Routes.TXNS) },
                )
            }
            composable(Routes.TXNS) { TransactionsScreen(app) }
            composable(Routes.BUDGET) { BudgetScreen(app) }
            composable(Routes.INVEST) { InvestmentScreen(app) }
            composable(Routes.REMINDERS) { RemindersScreen(app) }
            composable(Routes.REPORTS) { ReportsScreen(app) }
            composable(Routes.SETTINGS) { SettingsScreen(app) }
        }
    }

    quickAddType?.let { preset ->
        ModalBottomSheet(onDismissRequest = { quickAddType = null }) {
            QuickAddSheet(app, preset = preset, onDone = { quickAddType = null })
        }
    }
}

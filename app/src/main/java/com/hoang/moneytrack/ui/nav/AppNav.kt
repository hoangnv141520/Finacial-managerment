package com.hoang.moneytrack.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
import com.hoang.moneytrack.ui.settings.ManageWalletsScreen
import com.hoang.moneytrack.ui.settings.ManageCategoriesScreen
import com.hoang.moneytrack.ui.transactions.TransactionsScreen

object Routes {
    const val HOME = "home"
    const val TXNS = "txns"
    const val BUDGET = "budget"
    const val INVEST = "invest"
    const val REMINDERS = "reminders"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val MANAGE_WALLETS = "manage_wallets"
    const val MANAGE_CATEGORIES = "manage_categories"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(app: MoneyTrackApp) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var quickAddType by remember { mutableStateOf<TxnType?>(null) }

    val left = listOf(
        Triple(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
        Triple(Routes.TXNS, R.string.nav_transactions, Icons.Outlined.SwapHoriz),
    )
    val right = listOf(
        Triple(Routes.BUDGET, R.string.nav_budget, Icons.Outlined.PieChart),
        Triple(Routes.INVEST, R.string.nav_investment, Icons.Outlined.TrendingUp),
    )

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                left.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { goTab(r) },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                    )
                }
                // center "+" lives inside the bar — no floating FAB overlapping tabs
                NavigationBarItem(
                    selected = false,
                    onClick = { quickAddType = TxnType.EXPENSE },
                    icon = {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary) }
                    },
                )
                right.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { goTab(r) },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    app,
                    onQuickAdd = { quickAddType = it },
                    onOpenReminders = { nav.navigate(Routes.REMINDERS) },
                    onOpenReports = { nav.navigate(Routes.REPORTS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onSeeAllTxns = { goTab(Routes.TXNS) },
                )
            }
            composable(Routes.TXNS) { TransactionsScreen(app) }
            composable(Routes.BUDGET) { BudgetScreen(app) }
            composable(Routes.INVEST) { InvestmentScreen(app) }
            composable(Routes.REMINDERS) {
                SubScreen(stringResource(R.string.nav_reminders), nav) { RemindersScreen(app) }
            }
            composable(Routes.REPORTS) {
                SubScreen(stringResource(R.string.nav_reports), nav) { ReportsScreen(app) }
            }
            composable(Routes.SETTINGS) {
                SubScreen(stringResource(R.string.nav_settings), nav) { 
                    SettingsScreen(
                        app, 
                        onManageWallets = { nav.navigate(Routes.MANAGE_WALLETS) }, 
                        onManageCategories = { nav.navigate(Routes.MANAGE_CATEGORIES) }
                    ) 
                }
            }
            composable(Routes.MANAGE_WALLETS) {
                SubScreen(stringResource(R.string.manage_wallets), nav) { ManageWalletsScreen(app) }
            }
            composable(Routes.MANAGE_CATEGORIES) {
                SubScreen(stringResource(R.string.manage_categories), nav) { ManageCategoriesScreen(app) }
            }
        }
    }

    quickAddType?.let { preset ->
        ModalBottomSheet(onDismissRequest = { quickAddType = null }) {
            QuickAddSheet(app, preset = preset, onDone = { quickAddType = null })
        }
    }
}

/** Header with a back arrow for screens reached from Home icons. */
@Composable
private fun SubScreen(title: String, nav: NavHostController, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}

package com.hoang.moneytrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hoang.moneytrack.data.security.DbKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.LocalDate

@Database(
    entities = [
        Wallet::class, Category::class, Txn::class, Recurring::class,
        Budget::class, SavingGoal::class, Reminder::class, Holding::class,
        PortfolioSnapshot::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val key = DbKeyManager(context).getOrCreateDbKey()
            val db = Room.databaseBuilder(context, AppDatabase::class.java, "moneytrack.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(object : Callback() {
                    override fun onCreate(sqlite: SupportSQLiteDatabase) {
                        CoroutineScope(Dispatchers.IO).launch { seed(get(context).dao()) }
                    }
                })
                .build()
            return db
        }
    }
}

private suspend fun seed(dao: FinanceDao) {
    if (dao.walletCount() > 0) return
    val cash = dao.insertWallet(Wallet(name = "Tiền mặt", type = WalletType.CASH, balance = 2_500_000, colorHex = "#F59E0B", sortOrder = 0))
    val vcb = dao.insertWallet(Wallet(name = "VCB", type = WalletType.BANK, balance = 15_300_000, colorHex = "#00C896", sortOrder = 1))
    dao.insertWallet(Wallet(name = "Techcombank", type = WalletType.BANK, balance = 8_700_000, colorHex = "#3B82F6", sortOrder = 2))
    val momo = dao.insertWallet(Wallet(name = "MoMo", type = WalletType.EWALLET, balance = 1_200_000, colorHex = "#8B5CF6", sortOrder = 3))

    val food = dao.insertCategory(Category(name = "Ăn uống", emoji = "🍜", type = TxnType.EXPENSE))
    val move = dao.insertCategory(Category(name = "Di chuyển", emoji = "🚗", type = TxnType.EXPENSE))
    val home = dao.insertCategory(Category(name = "Nhà ở", emoji = "🏠", type = TxnType.EXPENSE))
    val health = dao.insertCategory(Category(name = "Sức khỏe", emoji = "💊", type = TxnType.EXPENSE))
    val shop = dao.insertCategory(Category(name = "Mua sắm", emoji = "🛍️", type = TxnType.EXPENSE))
    val salary = dao.insertCategory(Category(name = "Lương", emoji = "💰", type = TxnType.INCOME))
    val invest = dao.insertCategory(Category(name = "Đầu tư", emoji = "📈", type = TxnType.INCOME))
    dao.insertCategory(Category(name = "Tiết kiệm", emoji = "🐷", type = TxnType.EXPENSE))

    val today = LocalDate.now()
    val recurringSalary = dao.insertRecurring(
        Recurring(
            amount = 25_000_000, type = TxnType.INCOME, categoryId = salary, walletId = vcb,
            note = "Lương tháng", unit = RecurUnit.MONTHLY, dayOfUnit = 5,
            nextDate = today.withDayOfMonth(5).let { if (it.isAfter(today)) it else it.plusMonths(1) },
        )
    )

    // ~30 sample txns over last 2 months — deterministic, no Random
    val expenseCats = listOf(food, move, home, health, shop)
    val amounts = listOf(85_000L, 45_000L, 3_500_000L, 250_000L, 620_000L)
    val notes = listOf("Bún chả", "Grab", "Tiền nhà", "Thuốc", "Áo khoác")
    var i = 0
    for (weeksAgo in 0..8) {
        for (j in 0..2) {
            val idx = i % expenseCats.size
            dao.insertTxn(
                Txn(
                    amount = amounts[idx] + (i % 4) * 15_000, type = TxnType.EXPENSE,
                    categoryId = expenseCats[idx],
                    walletId = if (i % 3 == 0) cash else if (i % 3 == 1) vcb else momo,
                    note = notes[idx], date = today.minusWeeks(weeksAgo.toLong()).minusDays(j.toLong() * 2),
                )
            )
            i++
        }
    }
    dao.insertTxn(Txn(amount = 25_000_000, type = TxnType.INCOME, categoryId = salary, walletId = vcb, note = "Lương tháng", date = today.withDayOfMonth(5), recurringId = recurringSalary))
    dao.insertTxn(Txn(amount = 1_200_000, type = TxnType.INCOME, categoryId = invest, walletId = vcb, note = "Cổ tức", date = today.minusDays(12)))

    val month = today.year * 100 + today.monthValue
    dao.insertBudget(Budget(categoryId = food, month = month, limitAmount = 3_000_000))
    dao.insertBudget(Budget(categoryId = move, month = month, limitAmount = 1_000_000))
    dao.insertBudget(Budget(categoryId = shop, month = month, limitAmount = 2_000_000))

    dao.insertGoal(SavingGoal(name = "Mua xe", targetAmount = 100_000_000, savedAmount = 50_000_000))
    dao.insertGoal(SavingGoal(name = "Quỹ khẩn cấp", targetAmount = 30_000_000, savedAmount = 20_000_000))

    dao.insertReminder(Reminder(kind = ReminderKind.BILL, title = "Điện", amount = 450_000, dueDate = today.withDayOfMonth(10).let { if (it.isBefore(today)) it.plusMonths(1) else it }, recurDayOfMonth = 10))
    dao.insertReminder(Reminder(kind = ReminderKind.BILL, title = "Internet", amount = 220_000, dueDate = today.withDayOfMonth(15).let { if (it.isBefore(today)) it.plusMonths(1) else it }, recurDayOfMonth = 15))
    dao.insertReminder(Reminder(kind = ReminderKind.DEBT_LEND, title = "Cho Minh vay", amount = 2_000_000, dueDate = LocalDate.of(today.year, 7, 30)))

    dao.insertHolding(Holding(assetType = AssetType.STOCK, symbol = "FPT", quantity = 100.0, costBasis = 9_500_000, currentPrice = 132_000))
    dao.insertHolding(Holding(assetType = AssetType.STOCK, symbol = "VNM", quantity = 200.0, costBasis = 13_000_000, currentPrice = 61_500))
    dao.insertHolding(Holding(assetType = AssetType.GOLD, symbol = "SJC (chỉ)", quantity = 2.0, costBasis = 15_000_000, currentPrice = 8_200_000))
    dao.insertHolding(Holding(assetType = AssetType.CRYPTO, symbol = "BTC", quantity = 0.012, costBasis = 28_000_000, currentPrice = 2_600_000_000))
    dao.insertHolding(Holding(assetType = AssetType.SAVINGS, symbol = "TCB 6 tháng", quantity = 1.0, costBasis = 50_000_000, currentPrice = 51_200_000))
}

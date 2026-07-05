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
    dao.insertWallet(Wallet(name = "Tiền mặt", type = WalletType.CASH, balance = 0, colorHex = "#F59E0B", sortOrder = 0))
    listOf(
        Category(name = "Ăn uống", emoji = "🍜", type = TxnType.EXPENSE),
        Category(name = "Di chuyển", emoji = "🚗", type = TxnType.EXPENSE),
        Category(name = "Nhà ở", emoji = "🏠", type = TxnType.EXPENSE),
        Category(name = "Sức khỏe", emoji = "💊", type = TxnType.EXPENSE),
        Category(name = "Mua sắm", emoji = "🛍️", type = TxnType.EXPENSE),
        Category(name = "Tiết kiệm", emoji = "🐷", type = TxnType.EXPENSE),
        Category(name = "Lương", emoji = "💰", type = TxnType.INCOME),
        Category(name = "Đầu tư", emoji = "📈", type = TxnType.INCOME),
    ).forEach { dao.insertCategory(it) }
}

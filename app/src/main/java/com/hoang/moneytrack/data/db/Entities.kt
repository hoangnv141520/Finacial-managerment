package com.hoang.moneytrack.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

enum class WalletType { CASH, BANK, EWALLET }
enum class TxnType { INCOME, EXPENSE, TRANSFER }
enum class RecurUnit { MONTHLY, WEEKLY }
enum class ReminderKind { BILL, DEBT_LEND, DEBT_BORROW }
enum class AssetType { STOCK, GOLD, CRYPTO, SAVINGS }

@Entity
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    val balance: Long,
    val colorHex: String,
    val sortOrder: Int,
)

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val type: TxnType,
)

@Entity(
    tableName = "txn",
    indices = [Index("date"), Index("categoryId"), Index("walletId")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long, // đồng, always positive; sign decided by type
    val type: TxnType,
    val categoryId: Long?,
    val walletId: Long,
    val toWalletId: Long? = null, // TRANSFER only
    val note: String = "",
    val date: LocalDate,
    val recurringId: Long? = null,
)

@Entity
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TxnType,
    val categoryId: Long?,
    val walletId: Long,
    val note: String,
    val unit: RecurUnit,
    val dayOfUnit: Int, // MONTHLY: 1..28, WEEKLY: 1..7 (ISO)
    val nextDate: LocalDate,
    val active: Boolean = true,
)

@Entity(indices = [Index("month")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val month: Int, // yyyy*100+mm, e.g. 202607
    val limitAmount: Long,
)

@Entity
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val savedAmount: Long = 0,
    val deadline: LocalDate? = null,
)

@Entity(indices = [Index("dueDate")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ReminderKind,
    val title: String,
    val amount: Long,
    val dueDate: LocalDate,
    val paid: Boolean = false,
    val paidAmount: Long = 0, // debt installment progress
    val recurDayOfMonth: Int? = null, // recurring bill: day of month
)

@Entity
data class Holding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetType: AssetType,
    val symbol: String,
    val quantity: Double, // units/shares — not money
    val costBasis: Long, // total cost, đồng
    val currentPrice: Long, // price per unit, đồng
)

@Entity
data class PortfolioSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val totalValue: Long,
)

class Converters {
    @TypeConverter fun fromDate(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
}

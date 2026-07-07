package com.hoang.moneytrack.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

// --- LocalDate serializer (epoch-day Long) ---
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeLong(value.toEpochDay())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.ofEpochDay(decoder.decodeLong())
}

object LocalDateNullableSerializer : KSerializer<LocalDate?> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateNullable", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: LocalDate?) =
        if (value == null) encoder.encodeLong(-1L) else encoder.encodeLong(value.toEpochDay())
    override fun deserialize(decoder: Decoder): LocalDate? =
        decoder.decodeLong().let { if (it == -1L) null else LocalDate.ofEpochDay(it) }
}

@Serializable enum class WalletType { CASH, BANK, EWALLET }
@Serializable enum class TxnType { INCOME, EXPENSE, TRANSFER }
@Serializable enum class RecurUnit { MONTHLY, WEEKLY }
@Serializable enum class ReminderKind { BILL, DEBT_LEND, DEBT_BORROW }
@Serializable enum class AssetType { STOCK, GOLD, CRYPTO, SAVINGS }

@Serializable
@Entity
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    val balance: Long,
    val colorHex: String,
    val sortOrder: Int,
)

@Serializable
@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val type: TxnType,
)

@Serializable
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
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val recurringId: Long? = null,
)

@Serializable
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
    @Serializable(with = LocalDateSerializer::class) val nextDate: LocalDate,
    val active: Boolean = true,
)

@Serializable
@Entity(indices = [Index("month")])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val month: Int, // yyyy*100+mm, e.g. 202607
    val limitAmount: Long,
)

@Serializable
@Entity
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val savedAmount: Long = 0,
    @Serializable(with = LocalDateNullableSerializer::class) val deadline: LocalDate? = null,
)

@Serializable
@Entity(indices = [Index("dueDate")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ReminderKind,
    val title: String,
    val amount: Long,
    @Serializable(with = LocalDateSerializer::class) val dueDate: LocalDate,
    val paid: Boolean = false,
    val paidAmount: Long = 0, // debt installment progress
    val recurDayOfMonth: Int? = null, // recurring bill: day of month
)

@Serializable
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

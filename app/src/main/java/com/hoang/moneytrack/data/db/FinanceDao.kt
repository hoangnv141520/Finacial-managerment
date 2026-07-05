package com.hoang.moneytrack.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TxnRow(
    val id: Long,
    val amount: Long,
    val type: TxnType,
    val date: LocalDate,
    val note: String,
    val categoryId: Long?,
    val categoryName: String?,
    val emoji: String?,
    val walletId: Long,
    val walletName: String,
    val toWalletId: Long?,
    val recurringId: Long?,
)

data class CategorySum(val categoryId: Long, val name: String, val emoji: String, val total: Long)
data class DaySum(val day: Long, val total: Long)
data class BudgetRow(
    val id: Long,
    val categoryId: Long,
    val limitAmount: Long,
    val name: String,
    val emoji: String,
    val spent: Long,
)

@Dao
interface FinanceDao {
    // --- wallets ---
    @Query("SELECT * FROM Wallet ORDER BY sortOrder")
    fun wallets(): Flow<List<Wallet>>

    @Query("SELECT COALESCE(SUM(balance),0) FROM Wallet")
    fun totalBalance(): Flow<Long>

    @Insert suspend fun insertWallet(w: Wallet): Long
    @Update suspend fun updateWallet(w: Wallet)

    @Query("UPDATE Wallet SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Long)

    // --- categories ---
    @Query("SELECT * FROM Category ORDER BY id")
    fun categories(): Flow<List<Category>>

    @Insert suspend fun insertCategory(c: Category): Long

    // --- transactions ---
    @Insert suspend fun insertTxn(t: Txn): Long
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun txn(id: Long): Txn?
    @Query("DELETE FROM txn WHERE id = :id") suspend fun deleteTxn(id: Long)

    @Query(
        """SELECT t.id, t.amount, t.type, t.date, t.note, t.categoryId, t.toWalletId, t.recurringId,
                  c.name AS categoryName, c.emoji AS emoji, t.walletId, w.name AS walletName
           FROM txn t LEFT JOIN Category c ON c.id = t.categoryId JOIN Wallet w ON w.id = t.walletId
           WHERE t.date BETWEEN :from AND :to
             AND (:type IS NULL OR t.type = :type)
             AND (:query = '' OR t.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%')
           ORDER BY t.date DESC, t.id DESC"""
    )
    fun txnRows(from: LocalDate, to: LocalDate, type: TxnType?, query: String): Flow<List<TxnRow>>

    @Query(
        """SELECT t.id, t.amount, t.type, t.date, t.note, t.categoryId, t.toWalletId, t.recurringId,
                  c.name AS categoryName, c.emoji AS emoji, t.walletId, w.name AS walletName
           FROM txn t LEFT JOIN Category c ON c.id = t.categoryId JOIN Wallet w ON w.id = t.walletId
           ORDER BY t.date DESC, t.id DESC LIMIT :limit"""
    )
    fun recentTxns(limit: Int): Flow<List<TxnRow>>

    @Query("SELECT COALESCE(SUM(amount),0) FROM txn WHERE type = :type AND date BETWEEN :from AND :to")
    fun sumByType(type: TxnType, from: LocalDate, to: LocalDate): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM txn WHERE type = :type AND date BETWEEN :from AND :to")
    suspend fun sumByTypeOnce(type: TxnType, from: LocalDate, to: LocalDate): Long

    @Query(
        """SELECT c.id AS categoryId, c.name, c.emoji, SUM(t.amount) AS total
           FROM txn t JOIN Category c ON c.id = t.categoryId
           WHERE t.type = 'EXPENSE' AND t.date BETWEEN :from AND :to
           GROUP BY c.id ORDER BY total DESC"""
    )
    fun expenseByCategory(from: LocalDate, to: LocalDate): Flow<List<CategorySum>>

    @Query(
        """SELECT date AS day,
                  SUM(CASE WHEN type='INCOME' THEN amount WHEN type='EXPENSE' THEN -amount ELSE 0 END) AS total
           FROM txn WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"""
    )
    suspend fun netByDay(from: LocalDate, to: LocalDate): List<DaySum>

    // --- budgets ---
    @Query(
        """SELECT b.id, b.categoryId, b.limitAmount, c.name, c.emoji,
                  COALESCE((SELECT SUM(t.amount) FROM txn t
                            WHERE t.categoryId = b.categoryId AND t.type='EXPENSE'
                              AND t.date BETWEEN :from AND :to), 0) AS spent
           FROM Budget b JOIN Category c ON c.id = b.categoryId
           WHERE b.month = :month ORDER BY spent * 1.0 / b.limitAmount DESC"""
    )
    fun budgetRows(month: Int, from: LocalDate, to: LocalDate): Flow<List<BudgetRow>>

    @Insert suspend fun insertBudget(b: Budget): Long
    @Query("DELETE FROM Budget WHERE id = :id") suspend fun deleteBudget(id: Long)

    // --- goals ---
    @Query("SELECT * FROM SavingGoal ORDER BY id")
    fun goals(): Flow<List<SavingGoal>>

    @Insert suspend fun insertGoal(g: SavingGoal): Long
    @Query("UPDATE SavingGoal SET savedAmount = savedAmount + :delta WHERE id = :id")
    suspend fun addToGoal(id: Long, delta: Long)

    // --- reminders ---
    @Query("SELECT * FROM Reminder ORDER BY paid, dueDate")
    fun reminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder WHERE paid = 0 AND dueDate <= :until ORDER BY dueDate LIMIT :limit")
    fun dueReminders(until: LocalDate, limit: Int): Flow<List<Reminder>>

    @Query("SELECT * FROM Reminder WHERE paid = 0 AND dueDate <= :until")
    suspend fun dueRemindersOnce(until: LocalDate): List<Reminder>

    @Insert suspend fun insertReminder(r: Reminder): Long
    @Update suspend fun updateReminder(r: Reminder)
    @Query("SELECT * FROM Reminder WHERE id = :id") suspend fun reminder(id: Long): Reminder?
    @Delete suspend fun deleteReminder(r: Reminder)

    // --- recurring ---
    @Query("SELECT * FROM Recurring WHERE active = 1 AND nextDate <= :today")
    suspend fun dueRecurring(today: LocalDate): List<Recurring>

    @Query("SELECT * FROM Recurring WHERE id = :id") suspend fun recurring(id: Long): Recurring?
    @Insert suspend fun insertRecurring(r: Recurring): Long
    @Update suspend fun updateRecurring(r: Recurring)

    // --- holdings ---
    @Query("SELECT * FROM Holding ORDER BY assetType, symbol")
    fun holdings(): Flow<List<Holding>>

    @Query("SELECT * FROM Holding")
    suspend fun holdingsOnce(): List<Holding>

    @Insert suspend fun insertHolding(h: Holding): Long
    @Update suspend fun updateHolding(h: Holding)
    @Delete suspend fun deleteHolding(h: Holding)

    @Query("SELECT * FROM PortfolioSnapshot WHERE date >= :from ORDER BY date")
    fun snapshots(from: LocalDate): Flow<List<PortfolioSnapshot>>

    @Query("SELECT COUNT(*) FROM PortfolioSnapshot WHERE date = :date")
    suspend fun snapshotCount(date: LocalDate): Int

    @Insert suspend fun insertSnapshot(s: PortfolioSnapshot)

    // --- seed check ---
    @Query("SELECT COUNT(*) FROM Wallet") suspend fun walletCount(): Int
}

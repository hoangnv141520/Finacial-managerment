package com.hoang.moneytrack.data

import androidx.room.withTransaction
import com.hoang.moneytrack.data.db.AppDatabase
import com.hoang.moneytrack.data.db.Recurring
import com.hoang.moneytrack.data.db.RecurUnit
import com.hoang.moneytrack.data.db.Reminder
import com.hoang.moneytrack.data.db.Txn
import com.hoang.moneytrack.data.db.TxnType
import java.time.LocalDate

/** All money mutations go through here — balance updates share a transaction with the txn write. */
class FinanceRepository(private val db: AppDatabase) {
    val dao = db.dao()

    suspend fun addTxn(txn: Txn, recurring: Recurring? = null): Long = db.withTransaction {
        val recurringId = recurring?.let { dao.insertRecurring(it.copy(nextDate = advance(it.nextDate, it.unit, it.dayOfUnit))) }
        val id = dao.insertTxn(txn.copy(recurringId = recurringId ?: txn.recurringId))
        applyBalance(txn, +1)
        id
    }

    suspend fun deleteTxn(id: Long) = db.withTransaction {
        val txn = dao.txn(id) ?: return@withTransaction
        dao.deleteTxn(id)
        applyBalance(txn, -1)
    }

    /** Mark a reminder paid: creates the expense txn; recurring bills roll to next month. */
    suspend fun payReminder(id: Long, walletId: Long, categoryId: Long?) = db.withTransaction {
        val r = dao.reminder(id) ?: return@withTransaction
        val txn = Txn(
            amount = r.amount, type = TxnType.EXPENSE, categoryId = categoryId,
            walletId = walletId, note = r.title, date = LocalDate.now(),
        )
        dao.insertTxn(txn)
        applyBalance(txn, +1)
        if (r.recurDayOfMonth != null) {
            dao.updateReminder(r.copy(dueDate = r.dueDate.plusMonths(1).withDayOfMonth(r.recurDayOfMonth)))
        } else {
            dao.updateReminder(r.copy(paid = true, paidAmount = r.amount))
        }
    }

    suspend fun recordDebtPayment(reminder: Reminder, amount: Long) {
        val paid = (reminder.paidAmount + amount).coerceAtMost(reminder.amount)
        dao.updateReminder(reminder.copy(paidAmount = paid, paid = paid >= reminder.amount))
    }

    suspend fun addToGoal(goalId: Long, amount: Long, walletId: Long, savingCategoryId: Long?) =
        db.withTransaction {
            val txn = Txn(
                amount = amount, type = TxnType.EXPENSE, categoryId = savingCategoryId,
                walletId = walletId, note = "Tiết kiệm", date = LocalDate.now(),
            )
            dao.insertTxn(txn)
            applyBalance(txn, +1)
            dao.addToGoal(goalId, amount)
        }

    /** Generate txns for every recurring rule whose nextDate <= today. Idempotent per day. */
    suspend fun runRecurring(today: LocalDate = LocalDate.now()) = db.withTransaction {
        for (r in dao.dueRecurring(today)) {
            var next = r.nextDate
            while (!next.isAfter(today)) {
                val txn = Txn(
                    amount = r.amount, type = r.type, categoryId = r.categoryId,
                    walletId = r.walletId, note = r.note, date = next, recurringId = r.id,
                )
                dao.insertTxn(txn)
                applyBalance(txn, +1)
                next = advance(next, r.unit, r.dayOfUnit)
            }
            dao.updateRecurring(r.copy(nextDate = next))
        }
    }

    private suspend fun applyBalance(txn: Txn, sign: Int) {
        when (txn.type) {
            TxnType.INCOME -> dao.adjustBalance(txn.walletId, sign * txn.amount)
            TxnType.EXPENSE -> dao.adjustBalance(txn.walletId, -sign * txn.amount)
            TxnType.TRANSFER -> {
                dao.adjustBalance(txn.walletId, -sign * txn.amount)
                txn.toWalletId?.let { dao.adjustBalance(it, sign * txn.amount) }
            }
        }
    }

    companion object {
        fun advance(from: LocalDate, unit: RecurUnit, dayOfUnit: Int): LocalDate = when (unit) {
            RecurUnit.MONTHLY -> from.plusMonths(1).withDayOfMonth(dayOfUnit.coerceAtMost(28))
            RecurUnit.WEEKLY -> from.plusWeeks(1)
        }
    }
}

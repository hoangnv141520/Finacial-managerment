package com.hoang.moneytrack.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hoang.moneytrack.MoneyTrackApp
import com.hoang.moneytrack.R
import com.hoang.moneytrack.data.db.PortfolioSnapshot
import com.hoang.moneytrack.ui.common.toVnd
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Daily: generate recurring txns, snapshot portfolio, notify due reminders. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackApp
        val repo = app.repo
        repo.runRecurring()

        val today = LocalDate.now()
        if (repo.dao.snapshotCount(today) == 0) {
            val total = repo.dao.holdingsOnce().sumOf { (it.quantity * it.currentPrice).toLong() }
            repo.dao.insertSnapshot(PortfolioSnapshot(date = today, totalValue = total))
        }
        notifyDue(app)
        return Result.success()
    }

    private suspend fun notifyDue(app: MoneyTrackApp) {
        val due = app.repo.dao.dueRemindersOnce(LocalDate.now().plusDays(3))
        if (due.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val text = due.joinToString("\n") { "${it.title}: ${it.amount.toVnd()} — ${it.dueDate}" }
        NotificationManagerCompat.from(app).notify(
            1,
            NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(app.getString(R.string.notif_due_title, due.size))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    companion object { const val CHANNEL = "due_reminders" }
}

object Workers {
    fun scheduleDaily(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS).build(),
        )
    }
}

package com.hoang.moneytrack

import android.app.Application
import com.hoang.moneytrack.data.FinanceRepository
import com.hoang.moneytrack.data.db.AppDatabase
import com.hoang.moneytrack.data.prefs.SettingsStore
import com.hoang.moneytrack.data.security.DbKeyManager
import com.hoang.moneytrack.data.sync.SyncManager
import com.hoang.moneytrack.work.Workers

// ponytail: manual singletons instead of Hilt — 4 objects don't need a DI framework
class MoneyTrackApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val keys by lazy { DbKeyManager(this) }
    val repo by lazy { FinanceRepository(AppDatabase.get(this)) }
    val sync by lazy { SyncManager(AppDatabase.get(this), keys) }

    override fun onCreate() {
        super.onCreate()
        Workers.scheduleDaily(this)
    }
}

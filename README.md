# MoneyTrack

Personal finance app for Android. Vietnamese-first, offline, encrypted.

## Features

- **Home** — wallet balances, recent transactions, due bill alerts
- **Transactions** — log income / expense / transfer; recurring rules
- **Budget** — monthly category limits with progress bars; saving goals with deposits
- **Reports** — monthly bar chart, category breakdown pie chart
- **Reminders** — recurring bills + debt tracking (lend / borrow, installment payments)
- **Investments** — holdings tracker (stocks, crypto, gold, real estate)
- **Settings** — manage wallets & categories, PIN lock, backup / restore

## Tech

- Jetpack Compose · Material3
- Room + SQLCipher (encrypted DB)
- Kotlin Coroutines + Flow
- WorkManager (daily recurring engine)
- Min SDK 26 · Target SDK 35

## Build

Requires JDK 17 and Android SDK platform 35.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Changelog

### v0.1.0
- Initial release: 6 screens, SQLCipher DB, PIN lock, recurring engine, i18n VI/EN

### v0.1.1
- Fix: saving-goal creation crash (duplicate LazyColumn keys)
- Add: thousand-separator formatting on all money input fields
- Add: "Debt management" shortcut in Settings → Reminders

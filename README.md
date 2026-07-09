# MoneyTrack 💰

Ứng dụng quản lý tài chính cá nhân cho Android — đơn giản, bảo mật, offline-first.

## Tính năng

| Màn hình | Chức năng |
|---|---|
| **Tổng quan** | Số dư tổng, giao dịch gần đây, shortcuts nhanh |
| **Giao dịch** | Thu / Chi / Chuyển khoản, lọc theo tháng, lịch sử đầy đủ |
| **Ngân sách** | Hạn mức chi tiêu theo danh mục, cảnh báo >80%, mục tiêu tiết kiệm |
| **Đầu tư** | Theo dõi danh mục chứng khoán / crypto, lịch sử snapshot |
| **Nhắc nhở** | Hóa đơn định kỳ, quản lý nợ (cho vay / đi vay), ghi nhận trả nợ từng đợt |
| **Báo cáo** | Biểu đồ chi tiêu theo danh mục, xu hướng theo ngày |
| **Cài đặt** | Quản lý ví, danh mục, xuất/nhập dữ liệu, quản lý nợ vay |

### Điểm nổi bật
- 🔒 **Mã hóa SQLCipher** — database được bảo vệ bằng PIN, dữ liệu an toàn ngay cả khi mất điện thoại
- 📱 **Offline-first** — không cần internet, dữ liệu lưu hoàn toàn local
- 🔄 **Giao dịch định kỳ** — tự động tạo giao dịch thu/chi hàng tháng/hàng tuần
- 🌍 **Đa ngôn ngữ** — Tiếng Việt & English
- 🎨 **Material 3** — dark/light mode, giao diện hiện đại

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** + **SQLCipher** — local database với mã hóa
- **Kotlin Coroutines / Flow** — reactive UI
- **Navigation Compose** — điều hướng màn hình
- **WorkManager** — background jobs (snapshot danh mục đầu tư, recurring txns)
- **kotlinx.serialization** — sync payload
- Min SDK: **26** (Android 8.0) · Target SDK: **35**

## Build

Yêu cầu: JDK 17, Android SDK 35, Gradle 8+.

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (cần keystore)
./gradlew assembleRelease
```

APK debug xuất ra: `app/build/outputs/apk/debug/app-debug.apk`

## Cấu trúc thư mục

```
app/src/main/java/com/hoang/moneytrack/
├── data/
│   ├── db/          # Room entities, DAO, AppDatabase, migrations
│   ├── sync/        # SyncManager (export/import JSON)
│   └── FinanceRepository.kt   # tất cả mutation tiền đi qua đây
├── ui/
│   ├── home/        # màn Tổng quan
│   ├── budget/      # Ngân sách + Mục tiêu tiết kiệm
│   ├── investment/  # Danh mục đầu tư
│   ├── reminders/   # Hóa đơn + Nợ vay
│   ├── reports/     # Báo cáo biểu đồ
│   ├── transactions/# Lịch sử giao dịch
│   ├── quickadd/    # Bottom sheet thêm giao dịch nhanh
│   ├── settings/    # Cài đặt, quản lý ví/danh mục
│   ├── lock/        # Màn PIN bảo vệ
│   └── common/      # Components, Money formatter, Charts
└── work/            # DailyWorker (WorkManager)
```

## Changelog

### v0.1.0
- Khởi tạo app: 6 màn hình chính, SQLCipher, PIN lock, recurring engine, i18n VI/EN
- Fix: crash khi tạo mục tiêu tiết kiệm (duplicate LazyColumn key)
- Thêm: định dạng dấu chấm ngàn cho tất cả ô nhập số tiền (2.000.000đ)
- Thêm: shortcut Quản lý nợ vay trong Settings

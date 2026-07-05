# MoneyTrack — Personal Finance App (Android Native + Python BE) — Plan v3

> Chuyển đổi từ design web v2 (React/Figma "Personal Finance Management App") sang **Android native**.
> Ưu tiên: **Bảo mật → Tốc độ → Tối ưu** (theo thứ tự đó khi có xung đột).
> Repo: `C:\Users\hoang\Documents\GitHub\personal-financial`

---

## 0. Tech Stack (chốt)

| Lớp | Chọn | Lý do |
|---|---|---|
| Ngôn ngữ | Kotlin 2.x | chuẩn Android |
| UI | Jetpack Compose + Material 3 | map 1:1 với design tokens, animation built-in |
| Kiến trúc | MVVM + đơn module `app/` | 1 module đủ cho app cỡ này — KHÔNG multi-module (YAGNI) |
| DI | Hilt | chuẩn, ít boilerplate hơn tự viết |
| DB | Room + **SQLCipher** | dữ liệu tài chính mã hóa at-rest |
| Key storage | Android Keystore + EncryptedSharedPreferences | passphrase SQLCipher không bao giờ nằm plaintext |
| Charts | **Vico** (compose) | nhẹ hơn MPAndroidChart, native Compose |
| Điều hướng | Navigation Compose (single-activity) | |
| Nền | WorkManager | notification nhắc hóa đơn/nợ |
| Serialization | kotlinx.serialization | backup/export JSON |
| Min SDK | 26 (Android 8.0) | Keystore + notification channels ổn định |
| Target SDK | 35 | |
| **Backend** | **Python 3.11 + FastAPI** | auth + sync + backup; async, tự sinh OpenAPI docs |
| BE DB | SQLite (aiosqlite + SQLAlchemy 2) | 1 file, đủ cho personal app — lên Postgres khi cần multi-user |
| BE auth | JWT (PyJWT) + Argon2 (argon2-cffi) | access 15ph + refresh 30 ngày |
| HTTP client (app) | Retrofit + OkHttp + kotlinx-serialization | chuẩn Android |
| Deploy BE | Docker 1 container + Caddy (auto HTTPS) | |

**Không dùng:** Firebase, analytics SDK bên thứ 3 (bảo mật + tốc độ), KMP, microservices, Celery/Redis (chưa cần job queue).

**Nguyên tắc:** app vẫn **offline-first** — Room là source of truth trên máy, BE (Python) chỉ làm auth + sync + backup. Mất mạng app vẫn chạy đủ 100% tính năng.

---

## 1. Design System (giữ nguyên từ v2)

### 1.1 Màu — `ui/theme/Color.kt`

```kotlin
// Dark
val DarkBackground = Color(0xFF0D1117)
val DarkCard       = Color(0xFF161B22)
val DarkSecondary  = Color(0xFF21262D)
val DarkOnSurface  = Color(0xFFE6EDF3)
val DarkMuted      = Color(0xFF8B949E)
// Light
val LightBackground = Color(0xFFF7F9FB)
val LightCard       = Color(0xFFFFFFFF)
val LightOnSurface  = Color(0xFF0D1117)
val LightMuted      = Color(0xFF6B7280)
// Shared
val PrimaryDark  = Color(0xFF00C896)  // dark mode primary
val PrimaryLight = Color(0xFF00A67E)  // light mode primary (đậm hơn cho contrast)
val Destructive  = Color(0xFFFF4757)  // dark
val DestructiveLight = Color(0xFFE5484D)
// Chart palette
val ChartBlue = Color(0xFF3B82F6); val ChartAmber = Color(0xFFF59E0B); val ChartViolet = Color(0xFF8B5CF6)
// Budget progress: <60% primary, 60–80% ChartAmber, >80% Destructive
```

Quy tắc màu số tiền: **thu = primary, chi = destructive**, mọi nơi trong app.

### 1.2 Font

- Display (tiêu đề, số dư lớn): **Plus Jakarta Sans** — `res/font/plus_jakarta_sans_*.ttf`
- Body: **Inter**
- Số tiền / dữ liệu bảng: **DM Mono** (`FontFamily(Font(R.font.dm_mono_regular))`)
- Tất cả khai báo trong `Type.kt`, dùng `MaterialTheme.typography` + 1 style custom `MoneyStyle` (DM Mono, tabular).

### 1.3 Shape & spacing

- Corner radius card: 14dp (`--radius: 0.875rem`), radius nhỏ 10dp.
- Padding màn hình: 16dp ngang; card nội bộ 16dp; gap giữa section 20dp.

### 1.4 Theme mode

- 3 chế độ: Light / Dark / System — lưu DataStore, apply qua `MoneyTrackTheme(darkTheme = resolvedMode)`.
- KHÔNG dùng dynamic color (Material You) — giữ brand palette cố định.

### 1.5 i18n VI/EN

- Chuẩn Android: `res/values/strings.xml` (EN) + `res/values-vi/strings.xml` (VI). **Không** tự viết hàm `t()`.
- Đổi ngôn ngữ in-app: `AppCompatDelegate.setApplicationLocales()` (per-app language, API 33+; backport qua appcompat 1.6).
- Format tiền: `NumberFormat` theo locale — VI: `85.000đ`, EN: `₫85,000`. Một hàm duy nhất:

```kotlin
fun Long.toVnd(locale: Locale): String // amount lưu bằng Long (đơn vị: đồng), KHÔNG BAO GIỜ Float/Double
```

**Rule tiền tệ #1: mọi số tiền là `Long` (đồng). Không floating point.**

---

## 2. Bảo mật (ưu tiên cao nhất)

### 2.1 Mã hóa at-rest

1. DB Room mở bằng SQLCipher (`net.zetetic:sqlcipher-android`).
2. Passphrase = 32 byte random, sinh 1 lần lúc first-launch, lưu trong **EncryptedSharedPreferences** (master key trong Android Keystore, `AES256_GCM`).
3. Passphrase không bao giờ log, không bao giờ vào backup.

```kotlin
// data/security/DbKeyManager.kt
class DbKeyManager(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(...)
    fun getOrCreateDbKey(): ByteArray  // SecureRandom 32 bytes, base64 trong prefs
}
```

### 2.2 App lock

- PIN 6 số **hoạt động thật** (không chỉ demo như plan v2): PIN → PBKDF2-SHA256 (210k iterations, salt 16B random) → lưu hash trong EncryptedSharedPreferences.
- Biometric: `BiometricPrompt` (BIOMETRIC_STRONG), fallback về PIN.
- Lock trigger: app vào background > 60s (theo dõi qua `ProcessLifecycleOwner`), hoặc mở app mới.
- Màn `LockScreen` chặn toàn bộ NavHost cho tới khi unlock. Sai PIN 5 lần → khóa 30s.

### 2.3 Chống lộ dữ liệu

1. `FLAG_SECURE` trên Activity → chặn screenshot + recent-apps preview. Toggle được trong Settings (mặc định BẬT).
2. `android:allowBackup="false"` + `android:fullBackupContent="false"` — backup chỉ qua export thủ công có mã hóa (mục 2.4).
3. Chế độ "ẩn số dư" (icon con mắt trên Balance card) → hiển thị `••••••`, lưu state DataStore.
4. Không permission nào ngoài `POST_NOTIFICATIONS` + `USE_BIOMETRIC`.
5. R8 full mode + `debuggable=false` bản release.

### 2.4 Export/Backup thủ công

- Export JSON → mã hóa AES-256-GCM bằng key derive từ passphrase user nhập → ghi qua SAF (`ACTION_CREATE_DOCUMENT`). Import ngược lại. (Phase 5 — làm cuối.)

---

## 3. Tốc độ & Tối ưu

1. **Cold start < 1s** trên máy tầm trung: theme splash qua `androidx.core:core-splashscreen`; không init gì nặng trong `Application.onCreate` ngoài Hilt; DB mở lazy.
2. **Baseline Profile** (`androidx.baselineprofile`) — generate cho flow: mở app → Home → Transactions. Phase 5.
3. Mọi query qua **Flow + Room**, tính toán tổng hợp (tổng thu/chi tháng, budget %) bằng **SQL aggregate trong DAO**, không load hết list rồi sum trong Kotlin:

```sql
SELECT COALESCE(SUM(amount),0) FROM txn
WHERE type='EXPENSE' AND date BETWEEN :from AND :to
```

4. `LazyColumn` với `key = { txn.id }` cho mọi list; transaction list phân trang bằng Paging 3 **chỉ khi** > ~1k rows (bắt đầu bằng `LIMIT 100` + nút "tải thêm" — ponytail: đủ cho dữ liệu cá nhân).
5. Charts: Vico nhận data đã aggregate sẵn từ DAO (`GROUP BY day/category`), không tính trên UI thread.
6. Index DB: `txn(date)`, `txn(categoryId)`, `txn(walletId)`, `reminder(dueDate)`.
7. `@Immutable`/`@Stable` cho UI models; ViewModel expose `StateFlow<UiState>` duy nhất mỗi màn.
8. Icon danh mục = emoji text (như design) — không icon pack, không vector bloat.

---

## 4. Data Model — `data/db/`

```kotlin
@Entity data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val type: WalletType, // CASH, BANK, EWALLET
    val balance: Long, val colorHex: String, val sortOrder: Int)

@Entity data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val emoji: String, val type: TxnType) // INCOME/EXPENSE

@Entity(tableName = "txn") data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long, val type: TxnType, // INCOME, EXPENSE, TRANSFER
    val categoryId: Long?, val walletId: Long, val toWalletId: Long?, // transfer
    val note: String, val date: LocalDate, val recurringId: Long?)

@Entity data class Recurring( // lịch lặp cho lương/tiền nhà/subscription
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateTxn: /* các field như Txn */,
    val rule: RecurRule, // MONTHLY(dayOfMonth), WEEKLY(dayOfWeek)
    val nextDate: LocalDate, val active: Boolean)

@Entity data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long, val month: YearMonth, val limitAmount: Long)

@Entity data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val targetAmount: Long, val savedAmount: Long, val deadline: LocalDate?)

@Entity data class Reminder( // hóa đơn + nợ chung 1 bảng
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ReminderKind, // BILL, DEBT_LEND, DEBT_BORROW
    val title: String, val amount: Long, val dueDate: LocalDate,
    val paid: Boolean, val paidAmount: Long, // tiến độ trả góp
    val recurDayOfMonth: Int?) // hóa đơn định kỳ

@Entity data class Holding( // đầu tư
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetType: AssetType, // STOCK, GOLD, CRYPTO, SAVINGS
    val symbol: String, val quantity: Double, val costBasis: Long, val currentPrice: Long)
```

**Rule nghiệp vụ:**
1. Thêm/sửa/xóa Txn phải cập nhật `Wallet.balance` trong **cùng 1 Room transaction**.
2. TRANSFER = 1 row Txn với `walletId` (nguồn) + `toWalletId` (đích), trừ/cộng 2 ví atomic.
3. Recurring: khi app mở (và qua WorkManager hàng ngày), engine sinh Txn cho mọi `nextDate <= today` rồi tiến `nextDate`. Idempotent.
4. Giá Holding: nhập tay + nút "cập nhật giá" (không API real-time — YAGNI, thêm sau nếu cần).
5. Xóa Category đang được dùng → chặn, báo lỗi (FK RESTRICT).

Repository: 1 file `data/FinanceRepository.kt` bọc các DAO. Không interface + impl tách đôi khi chỉ có 1 implementation.

---

## 4B. Backend (Python + FastAPI) — `server/`

### Vai trò (chỉ 3 việc)
1. **Auth**: đăng ký/đăng nhập email + password.
2. **Sync**: đẩy/kéo dữ liệu giữa các thiết bị (last-write-wins theo `updatedAt`).
3. **Backup**: giữ bản sao dữ liệu mã hóa trên server.

### Cấu trúc

```
server/
├── app/
│   ├── main.py          # FastAPI app, routers, CORS off (chỉ mobile)
│   ├── db.py            # SQLAlchemy async engine + session
│   ├── models.py        # User, SyncRecord
│   ├── auth.py          # register/login/refresh, Argon2, JWT
│   ├── sync.py          # push/pull endpoints
│   └── config.py        # pydantic-settings, đọc .env
├── tests/test_api.py    # pytest + httpx: auth flow + sync round-trip
├── pyproject.toml       # uv, deps: fastapi, uvicorn, sqlalchemy[asyncio], aiosqlite, pyjwt, argon2-cffi, pydantic-settings
├── Dockerfile
└── .env.example         # JWT_SECRET, DB_PATH
```

### API

| Method | Path | Body → Response |
|---|---|---|
| POST | `/auth/register` | `{email, password}` → `{access, refresh}` |
| POST | `/auth/login` | `{email, password}` → `{access, refresh}` |
| POST | `/auth/refresh` | `{refresh}` → `{access}` |
| POST | `/sync/push` | `{records: [{table, uuid, payload, updatedAt, deleted}]}` → `{applied}` |
| GET | `/sync/pull?since=<ts>` | → `{records: [...], serverTime}` |

### Sync model (đơn giản nhất chạy được)

- Mỗi entity trên app thêm 2 cột: `uuid TEXT` (sinh client) + `updatedAt Long` (epoch ms) + `dirty Boolean`.
- Server lưu bảng duy nhất `sync_record(user_id, table, uuid, payload TEXT/JSON, updated_at, deleted)` — **server không hiểu nghiệp vụ**, chỉ là kho key-value theo user. Payload là JSON của row.
- Conflict: **last-write-wins** theo `updated_at`. `# ponytail: LWW đủ cho 1 user vài thiết bị; CRDT khi có collab thật`
- Xóa = soft-delete flag trong sync record.
- App: `SyncWorker` (WorkManager, khi có mạng + charging không bắt buộc) — push các row `dirty=true`, pull `since=lastSyncTs`, apply vào Room trong 1 transaction.
- **Payload mã hóa client-side** (AES-GCM, key derive từ passphrase user như mục 2.4) trước khi push → server zero-knowledge, DB server lộ cũng không đọc được dữ liệu tài chính. Server chỉ thấy metadata (table, uuid, timestamp).

### Bảo mật BE

1. Password: Argon2id, không giới hạn độ dài dưới 72 (không phải bcrypt).
2. JWT: HS256, secret 32B từ env; access 15 phút, refresh 30 ngày, refresh rotate khi dùng.
3. Rate limit login: 5 lần/phút/IP — `slowapi` hoặc counter in-memory `# ponytail: in-memory dict đủ 1 instance, Redis khi scale`.
4. HTTPS bắt buộc (Caddy tự cấp Let's Encrypt); app pin chỉ gọi `https://`.
5. Toàn bộ endpoint sync yêu cầu `Authorization: Bearer`; mọi query lọc theo `user_id` từ token — không bao giờ nhận user_id từ client.
6. Không log payload, không log token.

### Chạy dev

```bash
cd server && uv sync && uv run uvicorn app.main:app --reload  # http://127.0.0.1:8000/docs
uv run pytest                                                  # tests
```

---

## 5. Màn hình (map từ design v2)

### Navigation

- Single Activity, `NavHost`. Bottom bar 4 tab + FAB giữa: **Trang chủ | Giao dịch | [FAB] | Ngân sách | Đầu tư**.
- Nhắc nhở + Báo cáo + Settings: icon ở header Home (bell → Reminders, chart → Reports, gear → Settings).
- Tab switch: `AnimatedContent` fade+slide nhẹ (200ms). FAB mở modal bottom sheet.

### 5.1 Home — `ui/home/`
- Header: avatar (initials), "Chào, Hoàng 👋", bell icon + badge (số reminder chưa trả đến hạn ≤ 3 ngày), icon Reports, icon Settings.
- Balance card (gradient card tối, primary accent): tổng tài sản (SUM ví), thu/chi tháng này, icon mắt ẩn số dư.
- Quick actions: 3 nút — + Thu nhập / + Chi tiêu / Chuyển tiền (mở cùng sheet FAB với preset tương ứng).
- Wallet row scroll ngang: card mini mỗi ví (tên, số dư, màu).
- **Widget nhắc nhở**: tối đa 2 reminder `dueDate <= today+3 && !paid`, tap → tab Reminders.
- Giao dịch gần đây: 5 dòng + "Xem tất cả".
- Mini budget: 2–3 category có % dùng cao nhất, progress bar 3 màu.

### 5.2 Transactions — `ui/transactions/`
- Filter chips: Tất cả / Thu / Chi + month picker (◀ Tháng 7/2026 ▶).
- Search (debounce 300ms, query `LIKE` trên note + tên category).
- Group theo ngày: sticky header "Hôm nay / Hôm qua / dd/MM" + tổng ngày.
- Row: emoji category trong ô tròn màu, tên, tên ví, số tiền mono xanh/đỏ.
- Badge 🔁 khi `recurringId != null`; tap row → detail sheet (sửa/xóa; nếu recurring: xem rule lặp, ngày kế tiếp).

### 5.3 Budget — `ui/budget/`
- Month picker; summary card: tổng ngân sách / đã dùng / còn lại.
- List category budget: emoji, tên, `spent/limit`, progress bar đổi màu (<60 xanh, 60–80 vàng, >80 đỏ).
- **Banner cảnh báo** đầu màn khi ≥1 category vượt 80%: nền destructive/10, "⚠️ Ăn uống đã dùng 92% ngân sách".
- **Mục tiêu tiết kiệm**: section riêng, card mỗi goal (tên, `50tr/100tr`, progress, nút "+ Nạp tiền" → tạo Txn EXPENSE category "Tiết kiệm" + tăng savedAmount).
- Nút "+ Tạo ngân sách", "+ Tạo mục tiêu" → sheet form.

### 5.4 Investment — `ui/investment/`
- Header: tổng giá trị portfolio + P&L (số + %) màu theo dấu.
- Tabs: Cổ phiếu | Vàng | Crypto | Tiết kiệm.
- List holding: symbol (mono), số lượng, giá vốn, giá hiện tại, %± màu.
- Line chart 30 ngày (Vico) — data từ bảng snapshot giá trị portfolio ghi mỗi ngày (WorkManager) — nếu chưa đủ data thì mock từ current value ±.
- "+ Thêm tài sản", tap row → sửa giá/số lượng.

### 5.5 Reminders — `ui/reminders/`
- 2 sub-tab: **Hóa đơn định kỳ | Nợ vay**.
- Hóa đơn: tên, số tiền, "hạn 10/tháng", trạng thái chip (Đã trả / Chưa trả / Quá hạn), nút "Đánh dấu đã trả" → tạo Txn EXPENSE + reset sang tháng sau.
- Nợ: hướng (cho vay ↗ / đi vay ↙), người, số tiền, hạn, progress trả góp (`paidAmount/amount`), nút "+ Ghi nhận trả".
- "+ Thêm nhắc nhở" → sheet form.
- **Notification**: WorkManager daily 8:00 — bắn notification cho reminder đến hạn trong 3 ngày. Channel riêng "Nhắc nhở đến hạn".

### 5.6 Reports — `ui/reports/`
- Period tabs: Tuần / Tháng / Năm.
- Bar chart thu vs chi theo kỳ (Vico, 2 series primary/destructive).
- Pie/Donut chi theo danh mục (Vico hoặc Canvas donut đơn giản — Vico pie nếu bản dùng có, không thì Canvas ~40 dòng).
- Line chart tiết kiệm lũy kế (thu − chi cộng dồn).
- Bảng so sánh kỳ này vs kỳ trước: thu, chi, tiết kiệm, Δ% màu theo chiều tốt/xấu.

### 5.7 Settings — `ui/settings/`
- Ngôn ngữ: VI / EN.
- Giao diện: Light / Dark / Theo hệ thống.
- Bảo mật: bật/tắt PIN, đổi PIN, bật/tắt vân tay, bật/tắt chặn screenshot.
- Tài khoản & Đồng bộ: đăng nhập/đăng ký, trạng thái sync lần cuối, nút "Đồng bộ ngay", đăng xuất.
- Export / Import dữ liệu (mã hóa).
- Quản lý ví & danh mục (thêm/sửa/ẩn).

### 5.8 FAB Quick-add — `ui/quickadd/QuickAddSheet.kt`
- ModalBottomSheet: bàn phím số custom (grid 4×3, DM Mono, hiển thị số lớn có format), toggle Thu/Chi/Chuyển, chọn category (grid emoji), chọn ví, note, date picker (mặc định hôm nay), toggle "🔁 Lặp lại định kỳ" → chọn rule (hàng tháng ngày X / hàng tuần thứ Y).
- Lưu → Room transaction (Txn + update balance + tạo Recurring nếu bật) → sheet đóng, toast/snackbar.

---

## 6. Seed Data (thay mock data — insert lần đầu qua RoomDatabase.Callback)

- Ví: Tiền mặt 2.500.000 / VCB 15.300.000 / Techcombank 8.700.000 / MoMo 1.200.000.
- Category: 🍜 Ăn uống, 🚗 Di chuyển, 🏠 Nhà ở, 💊 Sức khỏe, 🛍️ Mua sắm (EXPENSE); 💰 Lương, 📈 Đầu tư (INCOME); 🐷 Tiết kiệm (EXPENSE, dùng cho goal).
- Hóa đơn: Điện 450.000 hạn 10 hàng tháng; Internet 220.000 hạn 15.
- Nợ: "Cho Minh vay" 2.000.000, hạn 30/07, kind=DEBT_LEND.
- Goal: Mua xe 50tr/100tr; Quỹ khẩn cấp 20tr/30tr.
- ~30 Txn mẫu rải 2 tháng gần nhất (đủ cho charts + group ngày).
- Recurring mẫu: Lương 25.000.000 ngày 5 hàng tháng.

---

## 7. Cấu trúc thư mục

```
app/src/main/java/com/hoang/moneytrack/
├── MoneyTrackApp.kt            # @HiltAndroidApp
├── MainActivity.kt             # single activity, FLAG_SECURE, lock gate
├── data/
│   ├── db/                     # AppDatabase, entities, DAOs, Converters, SeedCallback
│   ├── security/               # DbKeyManager, PinManager, CryptoBackup
│   ├── prefs/                  # SettingsDataStore (theme, lang, hideBalance, lock config)
│   ├── recurring/              # RecurringEngine
│   └── FinanceRepository.kt
├── ui/
│   ├── theme/                  # Color.kt, Type.kt, Theme.kt
│   ├── nav/                    # NavGraph, BottomBar
│   ├── common/                 # MoneyText, ProgressBar3Color, EmptyState, MonthPicker
│   ├── lock/                   # LockScreen, PinPad
│   ├── home/  transactions/  budget/  investment/  reminders/  reports/  settings/  quickadd/
│   └── ...mỗi màn: XxxScreen.kt + XxxViewModel.kt
└── work/                       # ReminderWorker, RecurringWorker, SnapshotWorker
```

---

## 8. Phases (thứ tự thực thi)

### Phase 0 — Scaffold *(nền)*
1. `gradle init` project Compose empty (Kotlin DSL, version catalog `libs.versions.toml`).
2. Deps: compose-bom, m3, nav, hilt, room+sqlcipher, security-crypto, biometric, datastore, vico, workmanager, splashscreen, kotlinx-serialization.
3. Theme + fonts + Color/Type/Shape. Chạy được app trống có bottom bar 4 tab + FAB.
4. Verify: build release minify OK.

### Phase 1 — Data + Security core
1. Entities + DAOs + AppDatabase (SQLCipher) + DbKeyManager + seed.
2. FinanceRepository + unit test: thêm Txn cập nhật balance đúng, transfer atomic, aggregate tháng đúng.
3. Verify: `./gradlew test` pass; cài app, DB file mở bằng sqlite3 thường phải FAIL (đã mã hóa).

### Phase 2 — Home + Transactions + Quick-add (core loop)
1. QuickAddSheet đầy đủ (chưa cần recurring).
2. Home đủ section (reminder widget để trống nếu chưa có Phase 4).
3. Transactions: filter, search, group ngày, detail sheet sửa/xóa.
4. Verify: thêm chi tiêu từ FAB → thấy ngay ở Home + Transactions, balance ví đổi đúng.

### Phase 3 — Budget + Goals + Reports
1. Budget list + banner 80% + form tạo.
2. SavingGoal + nạp tiền.
3. Reports 3 charts + bảng so sánh.
4. Verify: progress đúng tỷ lệ, banner hiện khi 1 category > 80%, charts đúng data seed.

### Phase 4 — Reminders + Recurring + Notifications + Investment
1. RecurringEngine + toggle lặp trong QuickAdd + badge 🔁.
2. Reminders 2 sub-tab, mark-paid tạo Txn.
3. ReminderWorker daily notification.
4. Investment screen + snapshot worker.
5. Verify: đổi ngày hệ thống → recurring sinh Txn đúng, không sinh trùng.

### Phase 5 — Lock, Settings, i18n, tối ưu
1. PIN + biometric + LockScreen + FLAG_SECURE toggle.
2. Settings đầy đủ; strings.xml EN + values-vi đầy đủ; per-app locale switch.
3. Export/Import mã hóa.
4. Baseline profile, kiểm cold-start (macrobenchmark hoặc `adb shell am start -W`).
5. Verify checklist mục 9.

### Phase 6 — Backend + Sync
1. `server/`: FastAPI scaffold + auth (register/login/refresh, Argon2, JWT) + pytest auth flow.
2. Endpoint sync push/pull + bảng `sync_record` + pytest round-trip (push từ "máy A", pull về "máy B" ra đúng data).
3. App: thêm cột `uuid/updatedAt/dirty` (Room migration), Retrofit client, màn đăng nhập trong Settings, `SyncWorker`.
4. Mã hóa payload client-side trước khi push.
5. Dockerfile + Caddy, deploy.
6. Verify: 2 emulator cùng tài khoản — tạo giao dịch máy A, sync, thấy trên máy B; tắt mạng app vẫn hoạt động đầy đủ; DB server không đọc được payload.

---

## 9. Verification cuối

- [ ] 6 khu vực chức năng render không lỗi, điều hướng đủ.
- [ ] Đổi theme Light/Dark/System, đổi ngôn ngữ VI/EN không cần restart process.
- [ ] Charts đúng data; progress bar budget/goal đúng tỷ lệ; banner >80% hoạt động.
- [ ] FAB → quick-add với recurring toggle hoạt động; recurring sinh giao dịch đúng lịch.
- [ ] PIN sai 5 lần bị khóa 30s; biometric unlock OK; screenshot bị chặn khi FLAG_SECURE bật.
- [ ] DB file trên thiết bị không đọc được bằng sqlite3 thường.
- [ ] `allowBackup=false`; release build R8 chạy bình thường.
- [ ] Cold start < ~1s máy thật; list 1k giao dịch scroll không giật.
- [ ] Mọi số tiền là Long; format VI `85.000đ`, EN `₫85,000`.
- [ ] BE: pytest pass; login sai 5 lần/phút bị rate-limit; sync 2 thiết bị đúng LWW; payload trên server là ciphertext.

---

## 10. Khác biệt so với plan v2 (web)

| v2 (web) | v3 (Android) | Lý do |
|---|---|---|
| `t('key')` object translations | strings.xml + values-vi | chuẩn nền tảng, free plural/format |
| CSS variables + .dark class | Material 3 ColorScheme | native |
| recharts | Vico | Compose-native, nhẹ |
| PIN/vân tay "chỉ UI demo" | **hoạt động thật** | user ưu tiên bảo mật |
| state giả lập persist | Room (SQLCipher) + DataStore | dữ liệu thật, mã hóa |
| phone frame max-w-sm | app thật | — |
| motion/react | Compose animation | — |

**Cắt bỏ (YAGNI, thêm sau nếu cần):** multi-currency, API giá real-time, widget homescreen, Wear OS, multi-module, Paging 3 (tới khi >1k rows), CRDT sync, Redis/Celery, Postgres.

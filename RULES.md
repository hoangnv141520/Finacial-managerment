# RULES.md — Quy tắc code dự án MoneyTrack

> Đọc kèm `PLAN.md`. Rule ngắn — vi phạm thì reviewer chỉ vào số mục.
> Nguyên tắc gốc: **code không viết ra là code tốt nhất**. YAGNI. Diff ngắn nhất chạy được là diff đúng.

---

## 1. Quy tắc chung (cả Android + BE)

1.1. **Không abstraction khi chỉ có 1 implementation.** Không interface `XxxRepository` + `XxxRepositoryImpl` khi chỉ có 1 class. Cần mock để test → dùng thư viện mock hoặc fake, không đẻ interface.
1.2. **Không viết "cho tương lai".** Không param không dùng, không config cho giá trị không bao giờ đổi, không generic khi chỉ có 1 kiểu.
1.3. **Tìm trước khi viết.** Helper/format/extension cần gì → grep repo trước. Trùng chức năng = xóa 1 cái.
1.4. **Xóa hẳn, không comment-out.** Git giữ lịch sử rồi. Không `// TODO: remove`, không code chết.
1.5. **Boring > clever.** Ai đọc lúc 3h sáng phải hiểu ngay. One-liner khôn lỏi thua 3 dòng dễ đọc.
1.6. **Comment giải thích WHY, không phải WHAT.** Code tự nói nó làm gì. Comment chỉ khi có lý do không hiển nhiên (workaround, ceiling đã biết, quyết định nghiệp vụ). Shortcut cố ý → đánh dấu `// ponytail: <ceiling + khi nào nâng cấp>`.
1.7. **Hàm làm 1 việc, ≤ ~40 dòng.** Dài hơn = tách hoặc nghĩ lại. File ≤ ~400 dòng.
1.8. **Fail loudly.** Không nuốt exception. Bắt được thì xử lý có ý nghĩa hoặc để nổ. Cấm `catch (e: Exception) {}` / `except: pass`.
1.9. **Commit nhỏ, message dạng** `type: mô tả` — `feat:`, `fix:`, `refactor:`, `test:`, `chore:`. Tiếng Anh, thì hiện tại, ≤ 72 ký tự.
1.10. **Mỗi logic non-trivial để lại 1 check chạy được** (unit test hoặc assert-based check). Đường tiền + bảo mật **bắt buộc** có test. One-liner hiển nhiên không cần test.

---

## 2. Tiền & bảo mật (KHÔNG thỏa hiệp — override mọi rule "lười")

2.1. Số tiền = `Long` (đồng). Cấm Float/Double cho tiền, kể cả tạm.
2.2. Cộng/trừ balance + insert Txn = **cùng 1 DB transaction**. Không có ngoại lệ.
2.3. Không log: số tiền, token, PIN, passphrase, payload sync. `Log.d` dữ liệu nhạy cảm = reject PR.
2.4. Key/secret chỉ sống trong Keystore (app) hoặc env (BE). Cấm hardcode, cấm commit `.env`.
2.5. Mọi input từ user/network validate tại biên (form, API endpoint). Bên trong tin nhau, không validate lặp.
2.6. BE: mọi query lọc theo `user_id` lấy từ JWT. Không bao giờ nhận user_id từ request body.

---

## 3. Android — tổ chức file

3.1. **Package theo feature, không theo layer:**

```
ui/home/HomeScreen.kt + HomeViewModel.kt     ✅
ui/screens/… + ui/viewmodels/…               ❌
```

3.2. Mỗi màn đúng 2 file: `XxxScreen.kt` (composables) + `XxxViewModel.kt`. Composable con của riêng màn đó nằm luôn trong `XxxScreen.kt` (private) — chỉ tách sang `ui/common/` khi màn **thứ hai** cần dùng.
3.3. `ui/common/` chỉ chứa thứ ≥2 màn dùng thật (MoneyText, MonthPicker…). Không bỏ sẵn "để dành".
3.4. `data/` như PLAN.md mục 7. Entity + DAO cùng file khi DAO < 50 dòng.
3.5. Không tạo file mới khi thêm được vào file sẵn có cùng chủ đề mà vẫn < 400 dòng.

## 4. Android — cách viết

4.1. **Naming:**
| Loại | Quy ước | Ví dụ |
|---|---|---|
| Class/Composable | PascalCase | `QuickAddSheet`, `MoneyText` |
| Hàm/biến | camelCase, động từ cho hàm | `markPaid()`, `totalBalance` |
| Boolean | is/has/can | `isPaid`, `hasOverBudget` |
| Hằng | UPPER_SNAKE trong companion/top-level | `MAX_PIN_ATTEMPTS` |
| StateFlow UI | `uiState` — 1 cái duy nhất/ViewModel | `val uiState: StateFlow<HomeUiState>` |
| Resource string | `snake_case` theo màn | `home_greeting`, `budget_warning_banner` |

Tên bằng **tiếng Anh**. Tiếng Việt chỉ ở strings.xml và comment nếu cần.

4.2. **ViewModel:** expose đúng 1 `StateFlow<XxxUiState>` (data class) + các hàm event public. Không expose `MutableStateFlow`, không LiveData.
4.3. **UiState là data class immutable**, có default cho mọi field → preview/test dễ:
```kotlin
data class HomeUiState(
    val totalBalance: Long = 0,
    val wallets: List<Wallet> = emptyList(),
    val isLoading: Boolean = true,
)
```
4.4. **Composable:** nhận state + lambda, không nhận ViewModel (trừ route-level). `XxxScreen(uiState, onAddClick = …)` → preview được không cần Hilt.
4.5. Tính toán/aggregate làm trong **SQL (DAO)** hoặc ViewModel — cấm business logic trong composable.
4.6. `LazyColumn` luôn có `key = { it.id }`.
4.7. Không `!!`. Null xử lý bằng `?:`, `?.let`, hoặc thiết kế lại để không nullable.
4.8. String hiển thị **luôn** qua `stringResource(R.string.…)` — hardcode string UI = reject.
4.9. Format tiền chỉ qua 1 hàm `Long.toVnd()` duy nhất (`ui/common/Money.kt`). Không tự format chỗ khác.
4.10. Coroutine: dùng `viewModelScope`, Room trả `Flow` → `stateIn`. Không tự đổi Dispatchers khi Room/Retrofit đã tự xử lý.

## 5. Python BE — tổ chức & cách viết

5.1. Cấu trúc phẳng như PLAN.md 4B: 6 file trong `app/`. **Không** chia `routers/ services/ repositories/ schemas/` nhiều tầng — app này 5 endpoint.
5.2. **Naming:** PEP8 — `snake_case` hàm/biến, `PascalCase` class, `UPPER_SNAKE` hằng. Tên tiếng Anh.
5.3. **Type hints bắt buộc** trên mọi signature public. Pydantic model cho mọi request/response body — không dict lồng nhau tự do.
5.4. Async toàn tuyến: `async def` endpoint, `aiosqlite`. Không trộn sync DB call.
5.5. Lỗi trả `HTTPException` với status đúng nghĩa (401 sai token, 409 email trùng, 422 để FastAPI tự lo). Không trả 200 kèm `{"error": …}`.
5.6. Tooling: **ruff** (lint + format, thay black/isort/flake8), chạy `uv run ruff check && uv run ruff format --check` trước commit. Config trong `pyproject.toml`, giữ default tối đa.
5.7. Test: pytest + httpx, 1 file `tests/test_api.py`. Test theo flow (register→login→push→pull), không unit-test từng hàm private.

## 6. Git & quy trình

6.1. Branch: `main` luôn build được. Feature branch `feat/<tên>`, merge khi test pass.
6.2. Mỗi task trong PLAN.md phase = 1 commit trở lên. Không commit "WIP" lên main.
6.3. `.gitignore`: `local.properties`, `.env`, `*.keystore`, `build/`, `.venv/`, `*.db`.
6.4. Trước commit: app build được (`assembleDebug`), test pass, BE thì ruff + pytest pass.

## 7. Dependency

7.1. Thêm dep mới phải trả lời được: stdlib/dep sẵn có làm được không? Nếu ≤ ~30 dòng tự viết → tự viết.
7.2. Version khai báo tập trung: `libs.versions.toml` (Android), `pyproject.toml` (BE). Không hardcode version rải rác.
7.3. Danh sách dep đã chốt nằm trong PLAN.md mục 0 — thêm ngoài danh sách phải ghi lý do vào PR/commit message.

---

## Checklist review nhanh (dán vào PR)

- [ ] Có abstraction thừa không? (interface 1 impl, helper 1 chỗ gọi)
- [ ] Tiền là Long? Balance update cùng transaction?
- [ ] Có log dữ liệu nhạy cảm không?
- [ ] String UI qua resource? Format tiền qua `toVnd()`?
- [ ] Logic non-trivial có 1 test/check?
- [ ] Xóa được dòng nào nữa không?

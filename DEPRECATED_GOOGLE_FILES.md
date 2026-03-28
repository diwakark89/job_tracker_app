# Deprecated Google Sheets Integration Files

> **Status:** Kept for reference. Safe to delete once Supabase rollout is confirmed stable.
> **Added:** March 2026 – Supabase migration phase 1.

---

## Files to Remove

| File | Reason |
|------|--------|
| `app/src/main/java/com/thewalkersoft/linkedin_job_tracker/sync/SyncService.kt` | Bidirectional Google Sheets sync. Replaced by `SupabaseRepository` + `OutboxSyncWorker`. |
| `app/src/main/java/com/thewalkersoft/linkedin_job_tracker/client/RetrofitClient.kt` | Google Apps Script Retrofit singleton. Replaced by `SupabaseClient`. |
| `app/src/main/java/com/thewalkersoft/linkedin_job_tracker/service/GoogleSheetApiService.kt` | Retrofit interface for Google Sheets endpoints. Replaced by `SupabaseApiService`. |
| `app/src/main/java/com/thewalkersoft/linkedin_job_tracker/service/GoogleSheetResponse.kt` | DTO for Google Sheets API responses. No equivalent needed for Supabase. |
| `GoogleSheetUpdateScript.gs` | Google Apps Script backend. Replaced by the Supabase `jobs` and `shared_links` tables. |

---

## Deletion Pre-checks

Before deleting, confirm all the following:

- [ ] `./gradlew build` is green with Supabase configured.
- [ ] Supabase realtime subscription is verified live on a device or emulator.
- [ ] Outbox queue replays successfully on reconnect (observe logcat tag `OutboxSyncWorker`).
- [ ] No remaining `import` of `RetrofitClient`, `SyncService`, `GoogleSheetApiService`, or `GoogleSheetResponse` in any source file.
- [ ] `DEPLOYMENT_ID` in `RetrofitClient.kt` is no longer referenced in any documentation.

---

## Grep Verification Command

Run the following before deletion to confirm no live references remain:

```powershell
# Windows PowerShell
Select-String -Path "app\src\main\java\**\*.kt" -Pattern "RetrofitClient|SyncService|GoogleSheetApiService|GoogleSheetResponse" -Recurse
```

---

## Related New Files

| New File | Purpose |
|----------|---------|
| `client/SupabaseClient.kt` | Retrofit singleton for Supabase REST API with `JobStatus` Gson adapter. |
| `service/SupabaseApiService.kt` | Retrofit interface for Supabase PostgREST (`jobs`, `shared_links`). |
| `sync/SupabaseRepository.kt` | Cloud push/pull operations used by the ViewModel. |
| `sync/OutboxSyncWorker.kt` | WorkManager worker that replays the offline queue. |
| `sync/OutboxWorkScheduler.kt` | Schedules the 60-minute periodic background retry. |
| `sync/SupabaseRealtimeManager.kt` | OkHttp WebSocket realtime subscription to the `jobs` table. |
| `sync/RealtimeJobEvent.kt` | Sealed class for INSERT / UPDATE / DELETE realtime events. |
| `sync/OutboxOperation.kt` | Data class representing a queued outbox mutation. |


# AGENTS.md

## Project Overview

Job Tracker is a single-module Android app for saving and tracking LinkedIn applications. Users share job links from Android share intent, the app scrapes metadata with JSoup, stores data in Room, and syncs with Supabase.

**Key technologies:** Kotlin, Jetpack Compose, Room (KSP), Retrofit, OkHttp, JSoup, Navigation Compose, WorkManager, Supabase REST/Realtime.

**SDK targets:** `minSdk 34`, `compileSdk / targetSdk 36`, Java 11.

**Architecture:** MVVM with a single `JobViewModel` as orchestration point.

---

## Project Structure

```text
app/src/main/java/com/thewalkersoft/linkedin_job_tracker/
|- MainActivity.kt
|- data/
|  |- JobEntity.kt
|  |- JobDao.kt
|  `- JobDatabase.kt
|- viewmodel/
|  `- JobViewModel.kt
|- scraper/
|  `- JobScraper.kt
|- sync/
|  |- SupabaseRepository.kt
|  |- SupabaseRealtimeManager.kt
|  |- SyncDiagnostics.kt
|  |- OutboxOperation.kt
|  |- OutboxSyncWorker.kt
|  `- OutboxWorkScheduler.kt
|- client/
|  `- SupabaseClient.kt
|- service/
|  `- SupabaseApiService.kt
|- navigation/
|- ui/
`- util/
   `- PreferencesManager.kt
```

Room schema exports live in `app/schemas/`.

---

## Build Commands

```bash
./gradlew build
./gradlew clean build
./gradlew test
./gradlew connectedAndroidTest
```

On Windows use `gradlew.bat`.

---

## Core Conventions

### Room / Database
- Use `@Upsert` through `JobDao.upsertJob`.
- Keep explicit migrations in `JobDatabase`; do not use `fallbackToDestructiveMigration`.
- Update `updatedAt = System.currentTimeMillis()` on every mutation.
- Treat `jobUrl` as business key for sync identity.

### State Management
- `allJobs` stays unfiltered; `jobs` is filtered/search-derived.
- `_isScraping` covers scraping and sync loading states.
- `_message` carries user-facing one-shot messages; clear after display.

### Status Handling
- Use `JobStatus.displayName()` for UI text.
- Use `parseJobStatus()` for inbound string parsing.

### Supabase Integration
- `SupabaseApiService` targets `rest/v1/jobs_final` and uses `on_conflict=job_id`.
- Use canonical snake_case wire field names (`company_name`, `job_url`, `saved_at`, `modified_at`, etc.).
- Realtime updates are consumed from `realtime:public:jobs_final` through `SupabaseRealtimeManager` and applied to Room.

---

## Debugging Anchors

| Symptom | Where to check |
|---------|----------------|
| Missing details record | `Screen.kt` route args and `AppNavigation.kt` lookup against `allJobs` |
| Sync backlog not draining | Outbox in `PreferencesManager`, worker execution in `OutboxSyncWorker` |
| Cloud API failures | `SUPABASE_HTTP` logs and `SupabaseClient` configuration |
| Scrape output empty | Selector chain in `JobScraper.scrapeJobInfo()` |
| Migration crash on launch | Migration chain in `JobDatabase.kt` and `app/schemas/` |

**Key tags:** `Sync`, `SUPABASE_HTTP`, `DeleteJob`, `NextJobId`.

---

## PR Guidelines

- Keep title format: `[component] brief description`.
- Ensure `./gradlew build` and `./gradlew test` pass.
- If schema changed, commit generated JSON under `app/schemas/`.
- Keep architecture docs aligned with active Supabase runtime.

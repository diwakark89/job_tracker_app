# Job Tracker

Android app for capturing LinkedIn job links, scraping details, and tracking the lifecycle of applications with local Room storage and Supabase cloud sync.

## Features

- Share LinkedIn URLs directly into the app via Android share intent.
- Scrape company, title, and description using JSoup selector fallbacks.
- Store jobs locally in Room with offline-first behavior.
- Sync jobs bidirectionally with Supabase (`public.jobs_final`).
- Receive realtime updates from Supabase and reconcile local state.
- Filter/search by company and status in a stateless Compose UI.

## Tech Stack

- Kotlin, Jetpack Compose (Material 3)
- Room + KSP
- Retrofit + OkHttp + Gson
- JSoup
- WorkManager
- Supabase REST + Realtime

## Project Structure

```text
app/src/main/java/com/thewalkersoft/linkedin_job_tracker/
|- MainActivity.kt
|- data/
|  |- JobEntity.kt
|  |- JobDao.kt
|  |- JobDatabase.kt
|- scraper/
|  |- JobScraper.kt
|- sync/
|  |- SupabaseRepository.kt
|  |- SupabaseRealtimeManager.kt
|  |- OutboxSyncWorker.kt
|  |- OutboxWorkScheduler.kt
|- client/
|  |- SupabaseClient.kt
|- service/
|  |- SupabaseApiService.kt
|- viewmodel/
|  |- JobViewModel.kt
|- navigation/
|- ui/
`- util/
```

## Current Data Model Notes

- Local entity uses Room camelCase columns (`companyName`, `jobUrl`, `createdAt`, `updatedAt`).
- Supabase wire format uses canonical snake_case (`company_name`, `job_url`, `saved_at`, `modified_at`).
- Business identity for sync is `job_url` / `jobUrl`.
- Conflict handling uses `updatedAt` with clock-skew tolerance.

See `docs/SUPABASE_SCHEMA.md` for the current schema reference.

## Build and Test

Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat test
powershell -ExecutionPolicy Bypass -File .\tools\doc-lint.ps1
```

Other platforms:

```bash
./gradlew build
./gradlew test
```

## Runtime Configuration

Set these Gradle properties (for `BuildConfig` injection):

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

These are read in `app/build.gradle.kts` and used by `SupabaseClient`.

## Troubleshooting

- Sync not progressing: verify Supabase URL/key and check Logcat tags `Sync` and `SUPABASE_HTTP`.
- Stale details screen route: validate `jobId` route argument resolution in `AppNavigation.kt`.
- Empty scrape result: update selector chain in `JobScraper.scrapeJobInfo()`.
- Migration issues: ensure explicit migrations exist in `JobDatabase.kt` and schema JSON is committed under `app/schemas/`.

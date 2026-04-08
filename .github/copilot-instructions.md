# Job Tracker – Copilot Instructions

## Build & Test Commands

```bash
# Build
./gradlew build

# Clean build
./gradlew clean build

# Run unit tests (JVM only, no device needed)
./gradlew test

# Run a single test class
./gradlew test --tests "com.thewalkersoft.linkedin_job_tracker.scraper.JobScraperTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

- **minSdk 34**, **targetSdk / compileSdk 36**
- Room annotation processor uses **KSP** (not KAPT). Dependency versions live in `gradle/libs.versions.toml`.
- Room schema JSON files are exported to `app/schemas/`. Commit them when the schema changes.

---

## Architecture Overview

**MVVM** with a single `JobViewModel` (extends `AndroidViewModel`) that is the sole source of business logic and state.

```text
MainActivity
  └─ AppNavigation (NavHost)
       ├─ JobListScreen
       └─ JobDetailsScreen

JobViewModel
  ├─ JobDao (Room)                  ← local persistence
  ├─ JobScraper                     ← JSoup scraping (Dispatchers.IO)
  ├─ SupabaseRepository             ← REST sync/pull/push orchestration
  ├─ SupabaseRealtimeManager        ← realtime event stream
  ├─ SupabaseClient/SupabaseApiService
  └─ PreferencesManager             ← SharedPreferences for sync metadata
```

The **Navigation** component passes `jobId: String` as a route argument; the destination screen looks up the job from `allJobs` (the unfiltered StateFlow) rather than using Parcelable.

---

## Key Conventions

### State management
`JobViewModel` exposes two parallel job flows:
- `allJobs` – raw Room query, used for navigation lookups and status-count badges.
- `jobs` – derived via `combine(searchQuery, statusFilter, allJobs)`, used by the list UI.

`_isScraping` is reused for both web-scraping and sync progress; the same `LoadingOverlay` covers both operations.

### JobStatus
The `JobStatus` enum has **7 values**: `SAVED`, `APPLIED`, `INTERVIEW`, `INTERVIEWING`, `OFFER`, `RESUME_REJECTED`, `INTERVIEW_REJECTED`.

- Always use `JobStatus.displayName()` for human-readable strings.
- Always use `parseJobStatus(value: String)` when converting a string back to the enum; it normalises casing/delimiters and maps legacy `REJECTED` to `RESUME_REJECTED`.

### Room / database
- Use `@Upsert` (via `JobDao.upsertJob`) for all insert-or-update operations.
- `jobUrl` is the **unique business key** used for sync matching.
- `updatedAt = System.currentTimeMillis()` must be set on every mutation so sync conflict resolution works correctly.
- Current DB version is **8**. Always add an explicit `Migration` object in `JobDatabase`; `fallbackToDestructiveMigration` is **not** used.

### Supabase sync
- API endpoint is Supabase REST (`/rest/v1/jobs_raw`, `/rest/v1/shared_links`) configured in `SupabaseClient.kt`.
- `SupabaseApiService.upsertJob()` uses `on_conflict=job_url`.
- Use canonical snake_case wire names in JSON (`company_name`, `job_url`, `created_at`, `modified_at`, `is_deleted`, etc.).
- Realtime updates are consumed through `SupabaseRealtimeManager` and applied to Room.

### Web scraping
`JobScraper` is a Kotlin `object` (singleton). It tries CSS selectors in priority order; if all fail it returns a fallback string rather than throwing. When LinkedIn changes its HTML structure, update the selector chains in `scrapeJobInfo()`.

### UI patterns
- All screens are **stateless** Compose functions; state is hoisted to `MainActivity` via `collectAsStateWithLifecycle`.
- User-facing feedback is surfaced through `_message: MutableStateFlow<String?>` — show a snackbar/toast and call `viewModel.clearMessage()` immediately after displaying it.
- Use `@OptIn(ExperimentalMaterial3Api::class)` where needed (`SearchBar`, `SwipeToDismissBox`).

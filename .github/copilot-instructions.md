# LinkedIn Job Tracker Pro – Copilot Instructions

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

```
MainActivity
  └─ AppNavigation (NavHost)
       ├─ JobListScreen
       └─ JobDetailsScreen

JobViewModel
  ├─ JobDao (Room)          ← local persistence
  ├─ JobScraper             ← JSoup scraping (Dispatchers.IO)
  ├─ SyncService            ← bi-directional Google Sheets sync
  ├─ RetrofitClient         ← Retrofit singleton (Google Apps Script web app)
  └─ PreferencesManager     ← SharedPreferences for last-sync timestamp
```

The **Navigation** component passes `jobId: Long` as a route argument; the destination screen looks up the job from `allJobs` (the unfiltered StateFlow) rather than using Parcelable.

---

## Key Conventions

### State management
`JobViewModel` exposes two parallel job flows:
- `allJobs` – raw Room query, used for navigation lookups and status-count badges.
- `jobs` – derived via `combine(searchQuery, statusFilter, allJobs)`, used by the list UI.

`_isScraping` is reused for both web-scraping and sync progress; the same `LoadingOverlay` covers both operations.

### JobStatus
The `JobStatus` enum has **6 values**: `SAVED`, `APPLIED`, `INTERVIEWING`, `OFFER`, `RESUME_REJECTED`, `INTERVIEW_REJECTED`.

- Always use `JobStatus.displayName()` for human-readable strings (handles hyphenated names).
- Always use `parseJobStatus(value: String)` when converting a string back to the enum — it normalises casing, hyphens, and maps the legacy `"REJECTED"` string to `RESUME_REJECTED`.

### Room / database
- Use `@Upsert` (via `JobDao.upsertJob`) for all insert-or-update operations.
- `jobUrl` is the **unique business key** used for sync matching (not the auto-generated `id`).
- `lastModified = System.currentTimeMillis()` must be set on every mutation so sync conflict resolution works correctly.
- Current DB version is **3**. Always add an explicit `Migration` object in `JobDatabase`; `fallbackToDestructiveMigration` is **not** used.

### Google Sheets sync
- API endpoint is a Google Apps Script web app. The `DEPLOYMENT_ID` constant in `RetrofitClient.kt` must be updated whenever the script is redeployed.
- `SyncService.performBidirectionalSync()` matches jobs by `jobUrl`. Conflict resolution: **newer `lastModified` wins**; on a tie, the local app takes precedence.
- Every local mutation (add, update status, edit, delete) immediately calls the corresponding Retrofit method in addition to the Room upsert.
- The companion Google Apps Script source is `GoogleSheetUpdateScript.gs` in the project root. The expected sheet name is **"Linkedin Job Tracker Sheet"**.

### Web scraping
`JobScraper` is a Kotlin `object` (singleton). It tries CSS selectors in priority order; if all fail it returns a fallback string rather than throwing. When LinkedIn changes its HTML structure, update the selector chains in `scrapeJobInfo()`.

### UI patterns
- All screens are **stateless** Compose functions; state is hoisted to `MainActivity` via `collectAsStateWithLifecycle`.
- User-facing feedback is surfaced through `_message: MutableStateFlow<String?>` — show a snackbar/toast and call `viewModel.clearMessage()` immediately after displaying it.
- Use `@OptIn(ExperimentalMaterial3Api::class)` where needed (`SearchBar`, `SwipeToDismissBox`).

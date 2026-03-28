# AGENTS.md

## Project Overview

LinkedIn Job Tracker Pro is a single-module Android app that lets users save, track, and manage LinkedIn job applications. Users share a LinkedIn job URL from their browser/app to the Android share sheet, the app scrapes job details with JSoup, stores them locally with Room, and optionally syncs bidirectionally with a Google Sheet backend via a Google Apps Script web app.

**Key technologies:** Kotlin · Jetpack Compose · Room (KSP) · Retrofit · OkHttp · JSoup · Navigation Compose · Material 3

**SDK targets:** `minSdk 34` (Android 14) · `compileSdk / targetSdk 36` (Android 15) · Java 11

**Architecture:** MVVM — stateless Compose screens, a single `JobViewModel` (AndroidViewModel) as the sole business-logic owner, Room for persistence, and Retrofit for the Google Sheets API.

---

## Project Structure

```
app/src/main/java/com/thewalkersoft/linkedin_job_tracker/
├── MainActivity.kt                  # Entry point; handles Share Intent; hoists state
├── data/
│   ├── JobEntity.kt                 # @Entity, JobStatus enum, displayName(), parseJobStatus()
│   ├── JobDao.kt                    # @Dao: getAllJobs, getAllJobsOnce, upsertJob, deleteJob, getJobByUrl, getMaxId
│   └── JobDatabase.kt              # Room DB (version 3), explicit migrations v1→v2→v3
├── viewmodel/
│   └── JobViewModel.kt             # StateFlows: allJobs, jobs, searchQuery, statusFilter, _isScraping, _message
├── scraper/
│   └── JobScraper.kt               # Kotlin object; JSoup selectors with fallbacks; runs on Dispatchers.IO
├── sync/
│   └── SyncService.kt              # Bidirectional Google Sheets sync; conflict resolution by lastModified
├── client/
│   └── RetrofitClient.kt           # Retrofit singleton; DEPLOYMENT_ID constant for Apps Script URL
├── service/
│   ├── GoogleSheetApiService.kt    # Retrofit API interface (uploadJob, updateJob, deleteJob, getAllJobs)
│   └── GoogleSheetResponse.kt      # Response DTO
├── navigation/
│   ├── Screen.kt                   # Route definitions (JobList, JobDetails/{jobId})
│   └── AppNavigation.kt            # NavHost; resolves jobId from allJobs (no Parcelable), shows missing-state screen when stale id is opened
├── ui/
│   ├── screens/
│   │   ├── JobListScreen.kt        # Search bar, status filter chips, swipe-to-delete, sync button
│   │   └── JobDetailsScreen.kt     # Full job detail view
│   ├── components/
│   │   ├── JobCard.kt              # Expandable card with status badge
│   │   ├── EditJobDialog.kt        # Modal dialog for editing job fields
│   │   └── LoadingOverlay.kt       # Full-screen loading spinner (scraping + sync)
│   └── theme/
│       ├── Color.kt · Theme.kt · Type.kt   # Material 3 theme
└── util/
    └── PreferencesManager.kt       # SharedPreferences: stores last-sync timestamp
```

**Google Apps Script source:** `GoogleSheetUpdateScript.gs` at the repo root  
**Room schema exports:** `app/schemas/` — commit whenever the schema changes

---

## Build Commands

```bash
# Standard build
./gradlew build

# Clean build (use when KSP or Room schema generation seems stale)
./gradlew clean build

# Assemble debug APK only
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

> On Windows use `gradlew.bat` instead of `./gradlew`.

---

## Testing Instructions

```bash
# Run all JVM unit tests (no device required)
./gradlew test

# Run a single test class
./gradlew test --tests "com.thewalkersoft.linkedin_job_tracker.scraper.JobScraperTest"

# Run instrumented tests (requires connected device or running emulator)
./gradlew connectedAndroidTest
```

**Test locations:**

| Type | Path |
|------|------|
| Unit tests | `app/src/test/java/com/thewalkersoft/linkedin_job_tracker/` |
| Instrumented tests | `app/src/androidTest/java/com/thewalkersoft/linkedin_job_tracker/` |

**Existing test classes:**
- `JobScraperTest` — covers `normalizeLineBreaks` and `extractTextFromHtmlWithLineBreaks`
- `ExampleUnitTest` — placeholder arithmetic test
- `ExampleInstrumentedTest` — verifies app package name on device

When adding new functionality, add corresponding unit tests in `src/test/`. Scraper logic is the most testable layer; prefer testing selector chains there before wiring to the UI.

---

## Code Style and Conventions

### Kotlin / Compose
- All Compose screens are **stateless**; state is hoisted to `MainActivity` (currently `collectAsState` for most flows, `collectAsStateWithLifecycle` for `lastSyncTime`).
- Use `@OptIn(ExperimentalMaterial3Api::class)` on composables that use `SearchBar` or `SwipeToDismissBox`.
- No raw `enum.name` for user-facing strings — always call `JobStatus.displayName()`.
- Prefer `combine(flow1, flow2, flow3) { ... }` for derived state rather than intermediate `MutableStateFlow` mutations.

### Room / Database
- Always use `@Upsert` (`JobDao.upsertJob`) for insert-or-update; never use `@Insert` with `OnConflictStrategy`.
- Set `lastModified = System.currentTimeMillis()` on **every** mutation (add, update, delete-and-re-add).
- `jobUrl` is the **unique business key** — used for sync matching, `getJobByUrl`, and deduplication.
- DB is currently **version 3**. When changing the schema:
  1. Bump `version` in `JobDatabase.kt`.
  2. Add an explicit `Migration(old, new)` object — **do not use** `fallbackToDestructiveMigration`.
  3. KSP will regenerate schema JSON under `app/schemas/` — commit this file.

### JobStatus
- **6 valid values:** `SAVED`, `APPLIED`, `INTERVIEWING`, `OFFER`, `RESUME_REJECTED`, `INTERVIEW_REJECTED`
- UI labels: `JobStatus.displayName()` — returns hyphenated form for `RESUME_REJECTED` / `INTERVIEW_REJECTED`.
- Parsing inbound strings: `parseJobStatus(value)` — normalises casing/hyphens, maps legacy `"REJECTED"` → `RESUME_REJECTED`.

### State Management in JobViewModel
- `allJobs` — raw `Flow<List<JobEntity>>` from Room; used for navigation lookups and status-count badges.
- `jobs` — derived via `combine(searchQuery, statusFilter, allJobs)` — used by the list UI.
- `_isScraping: MutableStateFlow<Boolean>` — reused for both scraping and sync; drives `LoadingOverlay`.
- `_message: MutableStateFlow<String?>` — surface user feedback; UI shows a snackbar then calls `viewModel.clearMessage()`.

### External API Mutations
Every local mutation (add, update status, edit, delete) must **also** call the corresponding Retrofit method from `JobViewModel`:
- `uploadJob(job)` — new job
- `updateJob(job)` — status or field change
- `deleteJob(job)` — deletion (current API posts the full `JobEntity`)

---

## Google Sheets Integration

- The backend is a **Google Apps Script web app** deployed from `GoogleSheetUpdateScript.gs`.
- Expected sheet tab name: **`Linkedin Job Tracker Sheet`**
- The `DEPLOYMENT_ID` constant in `RetrofitClient.kt` must be updated whenever the Apps Script is redeployed.
- Sync conflict resolution: **newer `lastModified` wins**; on a tie the local app takes precedence.
- See `BIDIRECTIONAL_SYNC.md` for full sync protocol documentation.

---

## Web Scraping

- `JobScraper` is a Kotlin `object` (singleton) that runs on `Dispatchers.IO`.
- It tries CSS selectors in priority order; if all fail it returns a fallback string (never throws).
- User-Agent is set to a desktop browser string to avoid bot blocking.
- HTTP timeout is 10 seconds.
- When LinkedIn changes its HTML structure, update the selector chains in `JobScraper.scrapeJobInfo()`.

---

## Navigation

- Routes are defined in `Screen.kt` as sealed class entries.
- `JobDetailsScreen` receives a `jobId: Long` argument from the route, then resolves the full `JobEntity` from `allJobs` (not from a Parcelable). If no match is found, `JobDetailsMissingScreen` is shown with a back action.
- Do not pass `JobEntity` objects as navigation arguments — this is intentional to avoid serialisation issues.

---

## Debugging Anchors

| Symptom | Where to look |
|---------|---------------|
| Details screen shows "Job no longer available." | `Screen.kt` route format, `AppNavigation.kt` job lookup from `allJobs`, and `JobDetailsMissingScreen` in `JobDetailsScreen.kt` |
| Sync appears stale or doesn't reflect changes | `lastModified` updates in `JobViewModel`, sheet-side `handleUpdateJob` / `handleDeleteJob` in the Apps Script |
| Scraping returns empty strings | CSS selectors in `JobScraper.scrapeJobInfo()` — LinkedIn may have changed markup |
| Retrofit calls fail silently | OkHttp logging interceptor (tag `HTTP`); check `DEPLOYMENT_ID` in `RetrofitClient.kt` |
| Room crashes on upgrade | Missing or wrong `Migration` in `JobDatabase.kt` |
| Navigation ID mismatch | Log tag `NextJobId` in `JobViewModel` |

**Key log tags:** `Sync`, `SyncService`, `DeleteJob`, `HTTP`, `NextJobId`

---

## Dependency Overview

| Category | Library | Version |
|----------|---------|---------|
| Compose BOM | `androidx.compose:compose-bom` | 2024.09.00 |
| Room | `androidx.room:room-runtime/ktx/compiler` | 2.8.4 |
| Navigation | `androidx.navigation:navigation-compose` | 2.8.0 |
| Retrofit | `com.squareup.retrofit2:retrofit` + `converter-gson` | 2.11.0 |
| OkHttp logging | `com.squareup.okhttp3:logging-interceptor` | 4.12.0 |
| JSoup | `org.jsoup:jsoup` | 1.22.1 |
| Lifecycle (Compose) | `androidx.lifecycle:lifecycle-runtime-compose` | 2.6.1 |
| KSP | `com.google.devtools.ksp` | 2.3.2 |
| AGP | `com.android.application` | 9.0.1 |
| Kotlin | `org.jetbrains.kotlin` | 2.2.10 |

All versions are managed in `gradle/libs.versions.toml`.

---

## Pull Request Guidelines

- Title format: `[component] Brief description` (e.g., `[sync] Fix conflict resolution tie-breaking`)
- Before committing: ensure `./gradlew build` succeeds and `./gradlew test` is green.
- If the Room schema changed, confirm `app/schemas/` JSON is updated and committed.
- If `DEPLOYMENT_ID` changes, update `RetrofitClient.kt` and document the new deployment URL.
- Do not use `fallbackToDestructiveMigration` — always write an explicit `Migration`.

---

## Common Gotchas

- **KSP vs KAPT:** This project uses KSP for Room annotation processing — do not add KAPT dependencies.
- **minSdk 34:** You may freely use Android 14 APIs without version guards.
- **`_isScraping` dual purpose:** This flag covers both scraping and sync — don't add a separate loading state for sync.
- **`allJobs` vs `jobs`:** `allJobs` is unfiltered and must stay that way. `jobs` is the filtered/searched list for the UI. Navigation and badge counts always read from `allJobs`.
- **Share Intent parsing:** `MainActivity` receives `Intent.ACTION_SEND` with `text/plain` and extracts the LinkedIn URL via regex before handing off to `JobViewModel.scrapeAndSaveJob()`.


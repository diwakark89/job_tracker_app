# LinkedIn Job Tracker Pro

A modern Android app built with Jetpack Compose, Room Database, and JSoup for tracking LinkedIn job applications.

## Features

### 📱 Core Functionality
- **Share Intent Integration**: Share LinkedIn job postings directly from LinkedIn app
- **Web Scraping**: Automatically scrapes job descriptions using JSoup
- **Local Storage**: Persists jobs using Room Database
- **Google Sheets Sync**: Bi-directional sync with conflict resolution (`lastModified` wins)
- **Search**: Filter jobs by company name
- **Status Management**: Track application progress with color-coded chips
- **Swipe to Delete**: Easy job removal with swipe gesture

### 🎨 UI Features
- **Material 3 Design**: Modern UI with Material Design 3 components
- **Expandable Cards**: Tap to expand/collapse job details
- **SearchBar**: Quick search functionality in the top app bar
- **Loading Overlay**: Visual feedback during web scraping and sync
- **Color-Coded Status**:
  - 🟢 Green: Offer
  - 🔴 Red: Resume Rejected / Interview Rejected
  - 🟡 Yellow: Interviewing
  - 🔵 Blue: Applied
  - ⚪ Gray: Saved

## Architecture

### MVVM Pattern
- **Model**: `JobEntity` (Room Entity)
- **ViewModel**: `JobViewModel` (manages state and business logic)
- **View**: Jetpack Compose UI screens

### Project Structure
```
app/src/main/java/com/thewalkersoft/linkedin_job_tracker/
├── MainActivity.kt           # Entry point; handles Share Intent; hoists state
├── data/
│   ├── JobEntity.kt          # @Entity, JobStatus enum, displayName(), parseJobStatus()
│   ├── JobDao.kt             # @Dao: getAllJobs, getAllJobsOnce, upsertJob, deleteJob, getJobByUrl, getMaxId
│   └── JobDatabase.kt        # Room DB (version 3), explicit migrations v1→v2→v3
├── scraper/
│   └── JobScraper.kt         # JSoup web scraping logic
├── sync/
│   └── SyncService.kt        # Bidirectional Google Sheets sync; conflict resolution by lastModified
├── client/
│   └── RetrofitClient.kt     # Retrofit singleton; DEPLOYMENT_ID constant for Apps Script URL
├── service/
│   ├── GoogleSheetApiService.kt
│   └── GoogleSheetResponse.kt
├── navigation/
│   ├── Screen.kt
│   └── AppNavigation.kt
├── viewmodel/
│   └── JobViewModel.kt       # ViewModel with StateFlows
├── ui/
│   ├── components/
│   │   ├── JobCard.kt        # Expandable job card
│   │   ├── EditJobDialog.kt
│   │   └── LoadingOverlay.kt # Scraping + sync indicator
│   ├── screens/
│   │   ├── JobListScreen.kt
│   │   └── JobDetailsScreen.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── util/
    └── PreferencesManager.kt # Last-sync timestamp persistence
```

## Data Model

### JobEntity
```kotlin
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val jobTitle: String = "",
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)
```

### JobStatus Enum
- SAVED
- APPLIED
- INTERVIEWING
- OFFER
- RESUME_REJECTED
- INTERVIEW_REJECTED

## Database Operations

### JobDao
- `getAllJobs()`: Flow<List<JobEntity>>
- `getAllJobsOnce()`: List<JobEntity>
- `upsertJob(job: JobEntity)`: Insert or update
- `deleteJob(jobId: Long)`: Remove job
- `getJobByUrl(url: String)`: Find by business key
- `getMaxId()`: Max ID across local records

Every local mutation also syncs via Retrofit; deletions call `deleteJob(@Body job: JobEntity)` on the Google Sheets API.

## Web Scraping

### Scraper Logic
The `JobScraper` uses JSoup with:
- **User-Agent**: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
- **Timeout**: 10 seconds
- **Selectors**:
  1. `.description__text`
  2. `meta[name=description]`
  3. `.show-more-less-html__markup`
  4. `div[class*=description]`

## State Management

### ViewModel StateFlows
- `allJobs`: StateFlow<List<JobEntity>>
- `jobs`: StateFlow<List<JobEntity>> (derived from `combine(searchQuery, statusFilter, allJobs)`)
- `searchQuery`: StateFlow<String>
- `statusFilter`: StateFlow<JobStatus?>
- `isScraping`: StateFlow<Boolean>
- `message`: StateFlow<String?>
- `lastSyncTime`: StateFlow<String>

### Intent Handling
Parses shared text from LinkedIn:
- Accepts `Intent.ACTION_SEND` with `text/plain`
- Extracts the first URL using regex
- Automatically scrapes and saves job

## How to Use

### 1. Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run the app

### 2. Adding Jobs
1. Open LinkedIn app
2. Navigate to a job posting
3. Tap "Share" button
4. Select "LinkedIn Job Tracker Pro"
5. App automatically scrapes and saves the job

### 3. Managing Jobs
- **Search**: Use the search bar to filter by company
- **Expand**: Tap a card to see full description
- **Change Status**: Tap the status chip to update
- **Delete**: Swipe left on a card to remove

## Permissions

### AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### Intent Filter
```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

## Dependencies

### Gradle (libs.versions.toml)
```toml
[versions]
room = "2.8.4"
jsoup = "1.22.1"
compose-bom = "2024.09.00"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }
```

## Technical Details

### Compose Features Used
- `@OptIn(ExperimentalMaterial3Api::class)`
- `SearchBar` - Top app bar with search
- `SwipeToDismissBox` - Swipe to delete
- `LazyColumn` - Efficient list rendering
- `animateContentSize()` - Smooth expand/collapse
- `FilterChip` - Status selection
- `CircularProgressIndicator` - Loading state

### Coroutines
- `viewModelScope` - ViewModel lifecycle-aware scope
- `Dispatchers.IO` - Background thread for network/database
- `Flow` - Reactive data streams
- `StateFlow` - State management

### Room Features & Configuration
- **@Upsert**: Always use `@Upsert` (never `@Insert` with `OnConflictStrategy`)
- **@TypeConverters**: `JobStatus` enum converted to/from String
- **Flow**: Observable queries for reactive UI binding
- **Schema export**: `exportSchema = true` in `@Database` annotation
- **KSP compilation**: Uses KSP processor (configured in `build.gradle.kts`)

## Testing

### Example Test Cases
1. Share a LinkedIn job URL
2. Verify job is scraped and saved
3. Search for company name
4. Change job status
5. Swipe to delete job
6. Expand/collapse card

## Future Enhancements

### Potential Features
- [ ] Export to CSV
- [ ] Calendar reminders for interviews
- [ ] Notes for each job
- [ ] Contact tracking
- [ ] Analytics dashboard
- [ ] Dark theme customization
- [ ] Backup/restore

## Troubleshooting

### Common Issues

#### App crashes on share
**Problem**: App crashes when sharing from LinkedIn

**Solutions**:
1. Verify `INTERNET` permission in `AndroidManifest.xml` ✓
2. Check intent filter matches `action.SEND` with `text/plain` ✓
3. Examine Logcat for `MainActivity` intent parsing errors
4. Ensure device has active internet connection
5. Try re-sharing if network is unstable

#### No description scraped / "Unable to scrape" message
**Problem**: Job card shows empty description or fallback text

**Possible causes**:
1. LinkedIn updated HTML structure (most common)
2. Network timeout (check connection)
3. URL parsing failed in regex
4. JSoup selector chains all failed

**Fix**:
1. Check Logcat for scraper error details
2. Inspect LinkedIn job page HTML (view source)
3. Update CSS selectors in `JobScraper.kt` selectors list
4. Add new selector to priority chain

#### Jobs not appearing in list
**Problem**: Shared job doesn't appear after scraping

**Debug steps**:
1. Check if loading overlay appeared (network call made)
2. Search for company name (may be filtered)
3. Verify database insert in Logcat (`Room.*` logs)
4. Check ViewModel state with debugger breakpoint
5. Ensure job URL is valid and accessible

#### Sync not working or appearing stale
**Problem**: Changes not reflected in Google Sheets or vice versa

**Troubleshooting**:
1. Verify internet connection and DEPLOYMENT_ID in `RetrofitClient.kt`
2. Check Google Apps Script is deployed (visit deployment URL directly)
3. Confirm sheet name is "Linkedin Job Tracker Sheet" (exact case)
4. Verify `lastModified` timestamp is being updated on mutations
5. Check Logcat for `Sync` tag messages
6. Ensure Google account has access to the sheet

#### Build errors or Gradle sync issues
**Problem**: `./gradlew build` fails or Gradle sync hangs

**Solutions**:
1. Clean project: `./gradlew clean`
2. Invalidate Gradle cache: `./gradlew --stop`
3. Update gradle.properties with correct Gradle path
4. Ensure Java 11+ is installed and set in JAVA_HOME
5. Check that KSP is configured (not KAPT)

#### Room migration errors
**Problem**: "Migration from X to Y is required" error on app start

**Solutions**:
1. Verify migration objects exist in `JobDatabase.kt`
2. Check migration SQL is correct for your schema change
3. Never use `fallbackToDestructiveMigration()` (data loss)
4. Commit `app/schemas/` JSON files to version control
5. Ensure migration version numbers are sequential

#### Retrofit API calls fail silently
**Problem**: Cloud sync doesn't work but no error appears

**Debug**:
1. Enable OkHttp logging interceptor (already configured)
2. Check Logcat for `HTTP` tag messages
3. Verify DEPLOYMENT_ID is current (redeploy Apps Script if needed)
4. Test Apps Script URL directly in browser
5. Check Google Sheet sheet permissions and sharing

## License

This project is for educational purposes.

## Author

TheWalkerSoft

---

**Built with ❤️ using Jetpack Compose**


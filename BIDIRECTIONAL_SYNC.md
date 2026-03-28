# Bi-Directional Sync Implementation

## Overview
The application now supports **bi-directional synchronization** between the local Android database and Google Sheets. This means data flows both ways, keeping both sources up-to-date with intelligent conflict resolution.

## Decision Log
- **Intent:** Use tombstone deletes (`is_deleted=true`) to prevent cross-device resurrection while keeping deletion history.
  **Tradeoff:** Tombstoned rows remain in storage and are hidden from the app list.
  **Invariant:** Repeated realtime tombstone updates and later pulls must remain idempotent and keep the job deleted.
- **Intent:** Prefer cloud on exact `lastModified` ties for deterministic convergence.
  **Tradeoff:** Unsynced local restore can be overwritten when timestamps are equal.
  **Invariant:** Equal-timestamp conflicts always resolve to the cloud copy.
- **Intent:** Keep permissive RLS only for development velocity.
  **Tradeoff:** Current anon/authenticated allow-all policies are unsafe for production.
  **Invariant:** Tightening policies is tracked as a separate hardening task only.
  **TODO(v1.0-prod-hardening):** Replace permissive policies with owner-scoped predicates and service-role boundaries.

## Features

### 1. Two-Way Data Flow
- **Upload**: Jobs that exist only in the app are uploaded to Google Sheets
- **Download**: Jobs that exist only in Google Sheets are added to the app
- **Update**: Jobs that exist in both places are intelligently synchronized

### 2. Conflict Resolution Rules

When a job exists in both the app and Google Sheets, the system uses these rules to resolve conflicts:

#### Rule 1: Uniqueness
- Jobs are matched using **Job URL** as the unique identifier
- ID + URL combination ensures accurate matching

#### Rule 2: Status Matching
- **If statuses are the same** → App data takes precedence
  - The app version is considered authoritative
  - Sheet is updated with app data
  
#### Rule 3: Timestamp-Based Resolution
- **If statuses are different** → Most recent modification wins
  - Uses `lastModified` timestamp to determine which is newer
  - The newer version (app or sheet) overwrites the older one

#### Rule 4: No Changes
- If all fields are identical → No action taken
- Reduces unnecessary network calls

### 3. Sync Statistics
After each sync, the app displays:
- ⬆️ **Uploaded**: Number of jobs added to Google Sheets
- ⬇️ **Downloaded**: Number of jobs added to the app
- 🔄 **Updated**: Number of jobs modified in either location
- ⚠️ **Conflicts**: Number of conflicts where app took precedence

## Technical Implementation

### Database Schema Changes
```kotlin
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis() // NEW FIELD
)
```

### Google Sheets Schema
```
Column A: ID
Column B: Job URL
Column C: Company Name
Column D: Description
Column E: Status
Column F: Last Updated
Column G: Last Modified (NEW COLUMN)
```

### Sync Service Architecture

```
┌─────────────────┐
│   Android App   │
│   (Local DB)    │
└────────┬────────┘
         │
    Sync Request
         │
         ▼
┌─────────────────┐
│  SyncService    │◄── Compares data from both sources
│                 │    Applies conflict resolution rules
│                 │    Updates both locations as needed
└────────┬────────┘
         │
    API Calls
         │
         ▼
┌─────────────────┐
│ Google Sheets   │
│ (Cloud Storage) │
└─────────────────┘
```

## Usage

### Manual Sync
1. Open the app
2. Tap the **Sync** button (cloud icon) in the top bar
3. Wait for sync to complete
4. Check the notification for sync statistics

### Automatic Behavior
- When you change a job status → Immediately synced to sheet
- When you edit job details → Immediately synced to sheet
- When you add a new job → Immediately synced to sheet
- When you manually sync → Full bi-directional sync performed

## Example Scenarios

### Scenario 1: App Takes Precedence
```
App:   Company A | Status: APPLIED    | lastModified: 1000
Sheet: Company A | Status: APPLIED    | lastModified: 900

Result: Sheet is updated with app data (same status → app wins)
```

### Scenario 2: Newer Wins
```
App:   Company B | Status: SAVED       | lastModified: 1000
Sheet: Company B | Status: INTERVIEWING | lastModified: 2000

Result: App is updated with sheet data (sheet is newer)
```

### Scenario 3: Upload Only
```
App:   Company C | Status: SAVED | lastModified: 1000
Sheet: [Does not exist]

Result: Company C is uploaded to sheet
```

### Scenario 4: Download Only
```
App:   [Does not exist]
Sheet: Company D | Status: OFFER | lastModified: 2000

Result: Company D is downloaded to app
```

## Migration Notes

### Database Version Update
- Old version: `2`
- New version: `3`
- Migration strategy: **Explicit Migration objects** (safe, non-destructive)
- **No data loss**: All existing jobs are preserved during migration
- Migrations: `1→2` (add lastModified), `2→3` (add jobTitle)

### Google Apps Script Update
You need to redeploy the Google Apps Script with the updated code:

1. Open Google Sheets
2. Go to **Extensions > Apps Script**
3. Replace the code with the updated `GoogleSheetUpdateScript.gs`
4. Click **Deploy > New Deployment**
5. Select **Web app**
6. Update the `DEPLOYMENT_ID` in `RetrofitClient.kt` if needed

## Testing Checklist

- [ ] Add job in app → Verify it appears in sheet
- [ ] Add job in sheet → Sync → Verify it appears in app
- [ ] Update status in app → Verify sheet is updated
- [ ] Update status in sheet → Sync → Verify app is updated
- [ ] Same status, different data → Verify app data wins
- [ ] Different status, app newer → Verify sheet gets app data
- [ ] Different status, sheet newer → Verify app gets sheet data
- [ ] Delete job in app → It stays deleted (not re-downloaded)

## Troubleshooting

### Sync Not Working
1. Check internet connection
2. Verify Google Apps Script is deployed correctly
3. Check Logcat for sync errors
4. Ensure sheet name is "Linkedin Job Tracker Sheet"

### Conflicts Not Resolving
1. Check that `lastModified` column exists in sheet
2. Verify timestamps are being set correctly
3. Look for "Conflict resolved" messages in Logcat

### Data Loss After Update
1. This is expected due to database migration
2. Simply tap **Sync** to restore all jobs from Google Sheets
3. For future updates, proper migration will be implemented

## Future Enhancements

- [ ] Automatic periodic sync (background)
- [ ] Sync conflict UI for user resolution
- [ ] Batch operations for better performance
- [ ] Offline queue for failed syncs
- [ ] Proper Room database migration (non-destructive)
- [ ] Sync history and logs

## Code References

- **SyncService**: `app/src/main/java/.../sync/SyncService.kt`
- **JobViewModel**: `app/src/main/java/.../viewmodel/JobViewModel.kt`
- **JobEntity**: `app/src/main/java/.../data/JobEntity.kt`
- **Google Script**: `GoogleSheetUpdateScript.gs`

---

**Last Updated**: March 25, 2026
**Version**: 3.0.0


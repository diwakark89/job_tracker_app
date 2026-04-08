# Quick Start Guide - Job Tracker

## 🚀 Getting Started

### Prerequisites
- Android Studio Koala or later
- Android device/emulator with API 34+
- Active internet connection

### Installation Steps

1. **Open the Project**
   ```bash
   # Navigate to project directory
   cd LinkedIn_Job_Tracker
   
   # Open with Android Studio or sync Gradle
   ./gradlew build
   ```

2. **Sync Dependencies**
   - Open in Android Studio
   - Wait for Gradle sync to complete
   - All dependencies should download automatically

3. **Run the App**
   - Click "Run" (Shift+F10)
   - Select your device/emulator
   - App will install and launch

## 📱 Using the App

### Adding Your First Job

#### Method 1: Share from LinkedIn
1. Open LinkedIn mobile app
2. Find any job posting
3. Tap the **Share** button
5. Select **Job Tracker** from the share menu
5. The app will:
   - Open automatically
   - Show loading overlay
   - Scrape the job description
   - Save to your local database
   - Display in the job list

#### Method 2: Test with Sample Data
If you don't have LinkedIn installed, you can test by manually adding this code to `MainActivity.onCreate()` (temporary testing):

```kotlin
// Test scraping
viewModel.scrapeAndSaveJob(
    url = "https://www.linkedin.com/jobs/view/123456789"
)
```

### Managing Jobs

#### Search for Jobs
1. Tap the **search bar** at the top
2. Type a company name (e.g., "Google", "Microsoft")
3. Jobs are filtered in real-time
4. Clear search to see all jobs

#### View Job Details
1. **Tap any job card** to expand
2. Card shows:
   - Company name
   - Date added
   - Full job description (expanded)
   - LinkedIn URL (expanded)
3. **Tap again** to collapse

#### Change Job Status
1. Tap the **status chip** (colored button) on any job card
2. Select new status from dropdown:
   - 📥 **Saved** (gray) - Just saved for later
   - 📝 **Applied** (blue) - Application submitted
   - 📞 **Interview** (yellow) - Interview round scheduled
   - 💬 **Interviewing** (yellow) - In interview process
   - ✅ **Offer** (green) - Received offer
   - ❌ **Resume-Rejected** (red) - Rejected before interview
   - ❌ **Interview-Rejected** (red) - Rejected after interview
3. Status updates immediately

#### Delete a Job
1. **Swipe left** on any job card
2. Delete icon appears
3. Card is removed from list
4. Deletion is permanent

## 🎯 Example Workflow

### Real-World Usage Scenario

```
Day 1: Job Hunting
├─ Share 5 jobs from LinkedIn → All auto-scraped & saved
├─ Status: All show "Saved" (gray)
└─ Review descriptions in app

Day 3: Applied
├─ Applied to 3 companies
├─ Tap status chip → Change to "Applied" (blue)
└─ Search "Google" to find specific application

Day 7: Interview Scheduled
├─ Got interview call from 1 company
├─ Change status to "Interviewing" (yellow)
└─ Review job description before interview

Day 10: Results
├─ 1 offer → Change to "Offer" (green) ✅
├─ 1 resume rejection → Change to "Resume-Rejected" (red)
├─ 1 interview rejection → Change to "Interview-Rejected" (red)
└─ Still "Interviewing" with others

Week 2: Clean Up
├─ Swipe left on rejected jobs to delete
└─ Keep offer and active interviews
```

## 🔍 Feature Deep Dive

### Search Functionality
- **Real-time filtering**: Results update as you type
- **Case-insensitive**: "google" matches "Google"
- **Partial matching**: "Micro" finds "Microsoft"
- **Fast**: Uses Room's Flow queries

### Web Scraping
- **Automatic**: Triggered on share intent
- **Smart selectors**: Tries multiple HTML patterns
- **Error handling**: Shows message if scraping fails
- **Background thread**: Doesn't block UI

### Data Persistence
- **Local database**: All data stored on device
- **No internet required**: View jobs offline
- **Instant sync**: UI updates automatically
- **Reliable**: Room database with transactions

## 🎨 UI Tips

### Navigation
- **Pull to refresh**: Not implemented yet (future feature)
- **Scroll**: LazyColumn is efficient for 1000+ jobs
- **Edge-to-edge**: App uses full screen

### Visual Indicators
- **Loading overlay**: Dimmed screen + spinner = scraping/syncing in progress
- **Empty state**: Friendly message when no jobs
- **Status colors**: 
  - 🟢 Green: Offer
  - 🔴 Red: Resume Rejected / Interview Rejected
  - 🟡 Yellow: Interviewing
  - 🔵 Blue: Applied
  - ⚪ Gray: Saved
- **Swipe feedback**: Delete icon shows on swipe

## 🛠️ Troubleshooting

### App crashes on share
**Problem**: App crashes when sharing from LinkedIn

**Solutions**:
1. Check INTERNET permission in manifest ✓
2. Verify intent filter is correct ✓
3. Check Logcat for error messages
4. Ensure device has internet connection

### No description scraped
**Problem**: Job shows "Unable to scrape" message

**Possible causes**:
1. LinkedIn changed HTML structure → Update selectors
2. Network timeout → Check connection
3. URL not accessible → Try different job

**Fix**: Update selectors in `JobScraper.kt`:
```kotlin
val description = doc.select(".your-new-selector").text()
```

### Jobs not appearing
**Problem**: Shared job doesn't appear in list

**Debug steps**:
1. Check if scraping overlay appeared
2. Verify database save in Logcat
3. Try searching for company name
4. Check ViewModel state in debugger

### Build errors
**Problem**: Gradle build fails

**Solutions**:
1. Clean project: `./gradlew clean`
2. Invalidate caches: Android Studio → File → Invalidate Caches
3. Update gradle.properties ✓ (already done)
4. Sync project with Gradle files

## 📊 Understanding the Data

### Database Schema
```
Local Room table: jobs
├─ id: String (UUID primary key)
├─ companyName: String (searchable)
├─ jobUrl: String (LinkedIn URL, unique business key)
├─ jobDescription: String (scraped text)
├─ status: JobStatus (stored as display-name string)
├─ createdAt: Long (epoch millis)
└─ updatedAt: Long (epoch millis)

Supabase wire table: jobs_raw
├─ job_status (maps to local JobStatus)
├─ pipeline_stage
├─ content_hash
├─ external_id
└─ location
```

### Status Values
```kotlin
enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEW,
    INTERVIEWING,
    OFFER,
    RESUME_REJECTED,
    INTERVIEW_REJECTED
}
```

## 🔐 Privacy & Security

### Data Storage
- **Local-first**: Data is saved in Room first and remains usable offline
- **Cloud sync**: Supabase sync is supported when configured
- **Device storage**: Data saved in app's private directory
- **No analytics**: No tracking or telemetry
- **No permissions**: Only INTERNET for scraping

### Data Access
- **App-only**: Other apps cannot access your job data
- **Secure**: Android's app sandboxing protects data
- **Backup**: Android Auto Backup may include database

## 📈 Best Practices

### Job Management
1. **Save immediately**: Share jobs as you find them
2. **Update status**: Keep statuses current for accurate tracking
3. **Regular cleanup**: Delete rejected/expired jobs monthly
4. **Search frequently**: Use search to locate specific applications
5. **Review descriptions**: Expand cards to read full job details

### Performance Tips
1. **Batch sharing**: Share multiple jobs in succession (scraping runs on IO thread)
2. **Minimize manual sync**: App auto-syncs on every mutation
3. **Offline capability**: App works offline—jobs sync when connection returns
4. **Storage**: Job data stored locally—no cloud dependency except optional sync
5. **Battery**: Scraping runs on background thread, doesn't drain battery

### Status Update Best Practices
1. Start all jobs as **"Saved"** (gray)
2. Change to **"Applied"** when you submit the application
3. Update to **"Interviewing"** when an interview is scheduled
4. Mark **"Offer"** (green) or **"Resume-Rejected"** / **"Interview-Rejected"** (red) based on outcome
5. Delete or archive offers after accepting one

## 🎓 Learning Resources

### Technologies Used
- **Jetpack Compose**: Modern UI toolkit
- **Room Database**: SQLite wrapper
- **JSoup**: HTML parsing
- **Kotlin Coroutines**: Async programming
- **Material 3**: Design system

### Documentation Links
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [JSoup](https://jsoup.org/)
- [Material 3](https://m3.material.io/)

## 🚀 Next Steps

### Suggested Enhancements
1. Add notes field for each job
2. Implement interview reminders
3. Add company contact tracking
4. Export to CSV/PDF
5. Add statistics dashboard
6. Implement dark theme
7. Add job posting date
8. Track salary information
9. Add application deadline reminders
10. Integrate with calendar

## 💡 Tips & Tricks

### Efficiency Tips
1. **Bulk sharing**: Share multiple jobs at once (open app between shares)
2. **Quick status**: Long-press status chip (future feature)
3. **Keyboard search**: Type immediately when app opens
4. **Swipe carefully**: Accidental swipes delete permanently

### Power User Features
1. Filter by status using search (future feature)
2. Sort by date/company (future feature)
3. Batch operations (future feature)
4. Widgets (future feature)

## 📞 Support

### Getting Help
1. Check this guide first
2. Review README.md
3. Check IMPLEMENTATION_SUMMARY.md
4. Examine error logs in Logcat
5. Debug with breakpoints

### Reporting Issues
When reporting issues, include:
- Android version
- Device model
- Steps to reproduce
- Error messages from Logcat
- Screenshots if applicable

---

**Happy Job Hunting! 🎯**

Built with ❤️ by TheWalkerSoft


# Architecture & Data Flow Diagram

## State Management Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            JobViewModel                                      │
│                         (Central State Manager)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  StateFlows:                                                                 │
│  ├─ allJobs: List<JobEntity>              ─────► Database (Room)            │
│  ├─ jobs: List<JobEntity>                 ─────► Filtered by search/status  │
│  ├─ searchQuery: String                   ───┐                              │
│  ├─ statusFilter: JobStatus?              ───┤                              │
│  ├─ isScraping: Boolean                   ───┼─► combine() ──► jobs flow   │
│  ├─ message: String?                      ───┘                              │
│  ├─ cloudHealth: String                                                      │
│  ├─ jobSyncStateById: Map<String, JobSyncDotState>                          │
│  ├─ manualSyncUiState: ManualSyncUiState                                    │
│  ├─ pendingJobsByUrl: Map<String, Long>   ◄─── New Feature                 │
│  ├─ queueStatus: Int                      ◄─── New Feature                 │
│  └─ lastSyncTime: Long?                   ◄─── New Feature                 │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
         │                          │                           │
         │                          │                           │
         ▼                          ▼                           ▼
    MainActivity          AppNavigation              SyncDashboardScreen
         │                    NavHost                  (Dashboard UI)
         │                      │                           │
    collectAsState()            ├─► JobListScreen     │ Job List Screen
         │                      │   (Job List UI)     │
         │                      │                     │
         │                      └─► JobDetailsScreen  │ Job Details Screen
         │                          (Job Details UI)  │
         │                                            │
         └────────────────────────────────────────────┘

```

## Pending Job Processing Flow

```
User shares LinkedIn URL from LinkedIn app
          │
          ▼
    handleIntent()
          │
          ▼
    handleSharedLink(url)
          │
          ├─► Add to pendingJobsByUrl map
          │   [url -> timestamp]
          │
          ├─► Show toast: "Processing LinkedIn job..."
          │
          ├─► Push to Supabase (backend scraping)
          │
          └─► If offline: Queue to outbox
                │
                ▼
          OutboxSyncWorker (retry every 60min)
                │
                ▼
         Backend processes and scrapes
                │
                ▼
         Job inserted into `allJobs` via realtime (`realtime:public:jobs_final`)
                │
                ▼
         combine(pendingJobsByUrl, allJobs) detects match
                │
                ├─► Remove from pendingJobsByUrl
                │
                ├─► Show toast: "Job 'Title' from 'Company' added!"
                │
                └─► PendingJobCard auto-disappears from UI
```

## Search Filter Pipeline

```
User types in search bar
          │
          ▼
    onSearchQueryChange(text)
          │
          ▼
    _searchQuery.value = text
          │
          ▼
    combine(searchQuery, statusFilter, allJobs)
          │
          ├─► Filter 1: Company name contains query (case-insensitive)
          │
          └─► Filter 2: Job status matches filter (if selected)
                │
                ▼
           jobs StateFlow updates (instantly on UI)
                │
                ▼
           JobListScreen recomposes with filtered list
```

## Sync Dashboard Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│              SyncDashboardScreen                             │
└─────────────────────────────────────────────────────────────┘
         │           │            │           │
    ┌────┘           │            │           └─────┐
    │                │            │                 │
    ▼                ▼            ▼                 ▼
LastSync         Current         Queue        Statistics
StatusCard       SyncStatus      Status       Card
    │            Card            Card            │
    │            │               │               │
    └─► Uses:    └─► Uses:       └─► Uses:   └──► Uses:
        • lastSync     • cloudHealth  • queueStatus   • manualSyncUiState
           Time         • connection     (count)         - attempted
        • relative         state        • color         - acknowledged
           time label      • isRunning     badge          - failed
                          • onManual     • visual        - pulledUpdates
                            SyncClick     indicator

    All data from ViewModel StateFlows ◄─── Real-time updates every 2-5 sec
```

## Real-Time Updates Timing

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Refresh Intervals                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  Every 2 seconds:                                                    │
│  ├─ queueStatus = getOutboxOperations().size                        │
│  └─ lastSyncTime = getLastSyncTimeMillis()                          │
│                                                                       │
│  Every 5 seconds:                                                    │
│  └─ cloudHealth (includes queue size, failed count, last sync)       │
│                                                                       │
│  On Events (immediate):                                              │
│  ├─ Search query changes ──► jobs flow updates                      │
│  ├─ Status filter changes ──► jobs flow updates                     │
│  ├─ New job appears ──► allJobs updates (from Room)                 │
│  ├─ Pending job matched ──► pendingJobsByUrl updates                │
│  └─ Realtime event ──► job inserted/updated/deleted                 │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## UI Component Hierarchy

```
MainActivity
    │
    └─► setContent {
            LinkedIn_Job_TrackerTheme {
                AppNavigation(navController, ...)
                    │
                    ├─► JobListScreen
                    │       ├─► TopAppBar (with Info icon)
                    │       ├─► SearchBar (reactive, no button)
                    │       ├─► LazyColumn
                    │       │   ├─► PendingJobCard (NEW)
                    │       │   │   ├─ Pulsing indicator
                    │       │   │   ├─ Job URL display
                    │       │   │   └─ Processing message
                    │       │   │
                    │       │   └─► JobCard (regular jobs)
                    │       │       ├─ Company name
                    │       │       ├─ Job title
                    │       │       ├─ Status chip
                    │       │       └─ Sync indicator dot
                    │       │
                    │       └─► LoadingOverlay (scraping + sync)
                    │
                    ├─► JobDetailsScreen
                    │   └─► Full job details, edit, delete
                    │
                    └─► SyncDashboardScreen (NEW)
                        ├─ LastSyncStatusCard
                        ├─ CurrentSyncStatusCard
                        ├─ QueueStatusCard
                        ├─ ActiveSyncCard (if running)
                        ├─ SyncStatisticsCard
                        └─ InfoCard
```

## Data Flow: End-to-End Pending Job

```
1. SHARE INTENT
   └─ User shares LinkedIn URL from LinkedIn app

2. HANDLE INTENT
   └─ MainActivity.onNewIntent(intent)
      └─ viewModel.handleIntent(intent)
         └─ viewModel.handleSharedLink(rawSharedText)

3. ADD TO PENDING
   └─ pendingJobsByUrl[url] = System.currentTimeMillis()
   └─ Show toast: "Processing LinkedIn job..."

4. QUEUE/PUSH
   └─ repository.pushSharedLink(url)
      ├─ If SUCCESS: Update cloudHealth
      └─ If FAILED: Queue to outbox
         └─ OutboxWorkScheduler.kick()

5. BACKEND PROCESSING (Supabase)
   └─ Shared link handler:
      ├─ Fetch LinkedIn page
      ├─ Parse job details with JSoup
      └─ Insert into `public.jobs_final` table

6. REALTIME UPDATE
   └─ SupabaseRealtimeManager receives `postgres_changes` INSERT on `public.jobs_final`
      └─ processRealtimeEvent()
         └─ dao.upsertJob(newJob)

7. DETECT COMPLETION
   └─ combine(pendingJobsByUrl, allJobs) collects
      └─ Finds URL match in allJobs
         ├─ Remove from pendingJobsByUrl
         ├─ Find job details
         └─ Show toast: "Job 'Title' from 'Company' added!"

8. UI UPDATES
   └─ pendingJobsByUrl StateFlow changes
      └─ JobListScreen recomposes
         ├─ PendingJobCard disappears
         └─ New job card appears in list

9. SYNC DASHBOARD REFLECTS
   └─ queueStatus: decreases by 1
   └─ lastSyncTime: updates to now
   └─ cloudHealth: shows "Synced"
```

---

## Performance & Optimization

```
┌─────────────────────────────────────────────────────────────┐
│  Optimization Strategies Implemented                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. Debouncing                                               │
│     └─ Queue status: 2-second interval updates               │
│     └─ Cloud health: 5-second interval updates               │
│     └─ Search: Inherent debounce in combine()                │
│                                                               │
│  2. Efficient State Management                               │
│     └─ Using StateFlow (hot flow) for UI state               │
│     └─ Using combine() for derived state                     │
│     └─ Using SharingStarted.WhileSubscribed()                │
│                                                               │
│  3. UI Optimization                                          │
│     └─ Removed verbose top bar text                          │
│     └─ Pending cards use simple pulsing animation            │
│     └─ LazyColumn for efficient list rendering               │
│     └─ Key function for stable item identification           │
│                                                               │
│  4. Memory Efficiency                                        │
│     └─ Pending jobs stored as lightweight Map                │
│     └─ Auto-cleanup when jobs appear                         │
│     └─ Outbox compaction to remove duplicate operations      │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```


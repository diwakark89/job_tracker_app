# Supabase CRUD Operations Reference

> **Last updated:** 2026-04-05
> **Companion to:** [`SUPABASE_SCHEMA.md`](./SUPABASE_SCHEMA.md)
> **Operator runbook:** [`SUPABASE_JOBS_RAW_CUTOVER_RUNBOOK.md`](./SUPABASE_JOBS_RAW_CUTOVER_RUNBOOK.md)
>
> This document describes **exactly how the Android app performs core Create,
> Read, Update, and Delete operations**, plus the Phase 2-7 pipeline table
> operations — what triggers each operation, how local Room storage is mutated
> first (for app flows), what HTTP request is sent to Supabase, what response is
> expected, and how failures are handled via the offline outbox.

---

## Table of Contents

1. [Data-Flow Architecture](#1-data-flow-architecture)
2. [Global Request Headers](#2-global-request-headers)
3. [CREATE — Save a New Job](#3-create--save-a-new-job)
4. [CREATE — Insert a Shared Link](#4-create--insert-a-shared-link)
5. [READ — Fetch All Jobs (Pull)](#5-read--fetch-all-jobs-pull)
6. [READ — Realtime Subscription](#6-read--realtime-subscription)
7. [UPDATE — Change Job Status](#7-update--change-job-status)
8. [UPDATE — Edit Job Fields](#8-update--edit-job-fields)
9. [UPDATE — Restore a Deleted Job](#9-update--restore-a-deleted-job)
10. [DELETE — Soft Delete (Primary Path)](#10-delete--soft-delete-primary-path)
11. [DELETE — Hard Delete (Supabase-side)](#11-delete--hard-delete-supabase-side)
12. [Offline Outbox & Retry Flow](#12-offline-outbox--retry-flow)
13. [Realtime Event Processing](#13-realtime-event-processing)
14. [Outbox Compaction Rules](#14-outbox-compaction-rules)
15. [Error Response Handling](#15-error-response-handling)
16. [PIPELINE — Upsert Enrichment (jobs_enriched)](#16-pipeline--upsert-enrichment-jobs_enriched)
17. [PIPELINE — Insert Decision (job_decisions)](#17-pipeline--insert-decision-job_decisions)
18. [PIPELINE — Upsert Approval (job_approvals)](#18-pipeline--upsert-approval-job_approvals)
19. [PIPELINE — Upsert Final Record (jobs_final)](#19-pipeline--upsert-final-record-jobs_final)
20. [PIPELINE — Update Singleton Metrics (job_metrics)](#20-pipeline--update-singleton-metrics-job_metrics)

---

## 1. Data-Flow Architecture

Every mutation in the app follows a **local-first** pattern: Room is written
before any network call, so the UI responds instantly regardless of connectivity.

```
User Action (UI / Share Intent)
        │
        ▼
  JobViewModel
        │
        ├──[1]──► JobDao.upsertJob()       ← Room SQLite (local, instant)
        │
        └──[2]──► SupabaseRepository
                       │
                       ├── SUCCESS ──► Clear outbox entry for this job
                       │
                       └── FAILURE ──► PreferencesManager.enqueueOperation()
                                              │
                                              ▼
                                       OutboxSyncWorker (WorkManager)
                                              │
                                              └──► Retry when network available
```

**Read path:**

```
App Launch / Manual Sync
        │
        ├──[pull]──► GET /rest/v1/jobs_raw ──► Merge into Room
        │
        └──[live] ──► WebSocket subscription ──► Apply INSERT/UPDATE/DELETE to Room
```

---

## 2. Global Request Headers

Every HTTP request to Supabase must include:

```http
apikey: <ANON_KEY>
Authorization: Bearer <ANON_KEY>
Content-Type: application/json
```

These are injected by the `OkHttp` `authInterceptor` in `SupabaseClient` and do
not need to be added manually per-request.

---

## 3. CREATE — Save a New Job

### 3.1 Trigger paths

| Trigger | ViewModel method called |
|---------|------------------------|
| LinkedIn URL shared via Android share-intent | `handleIntent()` → `scrapeAndSaveJob()` → `saveJob()` |
| Manual URL entry in the app | `scrapeAndSaveJob()` → `saveJob()` |
| Sync pull finds a remote job not in Room | `SupabaseRepository.pullCloudJobsToRoom()` → `dao.upsertJob()` |

### 3.2 Step-by-step flow

```
1. JobViewModel.saveJob(job)
   └─ dao.upsertJob(job)                          ← Room insert (local first)
   └─ queueOrPushUpsert(job)
         └─ SupabaseRepository.pushJob(job)
               └─ SupabaseClient.instance.upsertJob(listOf(job))
```

### 3.3 Local Room mutation (happens first)

`JobDao.upsertJob(job)` performs a `@Upsert` which is INSERT OR REPLACE on the
`jobs` table keyed by `id`. The DAO query filters out `isDeleted = true` rows,
so the newly created job with `isDeleted = false` will immediately appear in the
UI via the Room Flow.

### 3.4 HTTP Request

```http
POST /rest/v1/jobs_raw?on_conflict=job_url
Prefer: resolution=merge-duplicates
```

**Request body** (JSON array with one element):

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "We are looking for an experienced Android engineer to join our team…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Saved",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "created_at": "2026-04-03T09:00:00.000Z",
    "modified_at": "2026-04-03T09:00:00.000Z",
    "is_deleted": false
  }
]
```

**Notes:**
- `id` is a client-generated UUID (`UUID.randomUUID().toString()`).
- `created_at` and `modified_at` are epoch-millisecond `Long` values in Room,
  serialised to ISO-8601 UTC strings (`"2026-04-03T09:00:00.000Z"`) by the
  Gson `timestampAdapter` before sending.
- `job_status` is the display-name form (e.g. `"Saved"`, not `"SAVED"`).
- Android raw writes intentionally omit local-only fields: `source_platform`, `match_score`, `prep_notes`, and `filter_reason`.
- `scrape_run_id` is **never sent** by the Android app — it is a server/pipeline-only field.

### 3.5 Expected response

| HTTP Code | Meaning |
|-----------|---------|
| `204 No Content` | ✅ Upsert succeeded |
| `4xx / 5xx` | ❌ Failure — operation queued in outbox for retry |

### 3.6 On failure

If the push fails, an `OutboxOperation` of type `UPSERT` is saved:

```json
{
  "key": "<uuid>",
  "type": "UPSERT",
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "jobUrl": "https://www.linkedin.com/jobs/view/1234567890",
  "sharedUrl": null,
  "lastModified": 1743674410421,
  "createdAt": 1743674410421
}
```

---

## 4. CREATE — Insert a Shared Link

### 4.1 Trigger

User shares a LinkedIn URL from any Android app via the OS share intent.
This fires **immediately** and in parallel with the scraping flow.

### 4.2 Step-by-step flow

```
JobViewModel.handleIntent()
   └─ handleSharedLink(linkedInJobUrl)
         └─ SupabaseRepository.pushSharedLink(rawUrl)
               └─ SupabaseClient.instance.insertSharedLink(listOf(SharedLinkRequest(url=rawUrl)))
```

> This operation does **not** write to Room. It is a cloud-only append to the
> `shared_links` table for consumption by a background pipeline.

### 4.3 HTTP Request

```http
POST /rest/v1/shared_links
Prefer: return=minimal
```

**Request body:**

```json
[
  {
    "url": "https://www.linkedin.com/jobs/view/1234567890",
    "source": "android-share-intent",
    "status": "Pending"
  }
]
```

**Field notes:**
- `source` is always `"android-share-intent"` from this app.
- `status` is always `"Pending"` — the pipeline updates it when processed.

### 4.4 Expected response

| HTTP Code | Meaning |
|-----------|---------|
| `201 Created` | ✅ Row inserted |
| `204 No Content` | ✅ Inserted (with `return=minimal`) |
| `4xx / 5xx` | ❌ Failure — queued as `SHARED_LINK` outbox operation |

### 4.5 On failure

```json
{
  "key": "<uuid>",
  "type": "SHARED_LINK",
  "jobId": null,
  "jobUrl": "https://www.linkedin.com/jobs/view/1234567890",
  "sharedUrl": "https://www.linkedin.com/jobs/view/1234567890",
  "lastModified": 1743674410421,
  "createdAt": 1743674410421
}
```

---

## 5. READ — Fetch All Jobs (Pull)

### 5.1 Trigger paths

| When | Who calls it |
|------|-------------|
| App launch (`init` block) | `JobViewModel.init` |
| Manual sync button tapped | `OutboxSyncWorker.doWork()` (at end) |
| Periodic background sync | `OutboxSyncWorker.doWork()` (WorkManager) |

### 5.2 Step-by-step flow

```
SupabaseRepository.pullCloudJobsToRoom()
   ├─ GET /rest/v1/jobs_raw?select=*&order=created_at.desc   ← fetch all remote rows
   ├─ dao.getAllJobsOnce()                                ← fetch all local rows
   └─ For each remote/local pair → reconcile (see §11 in schema doc)
```

### 5.3 HTTP Request

```http
GET /rest/v1/jobs_raw?select=*&order=created_at.desc
```

No request body.

### 5.4 Expected response

`200 OK` — JSON array of all job rows:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "We are looking for…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Applied",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "scrape_run_id": null,
    "created_at": "2026-04-01T09:00:00.479861+00:00",
    "modified_at": "2026-04-03T06:15:00.000000+00:00",
    "is_deleted": false
  }
]
```

**Deserialization notes:**
- `created_at` / `modified_at` arrive as ISO-8601 strings with offset notation
  (`+00:00` or `Z`) and are parsed to epoch-millisecond `Long` by the Gson
  `timestampAdapter` in `SupabaseClient`.
- `job_status` strings like `"Resume-Rejected"` are mapped back to `JobStatus.RESUME_REJECTED`
  via `parseJobStatus()`.
- **All rows** (including soft-deleted `is_deleted = true` tombstones) are
  returned. The app stores tombstones in Room so that the sync loop does not
  re-upload them from other devices.

### 5.5 Local effect after pull

```
Remote row exists, local does NOT      → dao.upsertJob(remoteJob)              [insert]
Remote newer than local (> 2 min gap)  → dao.upsertJob(remoteJob.copy(id=localJob.id)) [overwrite]
Local newer than remote (> 2 min gap)  → POST /rest/v1/jobs_raw (push local up)   [upload]
Exact tie                               → dao.upsertJob(remoteJob.copy(id=localJob.id)) [remote wins]
Within 2-min skew window                → keep local as-is                               [preserve]
Local row exists, remote does NOT      → POST /rest/v1/jobs_raw (push local up)   [upload]
```

---

## 6. READ — Realtime Subscription

### 6.1 Connection setup

At app launch, `SupabaseRealtimeManager.connect()` opens a WebSocket:

```
wss://<PROJECT_HOST>/realtime/v1/websocket?apikey=<ANON_KEY>&vsn=1.0.0
```

Then sends the channel join message:

```json
{
  "topic": "realtime:public:jobs_raw",
  "event": "phx_join",
  "payload": {
    "config": {
      "broadcast": { "self": false },
      "presence": { "key": "" },
      "postgres_changes": [
        { "event": "*", "schema": "public", "table": "jobs_raw" }
      ]
    },
    "access_token": "<ANON_KEY>"
  },
  "ref": "join_jobs"
}
```

### 6.2 Incoming INSERT event

```json
{
  "event": "postgres_changes",
  "topic": "realtime:public:jobs_raw",
  "payload": {
    "data": {
      "eventType": "INSERT",
      "schema": "public",
      "table": "jobs_raw",
      "new": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "company_name": "Acme Corp",
        "role_title": "Senior Android Engineer",
        "description": "Full description…",
        "job_url": "https://www.linkedin.com/jobs/view/1234567890",
        "job_status": "Saved",
        "language": "English",
        "pipeline_stage": "SCRAPED",
        "content_hash": null,
        "external_id": null,
        "location": null,
        "scrape_run_id": null,
        "created_at": "2026-04-03T09:00:00.000000+00:00",
        "modified_at": "2026-04-03T09:00:00.000000+00:00",
        "is_deleted": false
      },
      "old": {}
    }
  }
}
```

**Local effect:** `dao.upsertJob(parsedJob)` — adds to Room, UI updates live.
If `is_deleted = true`, `dao.deleteJob(job.id)` is called instead.

### 6.3 Incoming UPDATE event

```json
{
  "event": "postgres_changes",
  "payload": {
    "data": {
      "eventType": "UPDATE",
      "schema": "public",
      "table": "jobs_raw",
      "new": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "job_status": "Interview",
        "modified_at": "2026-04-03T10:30:00.000000+00:00",
        "is_deleted": false,
        "other_fields_omitted_for_brevity": "..."
      },
      "old": {
        "id": "550e8400-e29b-41d4-a716-446655440000"
      }
    }
  }
}
```

**Local effect:** `dao.upsertJob(parsedJob)` — merges into Room.
If `is_deleted = true`, `dao.deleteJob(job.id)` is called (immediate local removal).

### 6.4 Incoming DELETE event

```json
{
  "event": "postgres_changes",
  "payload": {
    "data": {
      "eventType": "DELETE",
      "schema": "public",
      "table": "jobs_raw",
      "new": {},
      "old": {
        "id": "550e8400-e29b-41d4-a716-446655440000"
      }
    }
  }
}
```

**Local effect:** `dao.deleteJob(id)` — removes the row from Room by its `id`.

---

## 7. UPDATE — Change Job Status

### 7.1 Trigger

User selects a new status from the dropdown/chips in the Job Detail or Job List screen.

### 7.2 Step-by-step flow

```
JobViewModel.updateJobStatus(job, newStatus)
   ├─ val updatedJob = job.copy(status = newStatus, updatedAt = System.currentTimeMillis())
   ├─ dao.upsertJob(updatedJob)        ← Room update (local first)
   └─ queueOrPushUpsert(updatedJob)   ← push to Supabase
```

### 7.3 HTTP Request

Identical endpoint and headers as [§3.4](#34-http-request). The full job
object is sent — not a partial/PATCH. Only `job_status` and `modified_at` differ
from the original:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "Full description…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Interview",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "created_at": "2026-04-01T09:00:00.000Z",
    "modified_at": "2026-04-03T10:30:00.000Z",
    "is_deleted": false
  }
]
```

> **Important:** The app always sends the **complete row**, not a partial update.
> Supabase's `resolution=merge-duplicates` merges all provided columns into the
> existing row on conflict. The `modified_at` trigger will overwrite the value
> you send with `now()` on the server side after the first write.

### 7.4 Expected response

`204 No Content`

---

## 8. UPDATE — Edit Job Fields

### 8.1 Trigger

User edits `companyName`, `jobUrl`, `jobTitle`, or `jobDescription` in the
Edit Job screen and confirms.

### 8.2 Step-by-step flow

```
JobViewModel.updateJob(job, companyName, jobUrl, jobTitle, jobDescription)
   ├─ val updatedJob = job.copy(
   │       companyName = companyName,
   │       jobUrl = jobUrl,
   │       jobTitle = jobTitle,
   │       jobDescription = jobDescription,
   │       updatedAt = System.currentTimeMillis()
   │   )
   ├─ dao.upsertJob(updatedJob)        ← Room update
   └─ queueOrPushUpsert(updatedJob)   ← push to Supabase
```

### 8.3 HTTP Request

Same endpoint as [§3.4](#34-http-request). Full row is sent with updated fields.

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp Updated",
    "role_title": "Lead Android Engineer",
    "description": "Updated description after re-reading the posting…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Applied",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "created_at": "2026-04-01T09:00:00.000Z",
    "modified_at": "2026-04-03T11:00:00.000Z",
    "is_deleted": false
  }
]
```

### 8.4 Expected response

`204 No Content`

---

## 9. UPDATE — Restore a Deleted Job

### 9.1 Trigger

User taps "Restore" on a tombstoned job (visible in a separate "Deleted" list view).

### 9.2 Step-by-step flow

```
JobViewModel.restoreJob(job)
   └─ val restoredJob = job.copy(isDeleted = false, updatedAt = System.currentTimeMillis())
   └─ saveJob(restoredJob)
         ├─ dao.upsertJob(restoredJob)      ← Room: row becomes visible again
         └─ queueOrPushUpsert(restoredJob)  ← push is_deleted=false to Supabase
```

### 9.3 HTTP Request

Same upsert endpoint as [§3.4](#34-http-request), with `"is_deleted": false`:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "Full description…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Saved",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "created_at": "2026-04-01T09:00:00.000Z",
    "modified_at": "2026-04-03T12:00:00.000Z",
    "is_deleted": false
  }
]
```

---

## 10. DELETE — Soft Delete (Primary Path)

The app uses **soft delete as the primary path**. A tombstone is written so that
the sync loop on other devices knows not to re-upload the row.

### 10.1 Trigger

User swipes-to-dismiss a job card, or taps "Delete" in the Job Detail screen.

### 10.2 Step-by-step flow

```
JobViewModel.deleteJob(jobId)
   ├─ val job = dao.getAllJobsOnce().firstOrNull { it.id == jobId }
   ├─ val tombstonedJob = job.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
   ├─ dao.upsertJob(tombstonedJob)        ← Row stays in Room with isDeleted=1
   │                                         DAO queries filter it → disappears from UI
   └─ queueOrPushUpsert(tombstonedJob)   ← Sends is_deleted=true to Supabase
```

### 10.3 HTTP Request (soft delete upsert)

```http
POST /rest/v1/jobs_raw?on_conflict=job_url
Prefer: resolution=merge-duplicates
```

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "Full description…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "job_status": "Applied",
    "language": "English",
    "pipeline_stage": "SCRAPED",
    "content_hash": null,
    "external_id": null,
    "location": null,
    "created_at": "2026-04-01T09:00:00.000Z",
    "modified_at": "2026-04-03T13:00:00.000Z",
    "is_deleted": true
  }
]
```

### 10.4 Expected response

`204 No Content`

### 10.5 Why soft delete instead of hard delete?

The tombstone `is_deleted = true` is stored in both Room and Supabase. When
the sync worker pulls from the cloud on a second device, it finds the tombstone
and calls `dao.upsertJob(remoteJob)` to store it locally, ensuring the deleted
job does **not** get re-uploaded back to the cloud from that device.

---

## 11. DELETE — Hard Delete (Supabase-side)

`SupabaseRepository.pushDelete(jobId, jobUrl)` issues a true SQL DELETE on the
Supabase table. This is called from `queueOrPushDelete()` in the ViewModel
(used in certain code paths and via the outbox for queued DELETE operations).

### 11.1 HTTP Request

```http
DELETE /rest/v1/jobs_raw?id=eq.550e8400-e29b-41d4-a716-446655440000
```

No request body.

### 11.2 Expected response

| HTTP Code | Meaning | App behaviour |
|-----------|---------|---------------|
| `204 No Content` | ✅ Row deleted | Mark outbox operation as acknowledged |
| `404 Not Found` | Row already gone | Treated as **success** (idempotent) |
| Other `4xx / 5xx` | ❌ Failure | Queue/re-queue as `DELETE` outbox op |

---

## 12. Offline Outbox & Retry Flow

When any network push fails, an `OutboxOperation` is serialised to JSON and
saved in `SharedPreferences` under a unique key. `OutboxSyncWorker` (WorkManager)
drains the queue whenever network becomes available.

### 12.1 Outbox operation structure

```json
{
  "key": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "type": "UPSERT",
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "jobUrl": "https://www.linkedin.com/jobs/view/1234567890",
  "sharedUrl": null,
  "lastModified": 1743674410421,
  "createdAt": 1743674410421
}
```

| Field | Values | Notes |
|-------|--------|-------|
| `type` | `UPSERT`, `DELETE`, `SHARED_LINK` | Determines which API call to replay |
| `jobId` | UUID string or `null` | Required for `DELETE` type; `null` for `SHARED_LINK` |
| `jobUrl` | URL string | Used as business key for compaction and failure diagnostics |
| `sharedUrl` | URL string or `null` | The URL to re-send for `SHARED_LINK` type |
| `lastModified` | epoch ms | Used by compaction to keep only the newest op per `jobUrl` |

### 12.2 Worker drain sequence

```
OutboxSyncWorker.doWork()
  1. preferencesManager.compactOutbox()           ← deduplicate before processing
  2. For each OutboxOperation (sorted by createdAt):
     ├─ UPSERT  → dao.getJobByUrl(jobUrl) → pushJob(job)
     ├─ DELETE  → pushDelete(jobId, jobUrl)
     └─ SHARED_LINK → pushSharedLink(sharedUrl)
  3. pullCloudJobsToRoom()                        ← full reconciliation after outbox drain
  4. Update PreferencesManager sync metadata
  5. Return Result.success(outputData)
```

### 12.3 Worker output data

```text
workDataOf(
  "attempted" to 12,
  "acknowledged" to 10,
  "failed" to 2,
  "pulled_updates" to 7
)
```

---

## 13. Realtime Event Processing

`JobViewModel` collects `SupabaseRealtimeManager.jobEvents` and applies each
event to Room immediately:

```
RealtimeJobEvent.Insert(record: JsonObject)
   ├─ if is_deleted == true  → dao.deleteJob(job.id)   (cloud tombstone → local remove)
   └─ if is_deleted == false → dao.upsertJob(job)       (new row → insert to Room)

RealtimeJobEvent.Update(record: JsonObject)
   ├─ if is_deleted == true  → dao.deleteJob(job.id)   (deleted on another device → remove locally)
   └─ if is_deleted == false → dao.upsertJob(job)       (field/status change → update Room)

RealtimeJobEvent.Delete(oldRecord: JsonObject)
   └─ id = oldRecord["id"]  → dao.deleteJob(id)         (hard delete event → remove from Room)
```

Parsing uses `SupabaseClient.supabaseGson` which handles the same ISO-8601 ↔
epoch-ms conversion and `JobStatus` display-name mapping as the REST path.

---

## 14. Outbox Compaction Rules

Before the worker runs (and on every `enqueueOperation` call), redundant
operations for the same `jobUrl` are collapsed:

| Scenario | Result after compaction |
|----------|------------------------|
| Multiple `UPSERT` ops for same `jobUrl` | Keep the one with the highest `lastModified` |
| `DELETE` + `UPSERT` for same `jobUrl` | Keep only the `DELETE` (delete wins) |
| Multiple `DELETE` ops for same `jobUrl` | Keep the one with the highest `lastModified` |
| `SHARED_LINK` ops | Never compacted — each is kept independently |

This ensures that if a user creates then immediately updates a job while offline,
only one network call is made when connectivity is restored.

---

## 15. Error Response Handling

### HTTP error bodies from Supabase (PostgREST format)

```json
{
  "code": "23503",
  "details": null,
  "hint": null,
  "message": "insert or update on table \"jobs_raw\" violates foreign key constraint"
}
```

The app reads `response.errorBody()?.string()` and stores it (truncated to
180 chars) as a `SyncFailureDiagnostic` in `SharedPreferences`, keyed by
`jobUrl`. This diagnostic is displayed in the UI as a per-job sync failure
indicator (red/yellow dot on the job card).

### Failure reason format stored locally

```
"<stage>: HTTP <code>: <errorBody>"

Examples:
  "pushJob: HTTP 422: insert or update on table jobs_raw violates check constraint"
  "outboxUpsert: Local job missing for queued replay"
  "pullUploadLocalWinner: HTTP 503: Service Unavailable"
```

### Sync dot states

| State | Condition |
|-------|-----------|
| 🟢 Green | Job was synced before `lastSyncTime` and has no outbox entry |
| 🟡 Yellow | Job has a pending outbox entry, OR `updatedAt ≥ lastSyncTime` |
| 🔴 Red | No successful sync has ever completed (`lastSyncTime == null`) |

---

## 16. PIPELINE — Upsert Enrichment (jobs_enriched)

### 16.1 Trigger

Pipeline enrichment service finishes structured extraction for one `jobs_raw` row.

### 16.2 HTTP Request

```http
POST /rest/v1/jobs_enriched?on_conflict=job_id
Prefer: resolution=merge-duplicates
```

### 16.3 Request body

```json
[
  {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "tech_stack": ["Kotlin", "Jetpack Compose", "Room"],
    "experience_level": "Senior",
    "remote_type": "Hybrid",
    "visa_sponsorship": false,
    "english_friendly": true
  }
]
```

### 16.4 Expected response

`204 No Content`

### 16.5 Constraints to respect

- `job_id` must already exist in `public.jobs_raw`.
- `job_id` is unique in this table (one enrichment row per job).

---

## 17. PIPELINE — Insert Decision (job_decisions)

### 17.1 Trigger

AI decision engine scores a job and emits a decision.

### 17.2 HTTP Request

```http
POST /rest/v1/job_decisions
Prefer: return=minimal
```

### 17.3 Request body

```json
[
  {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "match_score": 0.87,
    "decision": "AUTO_APPROVE",
    "reason": "Strong match on required Android stack and seniority.",
    "confidence": 0.91
  }
]
```

### 17.4 Expected response

`201 Created` (or `204` with `Prefer: return=minimal`)

### 17.5 Constraints to respect

- `decision` must be one of `AUTO_APPROVE`, `REVIEW`, `REJECT`.
- Use INSERT semantics for history preservation (multiple decisions per job allowed).

---

## 18. PIPELINE — Upsert Approval (job_approvals)

### 18.1 Trigger

Human approval step completes for a decision (Telegram/manual review).

### 18.2 HTTP Request

```http
POST /rest/v1/job_approvals?on_conflict=decision_id
Prefer: resolution=merge-duplicates
```

### 18.3 Request body

```json
[
  {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "decision_id": "66f2f0c8-8f57-4e75-84bd-5f1d9b301111",
    "user_action": "APPROVED",
    "approved_at": "2026-04-05T12:30:00.000Z"
  }
]
```

### 18.4 Expected response

`204 No Content`

### 18.5 Constraints to respect

- `decision_id` must exist in `public.job_decisions`.
- `decision_id` is unique (one approval row per decision).
- `user_action` must be `APPROVED`, `REJECTED`, or `PENDING`.

---

## 19. PIPELINE — Upsert Final Record (jobs_final)

### 19.1 Trigger

A job is accepted as final output after decision + approval logic.

### 19.2 HTTP Request

```http
POST /rest/v1/jobs_final?on_conflict=job_id
Prefer: resolution=merge-duplicates
```

### 19.3 Request body

```json
[
  {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890",
    "description": "We are looking for an experienced Android engineer to join our team…",
    "match_score": 0.87,
    "tags": ["android", "kotlin", "senior"]
  }
]
```

### 19.4 Expected response

`204 No Content`

### 19.5 Constraints to respect

- `job_id` is unique in `jobs_final` (strict one final row per raw job).
- `job_id` must exist in `public.jobs_raw`.
- `description` is optional and may store the final projected job description.

---

## 20. PIPELINE — Update Singleton Metrics (job_metrics)

### 20.1 Trigger

Pipeline run completes and cumulative counters need refresh.

### 20.2 HTTP Request

```http
PATCH /rest/v1/job_metrics?id=eq.1
```

### 20.3 Request body

```json
{
  "total_scraped": 1200,
  "total_approved": 145,
  "total_rejected": 780,
  "updated_at": "2026-04-05T13:00:00.000Z"
}
```

### 20.4 Expected response

`204 No Content`

### 20.5 Singleton guard rules

- Only row `id = 1` is valid (`CHECK (id = 1)`).
- Seed row should be created once by migration using:
  - `INSERT INTO public.job_metrics (id) VALUES (1) ON CONFLICT (id) DO NOTHING;`
- Counters must remain non-negative.

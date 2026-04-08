# Supabase Schema & Integration Reference

> **Last updated:** 2026-04-07
> **Room DB version:** 8 &nbsp;|&nbsp; **Supabase tables:** `jobs_raw`, `shared_links`, `jobs_enriched`, `job_decisions`, `job_approvals`, `jobs_final`, `job_metrics`
>
> This document is the single source of truth for any developer who wants to build a
> new client (web, iOS, desktop, pipeline, etc.) that reads from or writes to the
> same Supabase backend used by the Job Tracker Android app.
>
> **See also:** [`SUPABASE_CRUD_OPERATIONS.md`](./SUPABASE_CRUD_OPERATIONS.md) — exact HTTP
> requests, response payloads, and step-by-step flows for every Create / Read / Update / Delete operation.
> **Operator runbook:** [`SUPABASE_JOBS_RAW_CUTOVER_RUNBOOK.md`](./SUPABASE_JOBS_RAW_CUTOVER_RUNBOOK.md)
> **Pipeline migrations:**
> - [`../supabase/migrations/2026-04-05_phase2_phase7_pipeline_tables_singleton_metrics.sql`](../supabase/migrations/2026-04-05_phase2_phase7_pipeline_tables_singleton_metrics.sql)
> - [`../supabase/migrations/2026-04-05_phase2_phase7_pipeline_tables_singleton_metrics_verify.sql`](../supabase/migrations/2026-04-05_phase2_phase7_pipeline_tables_singleton_metrics_verify.sql)

---

## Table of Contents

1. [Prerequisites & Configuration](#1-prerequisites--configuration)
2. [Authentication](#2-authentication)
3. [Table: `public.jobs_raw`](#3-table-publicjobs_raw)
4. [Table: `public.shared_links`](#4-table-publicshared_links)
5. [Database Functions & Triggers](#5-database-functions--triggers)
6. [REST API Endpoints](#6-rest-api-endpoints)
7. [JSON Payload Examples](#7-json-payload-examples)
8. [Realtime WebSocket Integration](#8-realtime-websocket-integration)
9. [Field Name Mapping (App ↔ Supabase)](#9-field-name-mapping-app--supabase)
10. [JobStatus Enum Reference](#10-jobstatus-enum-reference)
11. [Conflict Resolution & Sync Logic](#11-conflict-resolution--sync-logic)
12. [Row-Level Security Notes](#12-row-level-security-notes)
13. [Quick-Start DDL (Copy-Paste)](#13-quick-start-ddl-copy-paste)
14. [Integration Checklist](#14-integration-checklist)
15. [Pipeline Tables (Phase 2-7)](#15-pipeline-tables-phase-2-7)
16. [Pipeline Verification (SELECT Diagnostics)](#16-pipeline-verification-select-diagnostics)

---

## 1. Prerequisites & Configuration

| Setting | Value / Where to find it |
|---------|--------------------------|
| Supabase Project URL | Your Supabase dashboard → Project Settings → API → `Project URL` |
| Publishable (anon) API key | Project Settings → API → `anon public` key |
| Base REST URL | `<PROJECT_URL>/rest/v1/` |
| Realtime WebSocket URL | `wss://<PROJECT_HOST>/realtime/v1/websocket?apikey=<ANON_KEY>&vsn=1.0.0` |

The Android app stores these values in `BuildConfig.SUPABASE_URL` and
`BuildConfig.SUPABASE_PUBLISHABLE_KEY` (injected from `local.properties` at
build time). Any other client must supply equivalent values.

---

## 2. Authentication

Every request **must** include the following HTTP headers:

```
apikey:        <ANON_KEY>
Authorization: Bearer <ANON_KEY>
Content-Type:  application/json
```

The app uses the **anon/publishable key** (not the service-role key). All
security is enforced by Row-Level Security policies on Supabase (see
[§12](#12-row-level-security-notes)).

---

## 3. Table: `public.jobs_raw`

### 3.1 Column Definitions

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | **Primary key** |
| `company_name` | `text` | NOT NULL | — | Company name |
| `role_title` | `text` | NOT NULL | — | Job title / role |
| `description` | `text` | NULL | — | Full job description text |
| `job_url` | `text` | NULL | — | **Unique business key** — used as upsert conflict target |
| `job_status` | `text` | NULL | `'Saved'` | Application status (see §10 for allowed values) |
| `language` | `text` | NULL | `'English'` | Posting language |
| `pipeline_stage` | `text` | NOT NULL | `'SCRAPED'` | Ingestion stage marker for pipeline flow |
| `content_hash` | `text` | NULL | — | Deduplication key. Unique when present |
| `external_id` | `text` | NULL | — | External source-specific identifier |
| `location` | `text` | NULL | — | Raw location text from source |
| `scrape_run_id` | `text` | NULL | — | Pipeline/server field; ignored by mobile client |
| `created_at` | `timestamptz` | NULL | `now()` | Row creation time (set once) |
| `modified_at` | `timestamptz` | NULL | `now()` | Last modification time; auto-updated by trigger |
| `is_deleted` | `boolean` | NOT NULL | `false` | Soft-delete tombstone — `true` means logically deleted |

> **Soft deletes:** The Android app issues a hard `DELETE` via the REST API for
> actual removal, **and** maintains an `is_deleted = true` tombstone on rows that
> were deleted on one device so that sync reconciliation does not re-insert them
> on other devices. Always filter `is_deleted = false` in consumer queries unless
> you specifically need tombstones.

### 3.2 Job Status CHECK Constraint

```sql
CHECK (job_status IN (
  'Saved',             'Applied',             'Interview',
  'Interviewing',      'Offer',
  'Resume-Rejected',   'Interview-Rejected',
  -- legacy / uppercase aliases also accepted:
  'SAVED',             'APPLIED',             'INTERVIEW',
  'INTERVIEWING',      'OFFER',
  'RESUME_REJECTED',   'INTERVIEW_REJECTED'
))
```

Prefer the **display-name form** (e.g. `'Resume-Rejected'`) when writing new
records. The uppercase aliases exist for backwards compatibility.

### 3.3 Indexes

| Index name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `jobs_raw_pkey` | `id` | Primary key (B-tree) | Row identity |
| `jobs_raw_job_url_key` | `job_url` | Unique B-tree | Upsert conflict target |
| `idx_jobs_raw_job_status_modified_at` | `(job_status, modified_at DESC)` | B-tree | Status-filtered lists sorted by recency |
| `idx_jobs_raw_created_at` | `created_at DESC` | B-tree | Default sort order for full listing |
| `idx_jobs_content_hash` | `content_hash` | Unique B-tree | Deduplication on hash when available |

### 3.4 Triggers

| Trigger name | Fires | Function called |
|---|---|---|
| `jobs_raw_set_modified_at` | `BEFORE UPDATE` on any column | `set_modified_at()` |

---

## 4. Table: `public.shared_links`

`shared_links` is an **append-only inbox** for raw URLs shared from an Android
share-intent (or other source). A background process (not in the Android app)
is expected to read these rows and create `jobs_raw` records from them.

### 4.1 Column Definitions

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | **Primary key** |
| `url` | `text` | NOT NULL | — | The shared URL |
| `source` | `text` | NULL | `'android-share-intent'` | Origin marker (see CHECK below) |
| `status` | `text` | NULL | `'Pending'` | Processing status for the pipeline |
| `created_at` | `timestamptz` | NULL | `now()` | Row creation time |
| `modified_at` | `timestamptz` | NULL | `now()` | Last update time (trigger-managed) |

### 4.2 Source CHECK Constraint

```sql
CHECK (source IN ('android-share-intent', 'web-extension', 'manual'))
```

### 4.3 Indexes

| Index name | Columns | Type |
|---|---|---|
| `shared_links_pkey` | `id` | Primary key |

### 4.4 Triggers

| Trigger name | Fires | Function called |
|---|---|---|
| `shared_links_set_modified_at` | `BEFORE UPDATE` | `set_modified_at()` |

---

## 5. Database Functions & Triggers

### `set_modified_at()`

Used by both tables to keep `modified_at` current on every UPDATE:

```sql
CREATE OR REPLACE FUNCTION public.set_modified_at()
RETURNS trigger AS $$
BEGIN
  NEW.modified_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## 6. REST API Endpoints

All paths are relative to `<PROJECT_URL>/rest/v1/`.

> **As of 2026-04-07** the Android app uses `jobs_final` for all operations.
> The `jobs_raw` endpoints are retained for pipeline / server use only.

### 6.1 List all jobs (app uses `jobs_final`)

```
GET /rest/v1/jobs_final?select=*&order=saved_at.desc
```

| Parameter | Default | Notes |
|-----------|---------|-------|
| `select` | `*` | Comma-separated column list, or `*` for all |
| `order` | `saved_at.desc` | PostgREST ordering syntax |

**Active jobs only (recommended):**

```
GET /rest/v1/jobs_final?select=*&order=saved_at.desc&is_deleted=eq.false
```

**Response:** `200 OK` — JSON array of job objects.

---

### 6.2 Upsert jobs (app uses `jobs_final`)

```
POST /rest/v1/jobs_final?on_conflict=job_id
Headers:
  Prefer: resolution=merge-duplicates
```

**Body:** JSON array of one or more job objects (see §7.1).

- Conflict is resolved on `job_id` (the unique FK to `jobs_raw.id`).
- `merge-duplicates` merges all supplied columns into the existing row.
- If `job_id` is new, a full insert is performed.
- `job_id` is the client-generated UUID (the Android app uses `UUID.randomUUID().toString()`).
- `modified_at` will be overwritten by the `jobs_final_set_modified_at` trigger on
  any subsequent UPDATE — do not rely on the value you send being persisted as-is
  after the first write.
- `match_score` defaults to `90` for app-created jobs.

**Response:** `204 No Content` on success.

---

### 6.3 Delete a job by job_id (app uses `jobs_final`)

```
DELETE /rest/v1/jobs_final?job_id=eq.<uuid>
```

| Parameter | Format | Example |
|-----------|--------|---------|
| `job_id` | `eq.<uuid>` | `eq.550e8400-e29b-41d4-a716-446655440000` |

**Response:**
- `204 No Content` — deleted successfully.
- `404 Not Found` — row did not exist; treat as success (idempotent).

> **Note:** The Android app uses a hard DELETE for immediate removal. The
> `is_deleted` tombstone flag is set locally in Room so that the sync loop does
> not re-upload the deleted row from a second device.

---

### 6.4 Insert a shared link

```
POST /rest/v1/shared_links
Headers:
  Prefer: return=minimal
```

**Body:** JSON array with one `SharedLinkRequest` object (see §7.3).

**Response:** `201 Created` (or `204` with `return=minimal`).

---

## 7. JSON Payload Examples

### 7.1 Upsert job payload (`jobs_final`)

```json
[
  {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "company_name": "Acme Corp",
    "role_title": "Senior Android Engineer",
    "description": "Full job description text…",
    "job_url": "https://www.linkedin.com/jobs/view/1234567890/",
    "job_status": "Applied",
    "match_score": 90,
    "tags": null,
    "saved_at": "2026-04-01T09:00:00.000Z",
    "language": "English",
    "modified_at": "2026-04-03T05:40:10.421Z",
    "is_deleted": false
  }
]
```

**Key points:**
- `saved_at` and `modified_at` are **ISO-8601 UTC strings** (`timestamptz`).
  The Android app serialises epoch-millisecond `Long` values to ISO-8601 before
  sending, and parses ISO-8601 (including offset notation like
  `2026-03-31T06:00:30.479861+00:00`) back to epoch millis.
- `job_status` must be one of the CHECK-constrained values (§3.2).
- `job_id` is the app business key and maps to `JobEntity.id`.
- App writes default `match_score = 90` and `tags = null`.
- Local-only fields `source_platform`, `prep_notes`, and `filter_reason` remain omitted from wire payloads.

---

### 7.2 Delete job by job_id

```
DELETE /rest/v1/jobs_final?job_id=eq.550e8400-e29b-41d4-a716-446655440000
```

No request body needed.

---

### 7.3 Insert shared link payload

```json
[
  {
    "url": "https://www.linkedin.com/jobs/view/1234567890/",
    "source": "android-share-intent",
    "status": "Pending"
  }
]
```

Allowed `source` values: `android-share-intent`, `web-extension`, `manual`.

---

### 7.4 Supabase Realtime event payload (PostgreSQL change on `jobs_final`)

```json
{
  "event": "postgres_changes",
  "topic": "realtime:public:jobs_final",
  "payload": {
    "data": {
      "eventType": "UPDATE",
      "schema": "public",
      "table": "jobs_final",
      "new": {
        "job_id": "550e8400-e29b-41d4-a716-446655440000",
        "company_name": "Acme Corp",
        "role_title": "Senior Android Engineer",
        "job_status": "Interview",
        "saved_at": "2026-04-01T09:00:00.000000+00:00",
        "modified_at": "2026-04-03T06:15:00.000000+00:00",
        "is_deleted": false,
        "other_fields_omitted_for_brevity": "..."
      },
      "old": {
        "job_id": "550e8400-e29b-41d4-a716-446655440000"
      }
    }
  }
}
```

> `new` contains the full updated row. With `REPLICA IDENTITY FULL` enabled on
> `jobs_final`, DELETE `old` payloads include all columns, including `job_id`.

---

## 8. Realtime WebSocket Integration

The Android app subscribes to all INSERT / UPDATE / DELETE events on the `jobs_final`
table using the **Supabase Realtime v1 / Phoenix channel** protocol.

> **Note:** As of 2026-04-07 the app subscribes to `jobs_final`, not `jobs_raw`.
> `REPLICA IDENTITY FULL` is enabled on `jobs_final` so DELETE events include
> `job_id` in the `old` payload.

### 8.1 Connection URL

```
wss://<PROJECT_HOST>/realtime/v1/websocket?apikey=<ANON_KEY>&vsn=1.0.0
```

### 8.2 Channel join message

Send the following JSON after the WebSocket connection opens:

```json
{
  "topic": "realtime:public:jobs_final",
  "event": "phx_join",
  "payload": {
    "config": {
      "broadcast": { "self": false },
      "presence": { "key": "" },
      "postgres_changes": [
        { "event": "*", "schema": "public", "table": "jobs_final" }
      ]
    },
    "access_token": "<ANON_KEY>"
  },
  "ref": "join_jobs"
}
```

### 8.3 Heartbeat

Respond to every `heartbeat` event from the server:

```json
{
  "topic": "phoenix",
  "event": "heartbeat",
  "payload": {},
  "ref": "<unique_ref>"
}
```

The Android app uses a 30-second OkHttp WebSocket `pingInterval` and an
`AtomicInteger` sequence counter for the ref.

### 8.4 Incoming event types

| `eventType` | `new` field | `old` field | Action |
|---|---|---|---|
| `INSERT` | Full new row | — | Insert/upsert into local store |
| `UPDATE` | Full updated row | PK only | Update local record matching `job_id` |
| `DELETE` | — | Full old row (Replica Identity Full) | Remove local record matching `job_id` |

### 8.5 Important: column name casing in realtime payloads

Supabase Realtime returns column names exactly as stored in Postgres. Because
the `jobs_final` table uses **snake_case** column names (`company_name`,
`job_url`, `job_id`, etc.), the realtime JSON keys will be in snake_case —
matching the `@SerializedName` annotations in `JobEntity`.

For DELETE events, `REPLICA IDENTITY FULL` ensures the `old` record contains
all columns (including `job_id`), not just the primary key.

---

## 9. Field Name Mapping (App ↔ Supabase `jobs_final`)

| Kotlin property (`JobEntity`) | Supabase / wire JSON key | PG type | Notes |
|---|---|---|---|
| `id` | `job_id` | `uuid` | Client-generated UUID. `@SerializedName(value = "job_id", alternate = ["id"])` |
| `companyName` | `company_name` | `text` | |
| `jobUrl` | `job_url` | `text` | Unique business key |
| `jobDescription` | `description` | `text` | |
| `jobTitle` | `role_title` | `text` | |
| `status` | `job_status` | `text` | Serialised as display name (e.g. `"Resume-Rejected"`). Reads legacy `status` too |
| `createdAt` | `saved_at` | `timestamptz` | Epoch ms ↔ ISO-8601 UTC. `@SerializedName(value = "saved_at", alternate = ["created_at"])` |
| `updatedAt` | `modified_at` | `timestamptz` | Epoch ms ↔ ISO-8601 UTC |
| `isDeleted` | `is_deleted` | `boolean` | |
| `language` | `language` | `text` | Default `"English"` |
| `matchScore` | `match_score` | `numeric` | Synced to `jobs_final`. Default `90` for app-created jobs |
| `prepNotes` | — | — | Local-only field; omitted from `jobs_final` writes |
| `sourcePlatform` | — | — | Local-only field; omitted from `jobs_final` writes |
| `filterReason` | — | — | Local-only field; omitted from `jobs_final` writes |

> `jobs_final` also has its own `id` (auto-generated PK) and `tags` (`text[]`)
> columns. The app sends `tags = null` and does not use the `id` column.

### Timestamp serialisation

The Android Gson adapter converts both directions:

| Direction | Format |
|---|---|
| **App → Supabase** | `Long` epoch ms → `"2026-04-03T05:40:10.421Z"` (ISO-8601 UTC) |
| **Supabase → App** | `"2026-03-31T06:00:30.479861+00:00"` → `Long` epoch ms |

Any client must handle both ISO-8601 UTC (`Z` suffix) and offset
(`+00:00`) notation when reading `saved_at` / `modified_at`.

---

## 10. JobStatus Enum Reference

| Kotlin enum constant | Supabase `job_status` value | Display label |
|---|---|---|
| `SAVED` | `Saved` | Saved |
| `APPLIED` | `Applied` | Applied |
| `INTERVIEW` | `Interview` | Interview |
| `INTERVIEWING` | `Interviewing` | Interviewing |
| `OFFER` | `Offer` | Offer |
| `RESUME_REJECTED` | `Resume-Rejected` | Resume-Rejected |
| `INTERVIEW_REJECTED` | `Interview-Rejected` | Interview-Rejected |

**Normalisation rule (`parseJobStatus`):**
Strip whitespace → uppercase → replace `-` and ` ` with `_` → map to enum.
Unknown values fall back to `SAVED`. The legacy value `REJECTED` is mapped to
`RESUME_REJECTED`.

Always write the **display-name form** (e.g. `"Resume-Rejected"`) to Supabase,
not the uppercase underscore form.

---

## 11. Conflict Resolution & Sync Logic

### 11.1 Upsert conflict key

All upsert operations use `on_conflict=job_id` on `jobs_final` with
`Prefer: resolution=merge-duplicates`.

### 11.2 Pull-based reconciliation (Android ↔ Supabase)

When the sync worker runs it:

1. Fetches all remote jobs via `GET /rest/v1/jobs_final?select=*&order=saved_at.desc`.
2. Fetches all local jobs from Room.
3. For each remote job, applies the following decision tree:

```
Remote job exists locally?
├─ NO  → Insert remote row into Room (including tombstones)
└─ YES → Compare modified_at timestamps
         ├─ |remote − local| > 120 000 ms AND remote > local
         │   → Remote wins: overwrite local row (preserve local PK)
         ├─ |remote − local| > 120 000 ms AND local > remote
         │   → Local wins: push local row to Supabase
         ├─ remote == local (exact tie)
         │   → Remote wins (deterministic convergence)
         └─ |remote − local| ≤ 120 000 ms (within clock-skew window)
             → Preserve local as-is (no push, no overwrite)
```

4. For each local job that has no remote counterpart → push to Supabase.

### 11.3 Conflict parameters

| Parameter | Value |
|---|---|
| Upsert conflict column | `job_id` (on `jobs_final`) |
| Clock-skew tolerance | `120 000` ms (2 minutes) |
| Tie-break rule | Remote wins (prevents oscillation) |
| Soft-delete propagation | `is_deleted = true` tombstone written locally; hard DELETE sent to Supabase `jobs_final` |

### 11.4 Outbox / offline writes

The Android app uses an **offline outbox** (persisted in `SharedPreferences`)
to queue failed sync operations. `OutboxSyncWorker` (WorkManager) drains the
outbox when network is available. Each operation has one of three types:

| `OutboxOperationType` | Action |
|---|---|
| `UPSERT` | Re-push a local job to Supabase |
| `DELETE` | Re-send a delete for a job ID |
| `SHARED_LINK` | Re-send a shared link insert |

New integrations that need reliable offline support should implement a similar
outbox pattern.

---

## 12. Row-Level Security Notes

- The app uses the **anon key** exclusively. Ensure your Supabase project's RLS
  policies allow `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on `public.jobs_final`
  and `INSERT` on `public.shared_links` for the `anon` role.
- If you add user authentication (JWT), update the `Authorization` header to use
  the user's JWT and adjust RLS policies accordingly.
- Do **not** embed the `service_role` key in any client application.

---

## 13. Quick-Start DDL (Copy-Paste)

Run the following SQL in your Supabase SQL Editor to recreate the schema from scratch:

```sql
-- ── Shared trigger function ──────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.set_modified_at()
RETURNS trigger AS $$
BEGIN
  NEW.modified_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── jobs_raw ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.jobs_raw (
  id              uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  company_name    text        NOT NULL,
  role_title      text        NOT NULL,
  description     text,
  job_url         text        UNIQUE,
  job_status      text        DEFAULT 'Saved'
    CHECK (job_status IN (
      'Saved','Applied','Interview','Interviewing','Offer',
      'Resume-Rejected','Interview-Rejected',
      'SAVED','APPLIED','INTERVIEW','INTERVIEWING','OFFER',
      'RESUME_REJECTED','INTERVIEW_REJECTED'
    )),
  language        text        DEFAULT 'English',
  pipeline_stage  text        NOT NULL DEFAULT 'SCRAPED',
  content_hash    text,
  external_id     text,
  location        text,
  scrape_run_id   text,
  created_at      timestamptz DEFAULT now(),
  modified_at     timestamptz DEFAULT now(),
  is_deleted      boolean     NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_jobs_raw_job_status_modified_at
  ON public.jobs_raw (job_status, modified_at DESC);

CREATE INDEX IF NOT EXISTS idx_jobs_raw_created_at
  ON public.jobs_raw (created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_jobs_content_hash
  ON public.jobs_raw (content_hash);

CREATE TRIGGER jobs_raw_set_modified_at
  BEFORE UPDATE ON public.jobs_raw
  FOR EACH ROW EXECUTE FUNCTION public.set_modified_at();

-- ── jobs_final (app primary table) ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.jobs_final (
  id             uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  job_id         uuid        UNIQUE REFERENCES public.jobs_raw(id) ON DELETE CASCADE,
  company_name   text,
  role_title     text,
  job_url        text,
  description    text,
  match_score    numeric,
  tags           text[],
  saved_at       timestamptz NOT NULL DEFAULT now(),
  job_status     text        DEFAULT 'Saved'
    CHECK (job_status IN (
      'Saved','Applied','Interview','Interviewing','Offer',
      'Resume-Rejected','Interview-Rejected',
      'SAVED','APPLIED','INTERVIEW','INTERVIEWING','OFFER',
      'RESUME_REJECTED','INTERVIEW_REJECTED'
    )),
  is_deleted     boolean     NOT NULL DEFAULT false,
  modified_at    timestamptz DEFAULT now(),
  language       text        DEFAULT 'English'
);

CREATE INDEX IF NOT EXISTS idx_jobs_final_saved_at
  ON public.jobs_final (saved_at DESC);

CREATE INDEX IF NOT EXISTS idx_jobs_final_job_status_modified_at
  ON public.jobs_final (job_status, modified_at DESC);

CREATE TRIGGER jobs_final_set_modified_at
  BEFORE UPDATE ON public.jobs_final
  FOR EACH ROW EXECUTE FUNCTION public.set_modified_at();

ALTER TABLE public.jobs_final REPLICA IDENTITY FULL;

-- ── shared_links ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.shared_links (
  id          uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  url         text        NOT NULL,
  source      text        DEFAULT 'android-share-intent'
    CHECK (source IN ('android-share-intent', 'web-extension', 'manual')),
  status      text        DEFAULT 'Pending',
  created_at  timestamptz DEFAULT now(),
  modified_at timestamptz DEFAULT now()
);

CREATE TRIGGER shared_links_set_modified_at
  BEFORE UPDATE ON public.shared_links
  FOR EACH ROW EXECUTE FUNCTION public.set_modified_at();
```

---

## 14. Integration Checklist

Use this checklist when building a new client against this Supabase backend:

- [ ] Obtain project URL and anon key from Supabase dashboard.
- [ ] Add `apikey` and `Authorization: Bearer` headers to every request.
- [ ] Use `is_deleted = false` filter on all active-job queries on `jobs_final`.
- [ ] Serialise/deserialise `saved_at` / `modified_at` as ISO-8601 UTC strings.
- [ ] Write `job_status` using the **display-name** form (`"Resume-Rejected"`, not `"RESUME_REJECTED"`).
- [ ] Upsert jobs via `POST /rest/v1/jobs_final?on_conflict=job_id` with `Prefer: resolution=merge-duplicates`.
- [ ] Use client-generated UUIDs for `job_id`.
- [ ] Default `match_score` to `90` for app-created jobs; send `tags = null`.
- [ ] Use `DELETE /rest/v1/jobs_final?job_id=eq.<uuid>` for hard deletes; treat `404` as success.
- [ ] Set `is_deleted = true` locally before the remote DELETE if you need soft-delete semantics.
- [ ] Subscribe to `realtime:public:jobs_final` for live updates (Phoenix channel protocol, see §8).
- [ ] Handle both `Z`-suffix and `+00:00`-offset ISO-8601 timestamps in realtime payloads.
- [ ] Implement an outbox / retry pattern for offline resilience.
- [ ] Respect the 2-minute clock-skew tolerance window in conflict resolution.
- [ ] Omit local-only fields (`prep_notes`, `source_platform`, `filter_reason`) from `jobs_final` writes.
- [ ] Use `jobs_enriched` as one-row-per-job enrichment state (`UNIQUE(job_id)`).
- [ ] Write only `AUTO_APPROVE | REVIEW | REJECT` into `job_decisions.decision`.
- [ ] Write only `APPROVED | REJECTED | PENDING` into `job_approvals.user_action`.
- [ ] Treat `jobs_final` as one-row-per-raw-job (`UNIQUE(job_id)`) — the app's primary Supabase table.
- [ ] Keep `job_metrics` as a singleton row (`id = 1`) with cumulative counters only.

---

## 15. Pipeline Tables (Phase 2-7)

These tables were added for the phased pipeline after `jobs_raw` ingestion.
They are intended for pipeline services and operator tooling. The Android app now
uses `jobs_final` as its primary jobs table and keeps `shared_links` for ingestion.

### 15.1 Table: `public.jobs_enriched`

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | Primary key |
| `job_id` | `uuid` | NOT NULL | — | FK to `public.jobs_raw(id)` with cascade delete |
| `tech_stack` | `text[]` | NULL | — | Structured stack extraction |
| `experience_level` | `text` | NULL | — | Parsed seniority |
| `remote_type` | `text` | NULL | — | Onsite/hybrid/remote classifier |
| `visa_sponsorship` | `boolean` | NULL | — | Parsed sponsorship signal |
| `english_friendly` | `boolean` | NULL | — | Parsed language friendliness |
| `created_at` | `timestamptz` | NOT NULL | `now()` | Enrichment write time |

**Constraints / indexes:**
- `jobs_enriched_job_id_fkey` → `public.jobs_raw(id)` (`ON DELETE CASCADE`)
- `jobs_enriched_job_id_key` (`UNIQUE(job_id)`, one enrichment row per job)
- `idx_jobs_enriched_created_at`

### 15.2 Table: `public.job_decisions`

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | Primary key |
| `job_id` | `uuid` | NOT NULL | — | FK to `public.jobs_raw(id)` |
| `match_score` | `numeric` | NULL | — | AI score |
| `decision` | `text` | NULL | — | Must be `AUTO_APPROVE`, `REVIEW`, or `REJECT` |
| `reason` | `text` | NULL | — | Decision rationale |
| `confidence` | `numeric` | NULL | — | AI confidence |
| `created_at` | `timestamptz` | NOT NULL | `now()` | Decision write time |

**Constraints / indexes:**
- `job_decisions_job_id_fkey` → `public.jobs_raw(id)` (`ON DELETE CASCADE`)
- `job_decisions_decision_check`
- `idx_job_decisions_job_id_created_at`
- `idx_job_decisions_decision_created_at`

### 15.3 Table: `public.job_approvals`

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | Primary key |
| `job_id` | `uuid` | NOT NULL | — | FK to `public.jobs_raw(id)` |
| `decision_id` | `uuid` | NOT NULL | — | FK to `public.job_decisions(id)` |
| `user_action` | `text` | NULL | — | Must be `APPROVED`, `REJECTED`, or `PENDING` |
| `approved_at` | `timestamptz` | NULL | — | User approval timestamp |
| `created_at` | `timestamptz` | NOT NULL | `now()` | Approval row creation time |

**Constraints / indexes:**
- `job_approvals_job_id_fkey` → `public.jobs_raw(id)` (`ON DELETE CASCADE`)
- `job_approvals_decision_id_fkey` → `public.job_decisions(id)` (`ON DELETE CASCADE`)
- `job_approvals_decision_id_key` (`UNIQUE(decision_id)`, one approval row per decision)
- `job_approvals_user_action_check`
- `idx_job_approvals_job_id_created_at`
- `idx_job_approvals_user_action_created_at`

### 15.4 Table: `public.jobs_final`

> **As of 2026-04-07 the Android app uses `jobs_final` as its primary Supabase
> table for all read, write, upsert, delete, and Realtime operations.** The app
> no longer operates on `jobs_raw` directly.

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `uuid` | NOT NULL | `gen_random_uuid()` | Primary key (auto-generated, not used by the app) |
| `job_id` | `uuid` | NOT NULL | — | FK to `public.jobs_raw(id)`. **Maps to `JobEntity.id`** — the app's business key |
| `company_name` | `text` | NULL | — | Final projected company |
| `role_title` | `text` | NULL | — | Final projected role |
| `job_url` | `text` | NULL | — | Final projected canonical URL |
| `description` | `text` | NULL | — | Final projected job description |
| `match_score` | `numeric` | NULL | — | Final accepted score. Default `90` for app-created jobs |
| `tags` | `text[]` | NULL | — | Final tags. App defaults to `null` |
| `saved_at` | `timestamptz` | NOT NULL | `now()` | Finalization / creation timestamp. Maps to `JobEntity.createdAt` |
| `job_status` | `text` | NULL | `'Saved'` | Application status (same CHECK constraint as `jobs_raw` §3.2) |
| `is_deleted` | `boolean` | NOT NULL | `false` | Soft-delete tombstone |
| `modified_at` | `timestamptz` | NULL | `now()` | Last modification time; auto-updated by trigger |
| `language` | `text` | NULL | `'English'` | Posting language |

**Constraints / indexes:**
- `jobs_final_job_id_fkey` → `public.jobs_raw(id)` (`ON DELETE CASCADE`)
- `jobs_final_job_id_key` (`UNIQUE(job_id)`, strict one final row per raw job)
- `idx_jobs_final_saved_at`
- `idx_jobs_final_job_status_modified_at` on `(job_status, modified_at DESC)`

**Triggers:**

| Trigger name | Fires | Function called |
|---|---|---|
| `jobs_final_set_modified_at` | `BEFORE UPDATE` on any column | `set_modified_at()` |

> **`REPLICA IDENTITY FULL`** is enabled on this table so that Supabase Realtime
> DELETE events include all columns in the `old` payload. This is required for
> the Android app to extract `job_id` from DELETE events and match them to local
> Room rows.

### 15.5 Table: `public.job_metrics`

| Column | PG Type | Nullable | Default | Notes |
|--------|---------|----------|---------|-------|
| `id` | `integer` | NOT NULL | — | Singleton key; constrained to `1` |
| `total_scraped` | `bigint` | NOT NULL | `0` | Cumulative scraped count |
| `total_approved` | `bigint` | NOT NULL | `0` | Cumulative approved count |
| `total_rejected` | `bigint` | NOT NULL | `0` | Cumulative rejected count |
| `created_at` | `timestamptz` | NOT NULL | `now()` | Row creation timestamp |
| `updated_at` | `timestamptz` | NOT NULL | `now()` | Last metrics update timestamp |

**Constraints / behavior:**
- `job_metrics_singleton_id_check` (`id = 1`)
- Non-negative checks for all counters
- Seed insert in migration ensures singleton row exists:
  - `INSERT INTO public.job_metrics (id) VALUES (1) ON CONFLICT (id) DO NOTHING;`
- This is intentionally a **minimal cumulative counter** table; trend/snapshot
  analytics are deferred to a future migration.

---

## 16. Pipeline Verification (SELECT Diagnostics)

After running the Phase 2-7 DDL migration, execute:

```sql
-- file: supabase/migrations/2026-04-05_phase2_phase7_pipeline_tables_singleton_metrics_verify.sql
```

The verification script is read-only (`SELECT` diagnostics only) and checks:
- Existence of all new tables.
- Column inventory for all Phase 2-7 tables.
- Constraints and index definitions.
- `jobs_final` uniqueness expectations (`COUNT(*) == COUNT(DISTINCT job_id)`).
- Singleton validity for `job_metrics` (`exactly one row`, `id = 1`).

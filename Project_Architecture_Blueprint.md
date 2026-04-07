# Project Architecture Blueprint

Generated on: 2026-04-03
Project: LinkedIn Job Tracker Pro

## 1) Active Architecture Snapshot

- Platform: Android (Kotlin), Jetpack Compose, Room, Coroutines/Flow.
- App pattern: MVVM with a single `JobViewModel`.
- Cloud integration: Supabase REST (`/rest/v1/jobs_raw`, `/rest/v1/shared_links`) + Realtime (`realtime:public:jobs_raw`).
- Local-first behavior: Room is UI source of truth; outbox/retry handles transient network failures.

## 2) Main Components

- `MainActivity`: share intent entry and state hoisting.
- `JobViewModel`: orchestration for scraping, local persistence, sync, and UI state.
- `JobScraper`: JSoup extraction with selector fallback.
- `JobDatabase` / `JobDao`: persistence and migrations.
- `SupabaseClient` + `SupabaseApiService`: REST transport.
- `SupabaseRepository`: pull/push logic and conflict decisions.
- `SupabaseRealtimeManager`: realtime `postgres_changes` stream for `public.jobs_raw`.
- `OutboxSyncWorker` + `OutboxWorkScheduler`: background retry pipeline.

## 3) Runtime Flow

1. User shares LinkedIn URL.
2. ViewModel triggers scraper and writes a local `JobEntity`.
3. ViewModel pushes raw-schema writes to `/rest/v1/jobs_raw` through repository/client.
4. Realtime events from `realtime:public:jobs_raw` and pull sync reconcile local Room records.
5. UI observes `allJobs`/`jobs` state flows and re-renders.

## 4) Data and Sync Rules

- Business key: `jobUrl` (wire: `job_url`).
- Client conflict field: `updatedAt` (wire: `modified_at`).
- Tie-breaking policy: remote wins on exact timestamp ties.
- Clock skew tolerance: +/- 2 minutes.
- Soft delete support: `isDeleted` (wire: `is_deleted`).

## 5) Field Mapping

`JobEntity` serializes with snake_case wire keys for Supabase:

- `companyName` -> `company_name`
- `jobUrl` -> `job_url`
- `jobDescription` -> `description`
- `jobTitle` -> `role_title`
- `status` -> `job_status` (reads legacy `status` inbound)
- `createdAt` -> `created_at`
- `updatedAt` -> `modified_at`
- `pipeline_stage` -> currently set/normalized on server (`SCRAPED` default)
- `matchScore`, `prepNotes`, `sourcePlatform`, `filterReason` -> local-only fields (omitted from `jobs_raw` writes)

## 6) Guardrails

- Keep Compose screens stateless; business logic stays in `JobViewModel`.
- Keep `allJobs` unfiltered; derive filtered list via flow composition.
- Add explicit Room migrations for every schema change.
- Preserve outbox-based retry behavior for mutation durability.
- Keep docs aligned with active Supabase runtime only.

## 7) Verification Checklist for Architecture Changes

- `./gradlew build` passes.
- `./gradlew test` passes.
- Room schema JSON updated/committed when schema changes.
- Sync behavior tested for insert/update/delete and conflict cases.

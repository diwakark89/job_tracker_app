# UI Improvement Checklist

Design authority: screenshot parity + existing Material 3 dynamic color behavior (`dynamicColor = true` in `LinkedIn_Job_TrackerTheme`).

Scope: visual refresh for Job List, Job Details, and Sync Dashboard. No navigation, data, sync, or state-management behavior changes.

## Shared requirements (cross-screen)

| ID | Requirement | Acceptance criteria | Touchpoints | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| UI-REQ-001 | Spacing rhythm and section density are consistent | Outer spacing uses a predictable rhythm; related section blocks have clear grouping and breathing room | `JobListScreen`, `JobDetailsScreen`, `SyncDashboardScreen`, `SyncStatusBanner`, `JobCard` | [x] | Applied shared spacing rhythm across list/details/dashboard cards and banner blocks. |
| UI-REQ-002 | Card and container shape language is unified | Cards/banners/chips use rounded shapes with consistent hierarchy (large cards > small pills) | `JobCard`, `SyncStatusBanner`, `SyncDashboardScreen`, `JobDetailsScreen` | [x] | Standardized rounded card and pill shapes for refreshed surfaces and chips. |
| UI-REQ-003 | Typography hierarchy improves scanability | Company/title/status labels are visually distinct and readable in both themes | `JobCard`, `JobListScreen`, `SyncDashboardScreen`, `JobDetailsScreen` | [x] | Updated title/label emphasis and overflow handling for key text fields. |
| UI-REQ-004 | Status and sync badges use accessible emphasis | Badge color treatment differentiates states without reducing readability; labels remain short and clear | `StatusChip`, `StatusChipLarge`, `SyncBadge`, `SyncDashboardScreen` | [x] | Stronger chip/badge contrast and clearer pill affordance applied. |
| UI-REQ-005 | Interactive controls meet accessibility targets | Tap targets are at least 48dp where applicable; icon actions remain discoverable | `SyncStatusBanner`, `SyncDashboardScreen`, `JobListScreen`, `JobDetailsScreen` | [x] | Primary controls kept on Material buttons/icon buttons with default target sizing. |
| UI-REQ-006 | Truncation behavior preserves key context | Long text truncates predictably (`maxLines`/ellipsis) without collapsing visual rhythm | `JobCard`, `JobDetailsScreen` | [x] | Added explicit `maxLines` and `TextOverflow.Ellipsis` on long-form labels. |
| UI-REQ-007 | Contrast and theming are robust with dynamic color | UI remains legible under light/dark and dynamic color palettes | `Theme`, all refreshed components | [x] | Kept `dynamicColor = true`; refreshed UI still references `MaterialTheme.colorScheme`. |
| UI-REQ-008 | Requirement wording aligns with current model enums | Status naming reflects current `JobStatus` values (`SAVED`, `APPLIED`, `INTERVIEW`, `RESUME_REJECTED`, `INTERVIEW_REJECTED`) | Checklist + UI labels | [x] | Checklist wording aligned to existing enum values. |
| UI-REQ-009 | Accessibility semantics are explicit on core interactive surfaces | Key cards/chips/actions include meaningful `contentDescription`/`stateDescription` where appropriate | `JobCard`, `JobDetailsScreen`, `SyncDashboardScreen` | [x] | Added explicit semantics on status controls and dashboard state cards. |
| UI-REQ-010 | Swipe dismissal implementation uses non-deprecated API pattern | Swipe gesture still prompts delete confirmation and row resets without direct dismissal | `JobListScreen` | [x] | Replaced deprecated `confirmValueChange` usage with observed state + `reset()`; added compose test `JobListScreenSwipeDismissTest`. |

## Screen mapping

- Job List: `JobListScreen`, `SyncStatusBanner`, `JobCard`, `StatusChip`, `SyncBadge`
- Job Details: `JobDetailsScreen`, `StatusChipLarge`
- Sync Dashboard: `SyncDashboardScreen`
- Non-functional boundary: `AppNavigation`

## Execution tracking

- [x] Baseline completed for all touchpoints
- [x] Shared theme/token updates completed
- [x] Job List refresh completed
- [x] Job Details refresh completed
- [x] Sync Dashboard refresh completed
- [x] Accessibility polish pass completed
- [x] Swipe-dismiss deprecation migration completed
- [ ] Manual visual QA (light, dark, dynamic color) completed


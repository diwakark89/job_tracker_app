package com.thewalkersoft.linkedin_job_tracker.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperation
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperationType

data class SyncFailureDiagnostic(
    val reason: String,
    val updatedAt: Long = System.currentTimeMillis()
)

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun saveLastSyncTime(lastSyncTime: String) {
        sharedPreferences.edit().putString(KEY_LAST_SYNC_TIME, lastSyncTime).apply()
    }

    fun saveLastSyncTimeMillis(timestampMillis: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_SYNC_TIME_MILLIS, timestampMillis).apply()
    }

    fun getLastSyncTimeMillis(): Long? {
        val timestamp = sharedPreferences.getLong(KEY_LAST_SYNC_TIME_MILLIS, -1L)
        return if (timestamp > 0L) timestamp else null
    }

    fun getLastSyncTime(): String {
        return sharedPreferences.getString(KEY_LAST_SYNC_TIME, "Never") ?: "Never"
    }

    fun saveLastSyncFailedPushCount(count: Int) {
        sharedPreferences.edit().putInt(KEY_LAST_SYNC_FAILED_PUSH_COUNT, count.coerceAtLeast(0)).apply()
    }

    fun getLastSyncFailedPushCount(): Int {
        return sharedPreferences.getInt(KEY_LAST_SYNC_FAILED_PUSH_COUNT, 0)
    }

    fun saveLastSyncFailureReason(jobUrl: String, reason: String) {
        if (jobUrl.isBlank() || reason.isBlank()) return

        val updated = getSyncFailureDiagnostics().toMutableMap().apply {
            put(jobUrl, SyncFailureDiagnostic(reason = reason.take(MAX_SYNC_FAILURE_REASON_LENGTH)))
        }

        saveSyncFailureDiagnostics(
            updated.entries
                .sortedByDescending { it.value.updatedAt }
                .take(MAX_SYNC_FAILURE_ENTRIES)
                .associate { it.toPair() }
        )
    }

    fun getLastSyncFailureReason(jobUrl: String): SyncFailureDiagnostic? {
        if (jobUrl.isBlank()) return null
        return getSyncFailureDiagnostics()[jobUrl]
    }

    fun getAllSyncFailureReasons(): Map<String, SyncFailureDiagnostic> {
        return getSyncFailureDiagnostics()
    }

    fun clearLastSyncFailureReason(jobUrl: String) {
        if (jobUrl.isBlank()) return

        val updated = getSyncFailureDiagnostics().toMutableMap()
        if (updated.remove(jobUrl) != null) {
            saveSyncFailureDiagnostics(updated)
        }
    }

    fun enqueueOperation(operation: OutboxOperation): Int {
        val all = (getOutboxOperations() + operation)
        val compacted = compactOperations(all)
        saveOutboxOperations(compacted)
        incrementLifetimeCounter(KEY_METRIC_LIFETIME_QUEUED, 1)
        incrementRollingCounter(KEY_METRIC_ROLLING_QUEUED)
        return compacted.size
    }

    fun getOutboxOperations(): List<OutboxOperation> {
        val keys = getOutboxKeys()
        return keys.mapNotNull { key ->
            val json = sharedPreferences.getString(outboxItemKey(key), null) ?: return@mapNotNull null
            runCatching { gson.fromJson(json, OutboxOperation::class.java) }.getOrNull()
        }.sortedBy { it.createdAt }
    }

    fun acknowledgeOperation(operationKey: String) {
        val keys = getOutboxKeys().toMutableSet()
        if (keys.remove(operationKey)) {
            sharedPreferences.edit()
                .remove(outboxItemKey(operationKey))
                .putStringSet(KEY_OUTBOX_KEYS, keys)
                .apply()
            incrementLifetimeCounter(KEY_METRIC_LIFETIME_REPLAYED, 1)
            incrementRollingCounter(KEY_METRIC_ROLLING_REPLAYED)
        }
    }

    fun compactOutbox(): Int {
        val current = getOutboxOperations()
        val compacted = compactOperations(current)
        val removed = (current.size - compacted.size).coerceAtLeast(0)
        if (removed > 0) {
            saveOutboxOperations(compacted)
            incrementLifetimeCounter(KEY_METRIC_LIFETIME_COMPACTED, removed.toLong())
            incrementRollingCounter(KEY_METRIC_ROLLING_COMPACTED, removed)
        }
        return removed
    }

    fun clearMetricsAndOutbox() {
        val editor = sharedPreferences.edit()
        getOutboxKeys().forEach { key -> editor.remove(outboxItemKey(key)) }
        editor.remove(KEY_OUTBOX_KEYS)
        editor.remove(KEY_SYNC_FAILURE_DIAGNOSTICS)
        editor.remove(KEY_METRIC_ROLLING_QUEUED)
        editor.remove(KEY_METRIC_ROLLING_COMPACTED)
        editor.remove(KEY_METRIC_ROLLING_REPLAYED)
        editor.remove(KEY_LAST_SYNC_FAILED_PUSH_COUNT)
        editor.apply()
    }

    fun getRollingMetricsSummary(): Triple<Int, Int, Int> {
        return Triple(
            getRollingCounter(KEY_METRIC_ROLLING_QUEUED),
            getRollingCounter(KEY_METRIC_ROLLING_COMPACTED),
            getRollingCounter(KEY_METRIC_ROLLING_REPLAYED)
        )
    }

    fun getLifetimeMetricsSummary(): Triple<Long, Long, Long> {
        return Triple(
            sharedPreferences.getLong(KEY_METRIC_LIFETIME_QUEUED, 0L),
            sharedPreferences.getLong(KEY_METRIC_LIFETIME_COMPACTED, 0L),
            sharedPreferences.getLong(KEY_METRIC_LIFETIME_REPLAYED, 0L)
        )
    }

    fun isPendingDiagnosticsReset(): Boolean =
        sharedPreferences.getBoolean(KEY_PENDING_DIAGNOSTICS_RESET, false)

    fun setPendingDiagnosticsReset(pending: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PENDING_DIAGNOSTICS_RESET, pending).apply()
    }

    private fun compactOperations(items: List<OutboxOperation>): List<OutboxOperation> {
        val shared = items.filter { it.type == OutboxOperationType.SHARED_LINK }
        val jobOps = items.filter { it.type != OutboxOperationType.SHARED_LINK }

        val grouped = jobOps.groupBy { it.jobUrl }
        val compacted = mutableListOf<OutboxOperation>()

        grouped.forEach { (_, ops) ->
            val newestDelete = ops.filter { it.type == OutboxOperationType.DELETE }
                .maxByOrNull { it.lastModified }

            if (newestDelete != null) {
                compacted += newestDelete
            } else {
                val byType = ops.groupBy { it.type }
                byType.forEach { (_, sameTypeOps) ->
                    val newest = sameTypeOps.maxByOrNull { it.lastModified }
                    if (newest != null) compacted += newest
                }
            }
        }

        return (compacted + shared)
            .sortedBy { it.createdAt }
    }

    private fun saveOutboxOperations(operations: List<OutboxOperation>) {
        val editor = sharedPreferences.edit()
        getOutboxKeys().forEach { key -> editor.remove(outboxItemKey(key)) }

        val keys = mutableSetOf<String>()
        operations.forEach { operation ->
            keys += operation.key
            editor.putString(outboxItemKey(operation.key), gson.toJson(operation))
        }

        editor.putStringSet(KEY_OUTBOX_KEYS, keys)
        editor.apply()
    }

    private fun getOutboxKeys(): Set<String> {
        return sharedPreferences.getStringSet(KEY_OUTBOX_KEYS, emptySet()) ?: emptySet()
    }

    private fun outboxItemKey(key: String): String = "${KEY_OUTBOX_ITEM_PREFIX}$key"

    private fun incrementLifetimeCounter(key: String, delta: Long) {
        val current = sharedPreferences.getLong(key, 0L)
        sharedPreferences.edit().putLong(key, current + delta).apply()
    }

    private fun incrementRollingCounter(key: String, delta: Int = 1) {
        val nowBucket = currentMinuteBucket()
        val map = getBucketedCounter(key).toMutableMap()
        val current = map[nowBucket] ?: 0
        map[nowBucket] = current + delta

        val minBucket = nowBucket - 59
        map.keys.removeAll { it < minBucket }
        saveBucketedCounter(key, map)
    }

    private fun getRollingCounter(key: String): Int {
        val nowBucket = currentMinuteBucket()
        val minBucket = nowBucket - 59
        return getBucketedCounter(key)
            .filterKeys { it >= minBucket }
            .values
            .sum()
    }

    private fun getBucketedCounter(key: String): Map<Long, Int> {
        val json = sharedPreferences.getString(key, null) ?: return emptyMap()
        val type = object : TypeToken<Map<Long, Int>>() {}.type
        return runCatching { gson.fromJson<Map<Long, Int>>(json, type) }.getOrDefault(emptyMap())
    }

    private fun getSyncFailureDiagnostics(): Map<String, SyncFailureDiagnostic> {
        val json = sharedPreferences.getString(KEY_SYNC_FAILURE_DIAGNOSTICS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, SyncFailureDiagnostic>>() {}.type
        return runCatching {
            gson.fromJson<Map<String, SyncFailureDiagnostic>>(json, type)
        }.getOrDefault(emptyMap())
    }

    private fun saveSyncFailureDiagnostics(map: Map<String, SyncFailureDiagnostic>) {
        sharedPreferences.edit().putString(KEY_SYNC_FAILURE_DIAGNOSTICS, gson.toJson(map)).apply()
    }

    private fun saveBucketedCounter(key: String, map: Map<Long, Int>) {
        sharedPreferences.edit().putString(key, gson.toJson(map)).apply()
    }

    private fun currentMinuteBucket(): Long = System.currentTimeMillis() / 60_000L

    companion object {
        private const val PREFERENCES_NAME = "linkedin_job_tracker_prefs"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_LAST_SYNC_TIME_MILLIS = "last_sync_time_millis"
        private const val KEY_LAST_SYNC_FAILED_PUSH_COUNT = "last_sync_failed_push_count"
        private const val KEY_SYNC_FAILURE_DIAGNOSTICS = "sync_failure_diagnostics"

        private const val KEY_OUTBOX_KEYS = "outbox_keys"
        private const val KEY_OUTBOX_ITEM_PREFIX = "outbox_item_"

        private const val KEY_METRIC_LIFETIME_QUEUED = "metric_lifetime_queued"
        private const val KEY_METRIC_LIFETIME_COMPACTED = "metric_lifetime_compacted"
        private const val KEY_METRIC_LIFETIME_REPLAYED = "metric_lifetime_replayed"

        private const val KEY_METRIC_ROLLING_QUEUED = "metric_rolling_queued"
        private const val KEY_METRIC_ROLLING_COMPACTED = "metric_rolling_compacted"
        private const val KEY_METRIC_ROLLING_REPLAYED = "metric_rolling_replayed"

        private const val KEY_PENDING_DIAGNOSTICS_RESET = "pending_diagnostics_reset"
        private const val MAX_SYNC_FAILURE_ENTRIES = 100
        private const val MAX_SYNC_FAILURE_REASON_LENGTH = 180
    }
}

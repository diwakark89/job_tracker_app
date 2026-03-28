package com.thewalkersoft.linkedin_job_tracker.sync

import com.google.gson.JsonObject

/**
 * Sealed class representing typed realtime change events received from the
 * Supabase Realtime WebSocket for the `jobs` table.
 */
sealed class RealtimeJobEvent {
    data class Insert(val record: JsonObject) : RealtimeJobEvent()
    data class Update(val record: JsonObject) : RealtimeJobEvent()
    data class Delete(val oldRecord: JsonObject) : RealtimeJobEvent()
}

enum class RealtimeConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}


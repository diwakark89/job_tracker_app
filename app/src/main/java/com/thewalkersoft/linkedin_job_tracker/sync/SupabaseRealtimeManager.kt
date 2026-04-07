package com.thewalkersoft.linkedin_job_tracker.sync

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.thewalkersoft.linkedin_job_tracker.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages a persistent WebSocket connection to Supabase Realtime using the
 * Phoenix channel protocol. Subscribes to all INSERT / UPDATE / DELETE events
 * on the `jobs_final` table and emits them as a [SharedFlow].
 *
 * NOTE: Column names in the realtime payload match the actual Postgres column
 * names. The `jobs_final` table uses snake_case column names (`company_name`,
 * `job_url`, `job_id`, etc.), matching the `@SerializedName` annotations in
 * `JobEntity`. `REPLICA IDENTITY FULL` is enabled on `jobs_final` so that
 * DELETE events include all columns (specifically `job_id`).
 */
class SupabaseRealtimeManager {

    private val gson = Gson()
    private val heartbeatSeq = AtomicInteger(0)

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _jobEvents = MutableSharedFlow<RealtimeJobEvent>(extraBufferCapacity = 64)
    val jobEvents: SharedFlow<RealtimeJobEvent> = _jobEvents.asSharedFlow()

    private var webSocket: WebSocket? = null

    // Separate OkHttpClient with infinite read timeout required for WebSocket.
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        if (url.isBlank() || key.isBlank()) {
            Log.d(TAG, "Supabase not configured – skipping realtime connection")
            return
        }

        if (_connectionState.value == RealtimeConnectionState.CONNECTING ||
            _connectionState.value == RealtimeConnectionState.CONNECTED
        ) return

        val wsUrl = url
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/') + "/realtime/v1/websocket?apikey=$key&vsn=1.0.0"

        Log.d(TAG, "Connecting to Supabase Realtime…")
        _connectionState.value = RealtimeConnectionState.CONNECTING

        val request = Request.Builder().url(wsUrl).build()
        webSocket = wsClient.newWebSocket(request, socketListener)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
        Log.d(TAG, "Disconnected from Supabase Realtime")
    }

    // ── WebSocket listener ──────────────────────────────────────────────────

    private val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            _connectionState.value = RealtimeConnectionState.CONNECTED
            joinJobsChannel(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(webSocket, text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Connection failure: ${t.message}")
            _connectionState.value = RealtimeConnectionState.ERROR
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed ($code): $reason")
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
        }
    }

    // ── Protocol helpers ────────────────────────────────────────────────────

    private fun joinJobsChannel(ws: WebSocket) {
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

        val payload = """
            {
              "topic":"$REALTIME_TOPIC",
              "event":"phx_join",
              "payload":{
                "config":{
                  "broadcast":{"self":false},
                  "presence":{"key":""},
                  "postgres_changes":[{"event":"*","schema":"public","table":"$REALTIME_TABLE"}]
                },
                "access_token":"$key"
              },
              "ref":"join_jobs"
            }
        """.trimIndent()
        ws.send(payload)
        Log.d(TAG, "Sent phx_join for $REALTIME_TABLE channel")
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        try {
            val msg = gson.fromJson(text, JsonObject::class.java) ?: return
            when (msg.get("event")?.asString) {
                "phx_reply"       -> Log.d(TAG, "Channel ack: ${msg.get("ref")?.asString}")
                "heartbeat"       -> replyHeartbeat(ws)
                "postgres_changes" -> dispatchPostgresChange(msg)
                else              -> { /* phx_error, presence, etc. */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Message parse error: ${e.message}")
        }
    }

    private fun replyHeartbeat(ws: WebSocket) {
        val ref = "hb_${heartbeatSeq.incrementAndGet()}"
        ws.send("""{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}""")
    }

    private fun dispatchPostgresChange(msg: JsonObject) {
        val data = msg.getAsJsonObject("payload")?.getAsJsonObject("data") ?: return
        val eventType = data.get("eventType")?.asString?.uppercase() ?: return
        val newRecord = data.getAsJsonObject("new")
        val oldRecord = data.getAsJsonObject("old")

        val event: RealtimeJobEvent? = when (eventType) {
            "INSERT" -> newRecord?.let { RealtimeJobEvent.Insert(it) }
            "UPDATE" -> newRecord?.let { RealtimeJobEvent.Update(it) }
            "DELETE" -> oldRecord?.let { RealtimeJobEvent.Delete(it) }
            else     -> null
        }
        event?.let {
            _jobEvents.tryEmit(it)
            Log.d(TAG, "Emitted $eventType event")
        }
    }

    companion object {
        private const val REALTIME_TOPIC = "realtime:public:jobs_final"
        private const val REALTIME_TABLE = "jobs_final"
        private const val TAG = "SupabaseRealtime"
    }
}


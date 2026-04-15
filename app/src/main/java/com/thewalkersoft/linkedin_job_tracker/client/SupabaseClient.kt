package com.thewalkersoft.linkedin_job_tracker.client

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.Strictness
import com.thewalkersoft.linkedin_job_tracker.BuildConfig
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.parseJobStatus
import com.thewalkersoft.linkedin_job_tracker.service.SupabaseApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.time.Instant
import java.time.OffsetDateTime

object SupabaseClient {

    private val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun isCloudConfigured(): Boolean = isConfigured

    /**
     * Shared Gson instance that serialises [JobStatus] using uppercase enum values
     * ("SAVED", "APPLIED", "INTERVIEW", "INTERVIEWING", "OFFER",
     * "RESUME_REJECTED", "INTERVIEW_REJECTED") to match the Supabase
     * `jobs_final.job_status` check constraint, and deserialises back via
     * [parseJobStatus]. Used for both REST calls and realtime event parsing.
     *
     * The Long TypeAdapter converts epoch-millisecond timestamps to/from ISO-8601
     * strings so that Supabase's `timestamptz` columns are handled correctly:
     *   - Serialize: Long millis → ISO-8601 UTC string (e.g. "2026-04-03T05:40:10.421Z")
     *   - Deserialize: ISO-8601 string (e.g. "2026-03-31T06:00:30.479861+00:00") → Long millis
     */
    private val timestampAdapter = object : JsonSerializer<Long>, JsonDeserializer<Long> {
        override fun serialize(
            src: Long, typeOfSrc: Type, context: JsonSerializationContext
        ): JsonElement = JsonPrimitive(Instant.ofEpochMilli(src).toString())

        override fun deserialize(
            json: JsonElement, typeOfT: Type, context: JsonDeserializationContext
        ): Long {
            val prim = json.asJsonPrimitive
            return when {
                prim.isNumber -> prim.asLong
                prim.isString -> {
                    val raw = prim.asString
                    raw.toLongOrNull()
                        ?: OffsetDateTime.parse(raw).toInstant().toEpochMilli()
                }
                else -> 0L
            }
        }
    }

    val supabaseGson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .registerTypeAdapter(JobStatus::class.java, object :
            JsonSerializer<JobStatus>, JsonDeserializer<JobStatus> {
            override fun serialize(
                src: JobStatus, typeOfSrc: Type, context: JsonSerializationContext
            ): JsonElement = JsonPrimitive(src.name)

            override fun deserialize(
                json: JsonElement, typeOfT: Type, context: JsonDeserializationContext
            ): JobStatus = parseJobStatus(json.asString)
        })
        // Long TypeAdapter: converts epoch-millis ↔ ISO-8601 for Supabase timestamptz columns.
        // Registered for both primitive (long) and boxed (Long) so Gson picks it up regardless
        // of how Kotlin/Gson resolves the field type at runtime.
        .registerTypeAdapter(Long::class.java, timestampAdapter)
        .registerTypeAdapter(Long::class.javaObjectType, timestampAdapter)
        .create()

    val instance: SupabaseApiService by lazy {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/') + "/"
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(supabaseGson))
            .build()
            .create(SupabaseApiService::class.java)
    }

    private val authInterceptor = Interceptor { chain ->
        val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        val request = chain.request().newBuilder()
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("SUPABASE_HTTP", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()
}

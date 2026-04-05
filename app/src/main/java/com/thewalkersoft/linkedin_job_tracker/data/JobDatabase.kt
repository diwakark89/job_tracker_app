package com.thewalkersoft.linkedin_job_tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JobEntity::class], version = 8, exportSchema = true)
@TypeConverters(Converters::class)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao

    companion object {
        @Volatile
        private var INSTANCE: JobDatabase? = null

        // Migration from version 1 to version 2: Add lastModified column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add lastModified column with default value = timestamp
                database.execSQL(
                    "ALTER TABLE jobs ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0"
                )
                // Update existing rows to have lastModified = timestamp
                database.execSQL(
                    "UPDATE jobs SET lastModified = timestamp WHERE lastModified = 0"
                )
            }
        }

        // Migration from version 2 to version 3: Add jobTitle column
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add jobTitle column with default value = empty string
                database.execSQL(
                    "ALTER TABLE jobs ADD COLUMN jobTitle TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Migration from version 3 to version 4: move to UUID string IDs + cloud fields
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jobs_new (
                        id TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        jobUrl TEXT NOT NULL,
                        jobDescription TEXT NOT NULL,
                        jobTitle TEXT NOT NULL,
                        status TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        matchScore INTEGER,
                        language TEXT NOT NULL DEFAULT 'English',
                        prepNotes TEXT,
                        sourcePlatform TEXT,
                        filterReason TEXT,
                        createdAt TEXT,
                        updatedAt TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO jobs_new (
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        timestamp, lastModified, matchScore, language, prepNotes,
                        sourcePlatform, filterReason, createdAt, updatedAt
                    )
                    SELECT
                        lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || '4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + abs(random()) % 4, 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) AS id,
                        companyName,
                        jobUrl,
                        jobDescription,
                        jobTitle,
                        CASE status
                            WHEN 'SAVED' THEN 'Saved'
                            WHEN 'APPLIED' THEN 'Applied'
                            WHEN 'INTERVIEWING' THEN 'Interview'
                            WHEN 'OFFER' THEN 'Interview'
                            WHEN 'RESUME_REJECTED' THEN 'Resume-Rejected'
                            WHEN 'INTERVIEW_REJECTED' THEN 'Interview-Rejected'
                            ELSE 'Saved'
                        END AS status,
                        timestamp,
                        lastModified,
                        NULL,
                        'English',
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        NULL
                    FROM jobs
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE jobs")
                database.execSQL("ALTER TABLE jobs_new RENAME TO jobs")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_jobUrl ON jobs(jobUrl)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_status_lastModified ON jobs(status, lastModified DESC)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_jobs_timestamp ON jobs(timestamp DESC)")
            }
        }

        // Migration from version 4 to version 5: enforce unique jobUrl for all install paths.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Keep exactly one row per jobUrl before creating the unique index.
                database.execSQL(
                    """
                    DELETE FROM jobs
                    WHERE rowid NOT IN (
                        SELECT winner.rowid
                        FROM jobs AS winner
                        WHERE winner.rowid = (
                            SELECT candidate.rowid
                            FROM jobs AS candidate
                            WHERE candidate.jobUrl = winner.jobUrl
                            ORDER BY candidate.lastModified DESC, candidate.timestamp DESC, candidate.id DESC
                            LIMIT 1
                        )
                    )
                    """.trimIndent()
                )

                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_jobUrl ON jobs(jobUrl)")
            }
        }

        // Migration from version 5 to version 6: normalize jobs table so it exactly matches
        // the Room entity schema (no SQL defaults on optional fields and only entity indices).
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jobs_new (
                        id TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        jobUrl TEXT NOT NULL,
                        jobDescription TEXT NOT NULL,
                        jobTitle TEXT NOT NULL,
                        status TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        matchScore INTEGER,
                        language TEXT NOT NULL,
                        prepNotes TEXT,
                        sourcePlatform TEXT,
                        filterReason TEXT,
                        createdAt TEXT,
                        updatedAt TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                // Keep one row per jobUrl and preserve the newest version when duplicates exist.
                database.execSQL(
                    """
                    INSERT INTO jobs_new (
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        timestamp, lastModified, matchScore, language, prepNotes,
                        sourcePlatform, filterReason, createdAt, updatedAt
                    )
                    SELECT
                        candidate.id,
                        candidate.companyName,
                        candidate.jobUrl,
                        candidate.jobDescription,
                        candidate.jobTitle,
                        candidate.status,
                        candidate.timestamp,
                        candidate.lastModified,
                        candidate.matchScore,
                        COALESCE(candidate.language, 'English') AS language,
                        candidate.prepNotes,
                        candidate.sourcePlatform,
                        candidate.filterReason,
                        candidate.createdAt,
                        candidate.updatedAt
                    FROM jobs AS candidate
                    WHERE candidate.rowid = (
                        SELECT winner.rowid
                        FROM jobs AS winner
                        WHERE winner.jobUrl = candidate.jobUrl
                        ORDER BY winner.lastModified DESC, winner.timestamp DESC, winner.id DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE jobs")
                database.execSQL("ALTER TABLE jobs_new RENAME TO jobs")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_jobUrl ON jobs(jobUrl)")
            }
        }

        // Migration from version 6 to version 7: add tombstone flag for soft-delete sync.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jobs_new (
                        id TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        jobUrl TEXT NOT NULL,
                        jobDescription TEXT NOT NULL,
                        jobTitle TEXT NOT NULL,
                        status TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL,
                        matchScore INTEGER,
                        language TEXT NOT NULL,
                        prepNotes TEXT,
                        sourcePlatform TEXT,
                        filterReason TEXT,
                        createdAt TEXT,
                        updatedAt TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO jobs_new (
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        timestamp, lastModified, isDeleted, matchScore, language, prepNotes,
                        sourcePlatform, filterReason, createdAt, updatedAt
                    )
                    SELECT
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        timestamp, lastModified, 0, matchScore, language, prepNotes,
                        sourcePlatform, filterReason, createdAt, updatedAt
                    FROM jobs
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE jobs")
                database.execSQL("ALTER TABLE jobs_new RENAME TO jobs")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_jobUrl ON jobs(jobUrl)")
            }
        }

        // Migration from version 7 to version 8:
        //   - Rename timestamp (Long) -> createdAt (Long)
        //   - Rename lastModified (Long) -> updatedAt (Long)
        //   - Drop old createdAt TEXT and updatedAt TEXT columns (were ISO-string server timestamps)
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jobs_new (
                        id TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        jobUrl TEXT NOT NULL,
                        jobDescription TEXT NOT NULL,
                        jobTitle TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL,
                        matchScore INTEGER,
                        language TEXT NOT NULL,
                        prepNotes TEXT,
                        sourcePlatform TEXT,
                        filterReason TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO jobs_new (
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        createdAt, updatedAt, isDeleted, matchScore, language,
                        prepNotes, sourcePlatform, filterReason
                    )
                    SELECT
                        id, companyName, jobUrl, jobDescription, jobTitle, status,
                        timestamp, lastModified, isDeleted, matchScore,
                        COALESCE(language, 'English'),
                        prepNotes, sourcePlatform, filterReason
                    FROM jobs
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE jobs")
                database.execSQL("ALTER TABLE jobs_new RENAME TO jobs")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_jobs_jobUrl ON jobs(jobUrl)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )

        fun getDatabase(context: Context): JobDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JobDatabase::class.java,
                    "job_database"
                )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromJobStatus(status: JobStatus): String {
        return status.displayName()
    }

    @TypeConverter
    fun toJobStatus(value: String): JobStatus {
        return parseJobStatus(value)
    }
}

package com.thewalkersoft.linkedin_job_tracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JobDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_deduplicatesJobUrlAndEnforcesUniqueIndex() {
        val dbName = testDbName("dedupe")

        helper.createDatabase(dbName, 4).apply {
            seedVersion4Rows(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            JobDatabase.MIGRATION_4_5
        )

        assertEquals(1L, longForQuery(migratedDb, "SELECT COUNT(*) FROM jobs WHERE jobUrl = '$DUP_URL'"))
        assertEquals(1L, longForQuery(migratedDb, "SELECT COUNT(*) FROM jobs WHERE jobUrl = '$TIE_URL'"))

        assertEquals("dup-new", singleIdForUrl(migratedDb, DUP_URL))
        assertEquals("tie-b", singleIdForUrl(migratedDb, TIE_URL))

        val duplicateInsertError = runCatching {
            insertJobV4(
                db = migratedDb,
                id = "dup-should-fail",
                companyName = "Conflict Co",
                jobUrl = DUP_URL,
                jobDescription = "Duplicate that should fail",
                jobTitle = "Senior Android Engineer",
                status = "Saved",
                timestamp = 9999,
                lastModified = 9999
            )
        }.exceptionOrNull()

        assertNotNull("Expected UNIQUE constraint failure when inserting duplicate jobUrl", duplicateInsertError)
        assertTrue(
            "Expected UNIQUE constraint failure message, got: ${duplicateInsertError?.message}",
            duplicateInsertError?.message?.contains("UNIQUE constraint failed", ignoreCase = true) == true
        )
    }

    @Test
    fun migrate4To5_whenLastModifiedAndTimestampTie_higherIdWins() {
        val dbName = testDbName("tie-break")

        helper.createDatabase(dbName, 4).apply {
            insertJobV4(
                db = this,
                id = "tie-a",
                companyName = "Tie Co A",
                jobUrl = TIE_URL,
                jobDescription = "tie candidate a",
                jobTitle = "Kotlin Engineer",
                status = "Interview",
                timestamp = 3000,
                lastModified = 3000
            )
            insertJobV4(
                db = this,
                id = "tie-b",
                companyName = "Tie Co B",
                jobUrl = TIE_URL,
                jobDescription = "tie candidate b",
                jobTitle = "Kotlin Engineer",
                status = "Interview",
                timestamp = 3000,
                lastModified = 3000
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            JobDatabase.MIGRATION_4_5
        )

        assertEquals(1L, longForQuery(migratedDb, "SELECT COUNT(*) FROM jobs WHERE jobUrl = '$TIE_URL'"))
        assertEquals("tie-b", singleIdForUrl(migratedDb, TIE_URL))
        migratedDb.query("SELECT companyName, jobDescription FROM jobs WHERE jobUrl = '$TIE_URL'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Tie Co B", cursor.getString(0))
            assertEquals("tie candidate b", cursor.getString(1))
        }
    }

    @Test
    fun migrate6To7_addsIsDeletedDefaultFalseAndPreservesRows() {
        val dbName = testDbName("v6-v7-isDeleted")

        helper.createDatabase(dbName, 6).apply {
            insertJobV6(
                db = this,
                id = "soft-delete-seed",
                companyName = "Seed Co",
                jobUrl = "https://www.linkedin.com/jobs/view/seed",
                jobDescription = "seed description",
                jobTitle = "Android Engineer",
                status = "Saved",
                timestamp = 1234,
                lastModified = 5678
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            JobDatabase.MIGRATION_6_7
        )

        migratedDb.query(
            "SELECT companyName, jobUrl, lastModified, isDeleted FROM jobs WHERE id = 'soft-delete-seed'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Seed Co", cursor.getString(0))
            assertEquals("https://www.linkedin.com/jobs/view/seed", cursor.getString(1))
            assertEquals(5678L, cursor.getLong(2))
            assertEquals(0, cursor.getInt(3))
        }
    }

    private fun seedVersion4Rows(db: SupportSQLiteDatabase) {
        insertJobV4(
            db = db,
            id = "dup-old",
            companyName = "Old Co",
            jobUrl = DUP_URL,
            jobDescription = "older duplicate",
            jobTitle = "Android Dev",
            status = "Saved",
            timestamp = 1000,
            lastModified = 1000
        )

        insertJobV4(
            db = db,
            id = "dup-new",
            companyName = "New Co",
            jobUrl = DUP_URL,
            jobDescription = "newer duplicate",
            jobTitle = "Android Dev",
            status = "Applied",
            timestamp = 900,
            lastModified = 2000
        )

        insertJobV4(
            db = db,
            id = "tie-a",
            companyName = "Tie Co A",
            jobUrl = TIE_URL,
            jobDescription = "tie candidate a",
            jobTitle = "Kotlin Engineer",
            status = "Interview",
            timestamp = 3000,
            lastModified = 3000
        )

        insertJobV4(
            db = db,
            id = "tie-b",
            companyName = "Tie Co B",
            jobUrl = TIE_URL,
            jobDescription = "tie candidate b",
            jobTitle = "Kotlin Engineer",
            status = "Interview",
            timestamp = 3000,
            lastModified = 3000
        )
    }

    private fun insertJobV4(
        db: SupportSQLiteDatabase,
        id: String,
        companyName: String,
        jobUrl: String,
        jobDescription: String,
        jobTitle: String,
        status: String,
        timestamp: Long,
        lastModified: Long
    ) {
        db.execSQL(
            """
            INSERT INTO jobs (
                id, companyName, jobUrl, jobDescription, jobTitle, status,
                timestamp, lastModified, matchScore, language, prepNotes,
                sourcePlatform, filterReason, createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'English', NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any>(id, companyName, jobUrl, jobDescription, jobTitle, status, timestamp, lastModified)
        )
    }

    private fun insertJobV6(
        db: SupportSQLiteDatabase,
        id: String,
        companyName: String,
        jobUrl: String,
        jobDescription: String,
        jobTitle: String,
        status: String,
        timestamp: Long,
        lastModified: Long
    ) {
        db.execSQL(
            """
            INSERT INTO jobs (
                id, companyName, jobUrl, jobDescription, jobTitle, status,
                timestamp, lastModified, matchScore, language, prepNotes,
                sourcePlatform, filterReason, createdAt, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'English', NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any>(id, companyName, jobUrl, jobDescription, jobTitle, status, timestamp, lastModified)
        )
    }

    private fun longForQuery(db: SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { cursor ->
            if (!cursor.moveToFirst()) return 0L
            return cursor.getLong(0)
        }
    }

    private fun singleIdForUrl(db: SupportSQLiteDatabase, url: String): String {
        db.query("SELECT id FROM jobs WHERE jobUrl = '$url'").use { cursor ->
            if (!cursor.moveToFirst()) return ""
            return cursor.getString(0)
        }
    }

    companion object {
        private const val TEST_DB_PREFIX = "job-database-migration-test"
        private const val DUP_URL = "https://www.linkedin.com/jobs/view/dup"
        private const val TIE_URL = "https://www.linkedin.com/jobs/view/tie"

        private fun testDbName(suffix: String): String = "$TEST_DB_PREFIX-$suffix"
    }
}


package com.thewalkersoft.linkedin_job_tracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            execSQL(
                """
                INSERT INTO jobs (
                    id, companyName, jobUrl, jobDescription, jobTitle, status,
                    timestamp, lastModified, matchScore, language, prepNotes,
                    sourcePlatform, filterReason, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 'English', NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
                arrayOf<Any>(
                    "soft-delete-seed",
                    "Seed Co",
                    "https://www.linkedin.com/jobs/view/seed",
                    "seed description",
                    "Android Engineer",
                    "Saved",
                    1234L,
                    5678L
                )
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

    @Test
    fun migrate8To9_normalizesLegacyStatusesToUppercaseTokens() {
        val dbName = testDbName("v8-v9-status-normalization")

        helper.createDatabase(dbName, 8).apply {
            insertJobV8(
                db = this,
                id = "legacy-saved",
                companyName = "Saved Co",
                jobUrl = "https://www.linkedin.com/jobs/view/legacy-saved",
                jobDescription = "saved description",
                status = "Saved",
                createdAt = 1000,
                updatedAt = 2000
            )
            insertJobV8(
                db = this,
                id = "legacy-rejected",
                companyName = "Rejected Co",
                jobUrl = "https://www.linkedin.com/jobs/view/legacy-rejected",
                jobDescription = "rejected description",
                status = "Resume-Rejected",
                createdAt = 3000,
                updatedAt = 4000
            )
            insertJobV8(
                db = this,
                id = "legacy-alias",
                companyName = "Alias Co",
                jobUrl = "https://www.linkedin.com/jobs/view/legacy-alias",
                jobDescription = "alias description",
                status = "REJECTED",
                createdAt = 5000,
                updatedAt = 6000
            )
            insertJobV8(
                db = this,
                id = "legacy-unknown",
                companyName = "Unknown Co",
                jobUrl = "https://www.linkedin.com/jobs/view/legacy-unknown",
                jobDescription = "unknown description",
                status = "SCRAPED",
                createdAt = 7000,
                updatedAt = 8000
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            9,
            true,
            JobDatabase.MIGRATION_8_9
        )

        migratedDb.query("SELECT id, status FROM jobs ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())

            assertEquals("legacy-alias", cursor.getString(0))
            assertEquals("RESUME_REJECTED", cursor.getString(1))

            assertTrue(cursor.moveToNext())
            assertEquals("legacy-rejected", cursor.getString(0))
            assertEquals("RESUME_REJECTED", cursor.getString(1))

            assertTrue(cursor.moveToNext())
            assertEquals("legacy-saved", cursor.getString(0))
            assertEquals("SAVED", cursor.getString(1))

            assertTrue(cursor.moveToNext())
            assertEquals("legacy-unknown", cursor.getString(0))
            assertEquals("SAVED", cursor.getString(1))
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

    private fun insertJobV8(
        db: SupportSQLiteDatabase,
        id: String,
        companyName: String,
        jobUrl: String,
        jobDescription: String,
        status: String,
        createdAt: Long,
        updatedAt: Long
    ) {
        db.execSQL(
            """
            INSERT INTO jobs (
                id, companyName, jobUrl, jobDescription, jobTitle, status,
                createdAt, updatedAt, isDeleted, matchScore, language,
                prepNotes, sourcePlatform, filterReason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'English', NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any>(
                id,
                companyName,
                jobUrl,
                jobDescription,
                "Android Engineer",
                status,
                createdAt,
                updatedAt,
                0
            )
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

    @Test
    fun migrate9To10_dropsPrepNotesAndFilterReasonColumns() {
        val dbName = testDbName("v9-v10-drop-columns")

        helper.createDatabase(dbName, 9).apply {
            insertJobV9(
                db = this,
                id = "drop-cols-seed",
                companyName = "DropCols Co",
                jobUrl = "https://www.linkedin.com/jobs/view/drop-cols",
                jobDescription = "drop columns description",
                status = "SAVED",
                createdAt = 1000,
                updatedAt = 2000
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            10,
            true,
            JobDatabase.MIGRATION_9_10
        )

        // Verify row data is preserved
        migratedDb.query(
            "SELECT id, companyName, jobUrl, jobDescription, status, createdAt, updatedAt, isDeleted, language FROM jobs WHERE id = 'drop-cols-seed'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("drop-cols-seed", cursor.getString(0))
            assertEquals("DropCols Co", cursor.getString(1))
            assertEquals("https://www.linkedin.com/jobs/view/drop-cols", cursor.getString(2))
            assertEquals("drop columns description", cursor.getString(3))
            assertEquals("SAVED", cursor.getString(4))
            assertEquals(1000L, cursor.getLong(5))
            assertEquals(2000L, cursor.getLong(6))
            assertEquals(0, cursor.getInt(7))
            assertEquals("English", cursor.getString(8))
        }

        // Verify prepNotes and filterReason columns no longer exist
        val columnNames = mutableListOf<String>()
        migratedDb.query("PRAGMA table_info(jobs)").use { cursor ->
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertTrue("Expected sourcePlatform column to exist", "sourcePlatform" in columnNames)
        assertFalse("prepNotes column should be dropped", "prepNotes" in columnNames)
        assertFalse("filterReason column should be dropped", "filterReason" in columnNames)
    }

    private fun insertJobV9(
        db: SupportSQLiteDatabase,
        id: String,
        companyName: String,
        jobUrl: String,
        jobDescription: String,
        status: String,
        createdAt: Long,
        updatedAt: Long
    ) {
        db.execSQL(
            """
            INSERT INTO jobs (
                id, companyName, jobUrl, jobDescription, jobTitle, status,
                createdAt, updatedAt, isDeleted, matchScore, language,
                prepNotes, sourcePlatform, filterReason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'English', NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any>(
                id,
                companyName,
                jobUrl,
                jobDescription,
                "Android Engineer",
                status,
                createdAt,
                updatedAt,
                0
            )
        )
    }

    companion object {
        private const val TEST_DB_PREFIX = "job-database-migration-test"
        private const val DUP_URL = "https://www.linkedin.com/jobs/view/dup"
        private const val TIE_URL = "https://www.linkedin.com/jobs/view/tie"

        private fun testDbName(suffix: String): String = "$TEST_DB_PREFIX-$suffix"
    }
}


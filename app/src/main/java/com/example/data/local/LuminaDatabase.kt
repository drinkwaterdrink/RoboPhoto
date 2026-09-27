package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoEntity::class,
        CleanupRuleEntity::class,
        AiAuditLogEntity::class,
        ScanCheckpointEntity::class,
        DuplicatePairExclusionEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun luminaDao(): LuminaDao

    companion object {
        @Volatile
        private var INSTANCE: LuminaDatabase? = null

        private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                if (nameIdx < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return false
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinitionSql: String
        ) {
            if (!hasColumn(db, tableName, columnName)) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinitionSql")
            }
        }

        /**
         * Explicit Migration 1 -> 2: Adds video metadata columns (`mediaType`, `durationMs`, `mimeType`).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "photos", "mediaType", "TEXT NOT NULL DEFAULT 'IMAGE'")
                addColumnIfMissing(db, "photos", "durationMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "mimeType", "TEXT NOT NULL DEFAULT 'image/jpeg'")
            }
        }

        /**
         * Explicit Migration 2 -> 3: Adds cluster override & exclusion columns and unique URI index.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "photos", "clusterTitleOverride", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "excludedClusterKeys", "TEXT NOT NULL DEFAULT ''")
                // Deduplicate any historical duplicate uriString rows keeping the lowest id before creating unique index
                db.execSQL(
                    """
                    DELETE FROM `photos`
                    WHERE `id` NOT IN (
                        SELECT MIN(`id`) FROM `photos` GROUP BY `uriString`
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photos_uriString` ON `photos` (`uriString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_duplicateClusterId` ON `photos` (`duplicateClusterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_triageStatus` ON `photos` (`triageStatus`)")
            }
        }

        /**
         * Explicit Migration 3 -> 4:
         * - Preserves all user triage states, Review Bin items, AI descriptions, rules, and cluster overrides.
         * - Adds genuine capture/import/revision metadata columns to `photos`.
         * - Adds token usage foundation columns to `ai_audit_logs`.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "photos", "clusterTitleOverride", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "excludedClusterKeys", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "capturedAtEpochMs", "INTEGER")
                addColumnIfMissing(db, "photos", "importedAtEpochMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "dateModifiedEpochMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "sourceGenerationModified", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "isUriPermissionPersisted", "INTEGER NOT NULL DEFAULT 0")

                // Backfill capture & modified timestamps from existing dateTakenEpochMs without inventing current time
                db.execSQL(
                    """
                    UPDATE `photos`
                    SET `capturedAtEpochMs` = CASE WHEN `dateTakenEpochMs` > 0 THEN `dateTakenEpochMs` ELSE NULL END,
                        `dateModifiedEpochMs` = CASE WHEN `dateTakenEpochMs` > 0 THEN `dateTakenEpochMs` ELSE 0 END,
                        `importedAtEpochMs` = CASE WHEN `dateTakenEpochMs` > 0 THEN `dateTakenEpochMs` ELSE 0 END
                    WHERE `capturedAtEpochMs` IS NULL AND `dateModifiedEpochMs` = 0
                    """.trimIndent()
                )

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_photos_uriString` ON `photos` (`uriString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_duplicateClusterId` ON `photos` (`duplicateClusterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_triageStatus` ON `photos` (`triageStatus`)")

                // Extend ai_audit_logs with token usage columns
                addColumnIfMissing(db, "ai_audit_logs", "promptTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "ai_audit_logs", "completionTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "ai_audit_logs", "totalTokens", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "ai_audit_logs", "tokenUsageSource", "TEXT NOT NULL DEFAULT 'ESTIMATED'")
                addColumnIfMissing(db, "ai_audit_logs", "requestSucceeded", "INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    """
                    UPDATE `ai_audit_logs`
                    SET `totalTokens` = CASE WHEN `servedFromEncryptedCache` = 1 THEN 0 ELSE `estimatedTokens` END,
                        `tokenUsageSource` = CASE WHEN `servedFromEncryptedCache` = 1 THEN 'LOCAL_CACHE_ZERO' ELSE 'ESTIMATED' END
                    WHERE `totalTokens` = 0
                    """.trimIndent()
                )
            }
        }

        /**
         * Explicit Migration 4 -> 5 (Pass 2):
         * - Adds multi-frame video signature, LSH band, aspect-ratio/temporal bucket, and manual Best-Shot columns to `photos`.
         * - Adds size/duration threshold columns to `cleanup_rules`.
         * - Creates `scan_checkpoints` and `duplicate_pair_exclusions` tables.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "photos", "videoSignatureHashes", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "aspectRatioBucket", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "temporalBucket", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "lshBand0", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "lshBand1", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "lshBand2", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "lshBand3", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "userSelectedBestShot", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "photos", "motionStabilityScore", "INTEGER NOT NULL DEFAULT 75")
                addColumnIfMissing(db, "photos", "analysisStageVersion", "INTEGER NOT NULL DEFAULT 2")
                addColumnIfMissing(db, "photos", "clusterType", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "clusterConfidence", "REAL NOT NULL DEFAULT 0.0")
                addColumnIfMissing(db, "photos", "clusterReason", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "photos", "clusterGenerationVersion", "INTEGER NOT NULL DEFAULT 2")

                addColumnIfMissing(db, "cleanup_rules", "minFileSizeBytes", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "cleanup_rules", "minDurationMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "cleanup_rules", "maxDurationMs", "INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scan_checkpoints` (
                        `id` TEXT NOT NULL,
                        `phase` TEXT NOT NULL,
                        `discoveredCount` INTEGER NOT NULL,
                        `analyzedCount` INTEGER NOT NULL,
                        `totalCount` INTEGER NOT NULL,
                        `changedCount` INTEGER NOT NULL,
                        `unchangedSkippedCount` INTEGER NOT NULL,
                        `errorCount` INTEGER NOT NULL,
                        `lastProcessedCursorOffset` INTEGER NOT NULL,
                        `lastProcessedUri` TEXT NOT NULL,
                        `startedAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isPaused` INTEGER NOT NULL,
                        `isCancelled` INTEGER NOT NULL,
                        `lastErrorSummary` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `duplicate_pair_exclusions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uriA` TEXT NOT NULL,
                        `uriB` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_duplicate_pair_exclusions_uriA_uriB` ON `duplicate_pair_exclusions` (`uriA`, `uriB`)"
                )
            }
        }

        /**
         * Explicit Migration 5 -> 6 (Pass 3):
         * - Adds `lastEvaluatedAtEpochMs` to `cleanup_rules`.
         * - Adds `providerType`, `requestType`, `latencyMs`, and `endpointUsed` to `ai_audit_logs`.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "cleanup_rules", "lastEvaluatedAtEpochMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "ai_audit_logs", "providerType", "TEXT NOT NULL DEFAULT 'CUSTOM_OPENAI'")
                addColumnIfMissing(db, "ai_audit_logs", "requestType", "TEXT NOT NULL DEFAULT 'PHOTO_INSPECTION'")
                addColumnIfMissing(db, "ai_audit_logs", "latencyMs", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "ai_audit_logs", "endpointUsed", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun getInstance(context: Context): LuminaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LuminaDatabase::class.java,
                    "lumina_clean_forensics.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

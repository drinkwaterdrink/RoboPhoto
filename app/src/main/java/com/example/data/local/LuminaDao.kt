package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminaDao {

    @Query("SELECT * FROM photos ORDER BY COALESCE(capturedAtEpochMs, dateTakenEpochMs, dateModifiedEpochMs, importedAtEpochMs) DESC, id DESC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY COALESCE(capturedAtEpochMs, dateTakenEpochMs, dateModifiedEpochMs, importedAtEpochMs) DESC, id DESC")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE uriString = :uriString LIMIT 1")
    suspend fun getPhotoByUri(uriString: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id = :photoId AND uriString = :expectedUri LIMIT 1")
    suspend fun getVerifiedPhoto(photoId: Long, expectedUri: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhotosIgnoreConflicts(photos: List<PhotoEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity): Int

    @Update
    suspend fun updatePhotos(photos: List<PhotoEntity>): Int

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id = :photoId AND uriString = :expectedUri")
    suspend fun updateTriageStatusVerified(
        photoId: Long,
        expectedUri: String,
        status: String,
        vaultedAt: Long?
    ): Int

    @Query("DELETE FROM photos WHERE id = :photoId AND uriString = :expectedUri")
    suspend fun permanentlyDeleteVerifiedPhoto(photoId: Long, expectedUri: String): Int

    @Query("DELETE FROM photos WHERE uriString LIKE '%lumina_sample_media%' OR title LIKE 'sample_%'")
    suspend fun deleteLegacySamplePhotos()

    // Cleanup Rules (Automations)
    @Query("SELECT * FROM cleanup_rules ORDER BY createdAtEpochMs DESC")
    fun observeCleanupRules(): Flow<List<CleanupRuleEntity>>

    @Query("SELECT * FROM cleanup_rules ORDER BY createdAtEpochMs DESC")
    suspend fun getCleanupRulesOnce(): List<CleanupRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCleanupRule(rule: CleanupRuleEntity): Long

    @Update
    suspend fun updateCleanupRule(rule: CleanupRuleEntity)

    @Query("DELETE FROM cleanup_rules WHERE id = :ruleId")
    suspend fun deleteCleanupRule(ruleId: Long)

    // AI Audit Logs & Token Usage Foundation
    @Query("SELECT * FROM ai_audit_logs ORDER BY timestampEpochMs DESC LIMIT 500")
    fun observeAiAuditLogs(): Flow<List<AiAuditLogEntity>>

    @Query("SELECT * FROM ai_audit_logs WHERE timestampEpochMs >= :startEpochMs AND timestampEpochMs <= :endEpochMs ORDER BY timestampEpochMs DESC")
    suspend fun getAiAuditLogsInRangeOnce(startEpochMs: Long, endEpochMs: Long): List<AiAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAuditLog(log: AiAuditLogEntity)

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM ai_audit_logs WHERE servedFromEncryptedCache = 0 AND requestSucceeded = 1")
    fun observeTotalProviderTokens(): Flow<Long>

    // Scan Checkpoints (Pass 2 Incremental Pipeline)
    @Query("SELECT * FROM scan_checkpoints WHERE id = :checkpointId LIMIT 1")
    fun observeScanCheckpoint(checkpointId: String = "primary_library_scan"): Flow<ScanCheckpointEntity?>

    @Query("SELECT * FROM scan_checkpoints WHERE id = :checkpointId LIMIT 1")
    suspend fun getScanCheckpointOnce(checkpointId: String = "primary_library_scan"): ScanCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScanCheckpoint(checkpoint: ScanCheckpointEntity)

    // Duplicate Pair Exclusions (Pass 2 Pairwise Not-Duplicate Persistence)
    @Query("SELECT * FROM duplicate_pair_exclusions")
    suspend fun getAllPairExclusionsOnce(): List<DuplicatePairExclusionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPairExclusions(exclusions: List<DuplicatePairExclusionEntity>)
}

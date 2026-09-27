package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminaDao {

    @Query("SELECT * FROM photos ORDER BY dateTakenEpochMs DESC, id DESC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY dateTakenEpochMs DESC, id DESC")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE uriString = :uriString LIMIT 1")
    suspend fun getPhotoByUri(uriString: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhotosIgnoreConflicts(photos: List<PhotoEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Update
    suspend fun updatePhotos(photos: List<PhotoEntity>)

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id = :photoId")
    suspend fun updateTriageStatus(photoId: Long, status: String, vaultedAt: Long?)

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id = :photoId AND uriString = :expectedUri")
    suspend fun updateTriageStatusVerified(photoId: Long, expectedUri: String, status: String, vaultedAt: Long?): Int

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id IN (:photoIds)")
    suspend fun updateBatchTriageStatus(photoIds: List<Long>, status: String, vaultedAt: Long?)

    @Query("DELETE FROM photos WHERE id IN (:photoIds)")
    suspend fun permanentlyDeletePhotosByIds(photoIds: List<Long>)

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

    // AI Audit Logs
    @Query("SELECT * FROM ai_audit_logs ORDER BY timestampEpochMs DESC LIMIT 50")
    fun observeAiAuditLogs(): Flow<List<AiAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAuditLog(log: AiAuditLogEntity)

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM ai_audit_logs")
    fun observeTotalApiSpendUsd(): Flow<Double>
}

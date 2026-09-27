package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LuminaDao {

    @Query("SELECT * FROM photos ORDER BY dateTakenEpochMs DESC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY dateTakenEpochMs DESC")
    suspend fun getAllPhotosOnce(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Update
    suspend fun updatePhotos(photos: List<PhotoEntity>)

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id = :photoId")
    suspend fun updateTriageStatus(photoId: Long, status: String, vaultedAt: Long?)

    @Query("UPDATE photos SET triageStatus = :status, vaultedAtEpochMs = :vaultedAt WHERE id IN (:photoIds)")
    suspend fun updateBatchTriageStatus(photoIds: List<Long>, status: String, vaultedAt: Long?)

    @Query("DELETE FROM photos WHERE id IN (:photoIds)")
    suspend fun permanentlyDeletePhotosByIds(photoIds: List<Long>)

    @Query("DELETE FROM photos WHERE uriString LIKE '%lumina_sample_media%'")
    suspend fun deleteLegacySamplePhotos()

    @Query("DELETE FROM photos WHERE triageStatus = 'TRASH_VAULT'")
    suspend fun emptyTrashVault()

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getPhotoCount(): Int

    // Cleanup Rules
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
    @Query("SELECT * FROM ai_audit_logs ORDER BY timestampEpochMs DESC LIMIT 60")
    fun observeAiAuditLogs(): Flow<List<AiAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiAuditLog(log: AiAuditLogEntity)

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM ai_audit_logs WHERE servedFromEncryptedCache = 0")
    fun observeTotalApiSpendUsd(): Flow<Double>

    @Query("DELETE FROM ai_audit_logs")
    suspend fun clearAiAuditLogs()
}

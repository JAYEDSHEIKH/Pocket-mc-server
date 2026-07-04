package com.pocketcraft.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM server_profiles ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<ServerProfile>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    fun getServerById(id: String): Flow<ServerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ServerProfile)

    @Update
    suspend fun update(profile: ServerProfile)

    @Delete
    suspend fun delete(profile: ServerProfile)

    @Query("UPDATE server_profiles SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ServerStatus)

    @Query("""
        UPDATE server_profiles
        SET name = :name, ramMb = :ramMb,
            customStartCommand = :customStartCommand,
            serverNotes = :notes
        WHERE id = :id
    """)
    suspend fun updateSettings(
        id: String,
        name: String,
        ramMb: Int,
        customStartCommand: String?,
        notes: String
    )

    @Query("UPDATE server_profiles SET eulaAccepted = 1 WHERE id = :id")
    suspend fun markEulaAccepted(id: String)

    /** Resets any server that was left stuck in a running/transitioning state. */
    @Query("UPDATE server_profiles SET status = 'STOPPED' WHERE status IN ('RUNNING','STARTING','STOPPING')")
    suspend fun resetStaleRunningStatuses()
}

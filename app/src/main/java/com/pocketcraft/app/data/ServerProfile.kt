package com.pocketcraft.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class LoaderType { PAPER, FABRIC }

enum class ServerStatus {
    STOPPED,
    DOWNLOADING,
    STARTING,
    RUNNING,
    STOPPING,
    CRASHED
}

@Entity(tableName = "server_profiles")
data class ServerProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val loaderType: LoaderType = LoaderType.PAPER,
    val minecraftVersion: String,
    /** Heap allocation in megabytes (e.g. 1024, 2048) */
    val ramMb: Int,
    val status: ServerStatus = ServerStatus.STOPPED,
    val eulaAccepted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

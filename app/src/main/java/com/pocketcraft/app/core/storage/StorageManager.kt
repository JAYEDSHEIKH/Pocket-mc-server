package com.pocketcraft.app.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authority for all persistent file paths in PocketCraft.
 *
 * Server data lives in external app-specific storage when available
 * (visible in the device's Files app under Android/data/com.pocketcraft.app/files/servers/).
 * Falls back to internal storage if external is not mounted.
 *
 * The JRE stays in internal storage — it is an executable cache, not user data.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Root directory for all PocketCraft user data (server worlds, configs, plugins…) */
    val rootDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    /** Where the bundled JDK is extracted — always internal, not user-visible */
    val jreDir: File
        get() = File(context.filesDir, "jre")

    /** Directory for a specific server's files. Created on demand by the caller. */
    fun serverDir(serverId: String): File = File(rootDir, "servers/$serverId")

    /**
     * Resolves the actual directory for an existing server, handling migration from
     * the old internal-storage path used before this class was introduced.
     */
    fun resolveServerDir(serverId: String): File {
        // Check legacy path first so existing data is never orphaned
        val legacy = File(context.filesDir, "servers/$serverId")
        if (legacy.exists()) return legacy
        return serverDir(serverId)
    }
}

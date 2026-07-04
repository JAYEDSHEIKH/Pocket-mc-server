package com.pocketcraft.app.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authority for all persistent file paths in PocketCraft.
 *
 * Server data lives in app-specific external storage when available, under a
 * "PocketCraft" sub-folder.  This path is visible in the device's Files app
 * under:  Android/data/com.pocketcraft.app/files/PocketCraft/servers/
 *
 * Falls back to internal storage if external storage is not mounted.
 * The JRE stays in internal storage — it is an executable cache, not user data.
 *
 * Callers should always resolve a server directory via [resolveServerDir] so
 * that legacy paths (created before this class existed) are honoured.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Root for all PocketCraft user data.
     * Maps to:  /sdcard/Android/data/<packageId>/files/PocketCraft/
     */
    val rootDir: File
        get() {
            val base = context.getExternalFilesDir("PocketCraft") ?: context.filesDir
            return base.also { it.mkdirs() }
        }

    /** Where the bundled JDK is extracted — always internal, not user-visible. */
    val jreDir: File
        get() = File(context.filesDir, "jre").also { it.mkdirs() }

    /** Canonical directory for a specific server's files. */
    fun serverDir(serverId: String): File =
        File(rootDir, "servers/$serverId")

    /**
     * Resolves the actual directory for an existing server, handling migration from
     * legacy internal-storage paths used before this class was introduced.
     *
     * Priority:
     *  1. Legacy internal path  (filesDir/servers/{id})  — if it exists, use it
     *  2. External app-specific path (rootDir/servers/{id})
     */
    fun resolveServerDir(serverId: String): File {
        val legacy = File(context.filesDir, "servers/$serverId")
        if (legacy.exists()) return legacy
        return serverDir(serverId)
    }

    /**
     * Creates all sub-directories needed for a new server and returns the
     * server root.  Call once during server creation.
     */
    fun createServerDirs(serverId: String): File {
        val dir = serverDir(serverId)
        dir.mkdirs()
        File(dir, "plugins").mkdirs()
        File(dir, "world").mkdirs()
        File(dir, "logs").mkdirs()
        return dir
    }

    /** Human-readable display path shown to the user in Settings. */
    fun displayRootPath(): String = rootDir.absolutePath
}

package com.pocketcraft.app.core.jre

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts the bundled aarch64 OpenJDK 21 tarball from assets into app-private
 * internal storage on first run.  Subsequent runs are a fast file-existence check.
 *
 * Asset path: assets/jre/aarch64-jdk21.tar.gz
 * Destination: filesDir/jre/   (app-private, no root needed)
 * Java binary: filesDir/jre/<top-dir>/bin/java
 */
@Singleton
class JreInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "JreInstaller"
        private const val ASSET_PATH = "jre/aarch64-jdk21.tar.gz"
        private const val JRE_DIR_NAME = "jre"
        private const val MARKER_FILE = ".extracted"
    }

    private val jreRoot: File get() = File(context.filesDir, JRE_DIR_NAME)

    /** Returns the File for the java binary, or null if extraction fails. */
    suspend fun getJavaBinary(): File? = withContext(Dispatchers.IO) {
        // Fast path: already extracted
        if (isAlreadyExtracted()) {
            return@withContext findJavaBinary()
        }

        Log.i(TAG, "JRE not yet extracted — starting extraction...")
        jreRoot.mkdirs()

        runCatching {
            extractTarball()
            File(jreRoot, MARKER_FILE).createNewFile()
            Log.i(TAG, "JRE extraction complete.")
        }.onFailure { e ->
            Log.e(TAG, "JRE extraction failed", e)
            jreRoot.deleteRecursively()
            return@withContext null
        }

        findJavaBinary()
    }

    private fun isAlreadyExtracted(): Boolean =
        File(jreRoot, MARKER_FILE).exists() && findJavaBinary()?.exists() == true

    /**
     * Finds the java binary by walking one level into the jre root to find the
     * top-level directory created by the tarball (e.g. zulu21.38.21-ca-jre21.0.5-linux_aarch64/).
     */
    private fun findJavaBinary(): File? {
        // Direct path if already known
        val direct = File(jreRoot, "bin/java")
        if (direct.exists()) return direct

        // Walk one level of sub-directories (the tarball top-level folder)
        val topDirs = jreRoot.listFiles { f -> f.isDirectory } ?: return null
        for (dir in topDirs) {
            val candidate = File(dir, "bin/java")
            if (candidate.exists()) {
                candidate.setExecutable(true)
                return candidate
            }
        }
        return null
    }

    /** Extract using the system `tar` command available on all modern Android (API 26+). */
    private fun extractTarball() {
        // Copy asset to a temp file first (tar can't read from a stream directly)
        val tmpTar = File(context.cacheDir, "jdk21.tar.gz")
        context.assets.open(ASSET_PATH).use { input ->
            tmpTar.outputStream().use { output -> input.copyTo(output) }
        }

        val process = ProcessBuilder(
            "tar", "-xzf", tmpTar.absolutePath, "-C", jreRoot.absolutePath
        ).redirectErrorStream(true).start()

        val exitCode = process.waitFor()
        tmpTar.delete()

        if (exitCode != 0) {
            val stderr = process.inputStream.bufferedReader().readText()
            throw RuntimeException("tar extraction failed (exit $exitCode): $stderr")
        }

        // Ensure all extracted binaries are executable
        setExecutableBit(jreRoot)
    }

    private fun setExecutableBit(dir: File) {
        dir.walkTopDown()
            .filter { it.isFile && (it.name == "java" || it.parentFile?.name == "bin") }
            .forEach { it.setExecutable(true, false) }
    }
}

package com.pocketcraft.app.core.jre

import android.content.Context
import android.util.Log
import com.pocketcraft.app.core.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts the bundled aarch64 OpenJDK 21 tarball from assets into app-private
 * internal storage on first run.  Subsequent runs are a fast file-existence check.
 *
 * AAPT2 note: Android's asset packager strips the .gz wrapper from .tar.gz files,
 * storing them as plain .tar inside the APK.  We probe both paths at runtime so
 * this works regardless of AAPT2 version.
 *
 * Asset path:  assets/jre/aarch64-jdk21.tar.gz  (source)
 *              assets/jre/aarch64-jdk21.tar      (AAPT2-packaged form)
 * Destination: StorageManager.jreDir  (internal storage, app-private)
 */
@Singleton
class JreInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        private const val TAG = "JreInstaller"
        // AAPT2 strips the .gz wrapper when packaging assets, storing the file
        // as "aarch64-jdk21.tar" (plain tarball).  We try both paths so the code
        // works regardless of AAPT2 version or future toolchain changes.
        private const val ASSET_PATH_GZ  = "jre/aarch64-jdk21.tar.gz"
        private const val ASSET_PATH_TAR = "jre/aarch64-jdk21.tar"
        private const val MARKER_FILE = ".extracted"
    }

    // Prevents concurrent extraction races when multiple coroutines call getJavaBinary().
    private val extractionMutex = Mutex()

    private val jreRoot: File get() = storageManager.jreDir

    /**
     * Checks whether the JRE has already been extracted without triggering extraction.
     * Returns the binary file if present, null otherwise.  Used by Settings to show JRE status.
     */
    fun findExistingBinary(): File? = if (isAlreadyExtracted()) findJavaBinary() else null

    /** Returns the File for the java binary, or null if extraction fails. */
    suspend fun getJavaBinary(): File? = withContext(Dispatchers.IO) {
        // Fast path — no lock needed for a read-only existence check
        if (isAlreadyExtracted()) {
            val bin = findJavaBinary()
            Log.i(TAG, "JRE already extracted. Binary: ${bin?.absolutePath}")
            return@withContext bin
        }

        // Serialise extraction — only one coroutine should extract at a time
        extractionMutex.withLock {
            // Re-check inside the lock in case another coroutine finished while we waited
            if (isAlreadyExtracted()) {
                return@withLock findJavaBinary()
            }

            Log.i(TAG, "JRE not yet extracted — starting extraction to ${jreRoot.absolutePath}...")
            jreRoot.mkdirs()

            runCatching {
                extractTarball()
                File(jreRoot, MARKER_FILE).createNewFile()
                Log.i(TAG, "JRE extraction complete.")
            }.onFailure { e ->
                Log.e(TAG, "JRE extraction failed: ${e.message}", e)
                jreRoot.deleteRecursively()
                return@withLock null
            }

            val bin = findJavaBinary()
            Log.i(TAG, "JRE binary resolved to: ${bin?.absolutePath}")
            bin
        }
    }

    private fun isAlreadyExtracted(): Boolean =
        File(jreRoot, MARKER_FILE).exists() && findJavaBinary()?.exists() == true

    /**
     * Finds the java binary by walking one level into the jre root to locate the
     * top-level directory created by the tarball (e.g. temurin-21-linux_aarch64/).
     */
    private fun findJavaBinary(): File? {
        val direct = File(jreRoot, "bin/java")
        if (direct.exists()) return direct.also { it.setExecutable(true) }

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

    /** Extract using the system `tar` command (available on all Android API 26+). */
    private fun extractTarball() {
        // AAPT2 strips the .gz wrapper when packaging assets — the file ends up as
        // a plain .tar inside the APK. Try .tar.gz first (future-proof), then .tar.
        val (assetPath, isGzip) = probeAssetPath()

        Log.i(TAG, "Using asset '$assetPath' (gzip=$isGzip)")

        val tmpExt = if (isGzip) "tar.gz" else "tar"
        val tmpTar = File.createTempFile("jdk21-", ".$tmpExt", context.cacheDir)
        try {
            context.assets.open(assetPath).use { input ->
                tmpTar.outputStream().use { output -> input.copyTo(output) }
            }

            // Use -xzf for gzip-compressed tarballs, -xf for plain tarballs
            val tarFlags = if (isGzip) "-xzf" else "-xf"
            val process = ProcessBuilder("tar", tarFlags, tmpTar.absolutePath, "-C", jreRoot.absolutePath)
                .redirectErrorStream(true)
                .start()

            // Drain stdout/stderr BEFORE waitFor() to avoid pipe-buffer deadlock
            val output = StringBuilder()
            val drain = Thread {
                try { output.append(process.inputStream.bufferedReader().readText()) }
                catch (_: Exception) {}
            }.also { it.isDaemon = true; it.start() }

            val exitCode = process.waitFor()
            drain.join(5_000)

            if (exitCode != 0) {
                throw RuntimeException("tar extraction failed (exit $exitCode):\n$output")
            }
        } finally {
            // Always clean up the temp file, even if extraction threw
            tmpTar.delete()
        }

        setExecutableBit(jreRoot)
    }

    /**
     * Probes asset paths to find the JRE tarball.
     * Returns the asset path and whether it is gzip-compressed.
     * Throws RuntimeException if neither path exists (APK built without JRE).
     */
    private fun probeAssetPath(): Pair<String, Boolean> {
        // Try the original .tar.gz name first (in case a future AAPT2 preserves it)
        try {
            context.assets.open(ASSET_PATH_GZ).close()
            return Pair(ASSET_PATH_GZ, true)
        } catch (_: java.io.FileNotFoundException) { /* expected on most builds */ }

        // Fall back to .tar — the name AAPT2 uses after stripping the .gz wrapper
        try {
            context.assets.open(ASSET_PATH_TAR).close()
            return Pair(ASSET_PATH_TAR, false)
        } catch (e: java.io.FileNotFoundException) {
            throw RuntimeException(
                "JRE asset not found in APK. Looked for '$ASSET_PATH_GZ' and " +
                "'$ASSET_PATH_TAR'. This APK was built without the JRE bundled in — " +
                "run install-all.sh locally or wait for a GitHub Actions CI build.", e
            )
        }
    }

    private fun setExecutableBit(dir: File) {
        dir.walkTopDown()
            .filter { it.isFile && (it.name == "java" || it.parentFile?.name == "bin") }
            .forEach { it.setExecutable(true, false) }
    }
}

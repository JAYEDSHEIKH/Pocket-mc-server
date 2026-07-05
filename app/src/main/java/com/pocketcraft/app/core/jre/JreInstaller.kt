package com.pocketcraft.app.core.jre

import android.content.Context
import android.util.Log
import com.pocketcraft.app.core.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Downloads and extracts OpenJDK 21 (aarch64) from the Adoptium API on first run.
 * Subsequent runs are a fast file-existence check.
 *
 * This approach avoids bundling a 50 MB tarball in the APK (which AAPT2 decompresses
 * and renames, breaking AssetManager.open). The JRE is downloaded once, stored in
 * app-private internal storage, and reused across all server starts.
 *
 * Destination: StorageManager.jreDir
 */
@Singleton
class JreInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val storageManager: StorageManager
) {

    sealed class State {
        object Idle : State()
        data class Querying(val message: String = "Querying Adoptium API…") : State()
        data class Downloading(
            val progress: Float,       // 0f..1f
            val downloadedMb: Float,
            val totalMb: Float
        ) : State()
        object Extracting : State()
        data class Ready(val binary: File) : State()
        data class Failed(val error: String) : State()
    }

    companion object {
        private const val TAG = "JreInstaller"
        private const val MARKER_FILE = ".extracted"
        private const val ADOPTIUM_API =
            "https://api.adoptium.net/v3/assets/feature_releases/21/ga" +
            "?architecture=aarch64&heap_size=normal&image_type=jre" +
            "&jvm_impl=hotspot&os=linux&page=0&page_size=1&project=jdk&vendor=eclipse"
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Serialises concurrent calls — only one download/extraction runs at a time.
    private val mutex = Mutex()

    private val jreRoot: File get() = storageManager.jreDir

    /**
     * Non-suspend check used by Settings to show JRE status without triggering a download.
     */
    fun findExistingBinary(): File? = if (isAlreadyExtracted()) findJavaBinary() else null

    /**
     * Returns the java binary File.  Downloads and extracts the JRE if not already present.
     * Returns null if the download or extraction fails.
     */
    suspend fun getJavaBinary(): File? = withContext(Dispatchers.IO) {
        // Fast path — already extracted
        if (isAlreadyExtracted()) {
            val bin = findJavaBinary()
            if (bin != null) _state.value = State.Ready(bin)
            Log.i(TAG, "JRE already present: ${bin?.absolutePath}")
            return@withContext bin
        }

        mutex.withLock {
            // Re-check inside the lock
            if (isAlreadyExtracted()) {
                return@withLock findJavaBinary()
            }

            jreRoot.mkdirs()

            try {
                // Step 1 — resolve download URL
                _state.value = State.Querying()
                val url = resolveDownloadUrl()
                Log.i(TAG, "Resolved JRE URL: $url")

                // Step 2 — download tarball
                val tmpTar = File.createTempFile("jdk21-", ".tar.gz", context.cacheDir)
                try {
                    downloadToFile(url, tmpTar)

                    // Step 3 — extract
                    _state.value = State.Extracting
                    extractTarball(tmpTar)
                } finally {
                    tmpTar.delete()
                }

                // Step 4 — write marker and return binary
                File(jreRoot, MARKER_FILE).createNewFile()
                val bin = findJavaBinary()
                if (bin != null) {
                    _state.value = State.Ready(bin)
                    Log.i(TAG, "JRE ready: ${bin.absolutePath}")
                } else {
                    val msg = "JRE extracted but java binary not found in $jreRoot"
                    _state.value = State.Failed(msg)
                    Log.e(TAG, msg)
                }
                bin

            } catch (e: Exception) {
                Log.e(TAG, "JRE setup failed: ${e.message}", e)
                jreRoot.deleteRecursively()
                val msg = e.message ?: "Unknown error"
                _state.value = State.Failed(msg)
                null
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isAlreadyExtracted(): Boolean =
        File(jreRoot, MARKER_FILE).exists() && findJavaBinary()?.exists() == true

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

    /** Queries Adoptium API and returns the .tar.gz download URL. */
    private fun resolveDownloadUrl(): String {
        val request = Request.Builder().url(ADOPTIUM_API).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IOException("Adoptium API returned ${response.code}")
            val body = response.body?.string()
                ?: throw IOException("Empty response from Adoptium API")

            // Extract the first .tar.gz link from the JSON
            val match = Regex(""""link"\s*:\s*"([^"]+\.tar\.gz)"""").find(body)
            return match?.groupValues?.get(1)
                ?: throw IOException(
                    "Could not find a .tar.gz download link in Adoptium response.\n" +
                    "Response snippet: ${body.take(300)}"
                )
        }
    }

    /** Downloads [url] to [dest], emitting progress to [_state]. */
    private fun downloadToFile(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IOException("JRE download failed: HTTP ${response.code} for $url")

            val body = response.body
                ?: throw IOException("Empty body when downloading JRE from $url")

            val totalBytes = body.contentLength()   // -1 if unknown
            var downloadedBytes = 0L

            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(32_768)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloadedBytes += read
                        _state.value = State.Downloading(
                            progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f,
                            downloadedMb = downloadedBytes / 1_000_000f,
                            totalMb = if (totalBytes > 0) totalBytes / 1_000_000f else 0f
                        )
                    }
                }
            }
            Log.i(TAG, "Download complete: ${downloadedBytes / 1_000_000} MB")
        }
    }

    /** Extracts [tarGz] into [jreRoot] using the device's system `tar`. */
    private fun extractTarball(tarGz: File) {
        val process = ProcessBuilder("tar", "-xzf", tarGz.absolutePath, "-C", jreRoot.absolutePath)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        val drain = Thread {
            try { output.append(process.inputStream.bufferedReader().readText()) }
            catch (_: Exception) {}
        }.also { it.isDaemon = true; it.start() }

        val exitCode = process.waitFor()
        drain.join(5_000)

        if (exitCode != 0)
            throw RuntimeException("tar extraction failed (exit $exitCode):\n$output")

        // Ensure all binaries in bin/ are executable
        jreRoot.walkTopDown()
            .filter { it.isFile && (it.name == "java" || it.parentFile?.name == "bin") }
            .forEach { it.setExecutable(true, false) }
    }
}

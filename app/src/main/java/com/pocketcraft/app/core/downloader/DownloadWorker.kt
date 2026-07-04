package com.pocketcraft.app.core.downloader

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pocketcraft.app.data.ServerProfileDao
import com.pocketcraft.app.data.ServerStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * WorkManager worker that downloads a server jar in the background.
 *
 * Input data keys:
 *   KEY_SERVER_ID    — UUID string of the server profile
 *   KEY_DOWNLOAD_URL — full HTTPS URL to the jar
 *   KEY_DEST_PATH    — absolute path where the jar should be saved
 *
 * Output / progress:
 *   PROGRESS_BYTES_DOWNLOADED — long
 *   PROGRESS_BYTES_TOTAL      — long (-1 if unknown)
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val serverProfileDao: ServerProfileDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"

        const val KEY_SERVER_ID = "server_id"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_DEST_PATH = "dest_path"

        const val PROGRESS_BYTES_DOWNLOADED = "bytes_downloaded"
        const val PROGRESS_BYTES_TOTAL = "bytes_total"

        fun buildInputData(serverId: String, downloadUrl: String, destPath: String): Data =
            workDataOf(
                KEY_SERVER_ID to serverId,
                KEY_DOWNLOAD_URL to downloadUrl,
                KEY_DEST_PATH to destPath
            )
    }

    override suspend fun doWork(): Result {
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return Result.failure()
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val destPath = inputData.getString(KEY_DEST_PATH) ?: return Result.failure()

        Log.i(TAG, "[$serverId] Downloading $downloadUrl → $destPath")
        serverProfileDao.updateStatus(serverId, ServerStatus.DOWNLOADING)

        return runCatching {
            val destFile = File(destPath)
            destFile.parentFile?.mkdirs()

            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $downloadUrl")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()

            var bytesDownloaded = 0L
            val tmpFile = File("$destPath.tmp")

            body.byteStream().use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        setProgressAsync(
                            workDataOf(
                                PROGRESS_BYTES_DOWNLOADED to bytesDownloaded,
                                PROGRESS_BYTES_TOTAL to totalBytes
                            )
                        )
                    }
                }
            }

            // Atomic rename so we never leave a partial jar
            tmpFile.renameTo(destFile)
            Log.i(TAG, "[$serverId] Download complete (${bytesDownloaded / 1024 / 1024} MB)")
            serverProfileDao.updateStatus(serverId, ServerStatus.STOPPED)

            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "[$serverId] Download failed", e)
            serverProfileDao.updateStatus(serverId, ServerStatus.CRASHED)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

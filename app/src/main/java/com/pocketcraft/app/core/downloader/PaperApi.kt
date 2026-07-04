package com.pocketcraft.app.core.downloader

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

// ── Response DTOs ─────────────────────────────────────────────────────────────

/** GET /v3/projects/paper */
@JsonClass(generateAdapter = true)
data class PaperProjectResponse(
    @Json(name = "projectId") val projectId: String,
    @Json(name = "versions") val versions: List<String>
)

/** One build entry from GET /v3/projects/paper/versions/{version}/builds */
@JsonClass(generateAdapter = true)
data class PaperBuild(
    @Json(name = "id") val id: Int,
    @Json(name = "projectId") val projectId: String,
    @Json(name = "version") val version: String,
    @Json(name = "channel") val channel: String,      // "default" | "experimental"
    @Json(name = "promoted") val promoted: Boolean,
    @Json(name = "downloads") val downloads: PaperDownloads
)

@JsonClass(generateAdapter = true)
data class PaperDownloads(
    @Json(name = "application") val application: PaperDownloadFile
)

@JsonClass(generateAdapter = true)
data class PaperDownloadFile(
    @Json(name = "name") val name: String,
    @Json(name = "sha256") val sha256: String
)

// ── API interface ─────────────────────────────────────────────────────────────

interface PaperApi {

    /** Returns all Paper versions (latest first in the list). */
    @GET("v3/projects/paper")
    suspend fun getProject(): PaperProjectResponse

    /** Returns all builds for a given MC version, newest last. */
    @GET("v3/projects/paper/versions/{mcVersion}/builds")
    suspend fun getBuilds(@Path("mcVersion") mcVersion: String): List<PaperBuild>
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Constructs the download URL for a specific build's application jar.
 * Pattern: https://fill.papermc.io/v3/projects/paper/versions/{version}/builds/{build}/downloads/application
 */
fun PaperBuild.applicationDownloadUrl(): String =
    "https://fill.papermc.io/v3/projects/paper/versions/$version/builds/$id/downloads/application"

/** Returns the latest stable (non-experimental) build, or the newest build overall. */
fun List<PaperBuild>.latestStable(): PaperBuild? =
    lastOrNull { it.channel == "default" } ?: lastOrNull()

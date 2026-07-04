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

/**
 * Wrapper for GET /v3/projects/paper/versions/{version}/builds
 * The v3 API returns { "builds": [...] }, not a bare list.
 */
@JsonClass(generateAdapter = true)
data class PaperBuildsResponse(
    @Json(name = "builds") val builds: List<PaperBuild>
)

/** One build entry inside the builds response. */
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

    /** Returns all Paper versions (newest last in the list — sort descending in ViewModel). */
    @GET("v3/projects/paper")
    suspend fun getProject(): PaperProjectResponse

    /** Returns all builds for a given MC version wrapped in a builds object. */
    @GET("v3/projects/paper/versions/{mcVersion}/builds")
    suspend fun getBuilds(@Path("mcVersion") mcVersion: String): PaperBuildsResponse
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

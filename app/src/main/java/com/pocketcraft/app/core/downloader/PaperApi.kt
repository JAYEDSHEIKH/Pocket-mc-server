package com.pocketcraft.app.core.downloader

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

// ── Response DTOs — matched to the Fill v3 API (fill.papermc.io) ─────────────

/**
 * GET /v3/projects/paper
 *
 * Actual response shape:
 * {
 *   "project": { "id": "paper", "name": "Paper" },
 *   "versions": {
 *     "1.21": ["1.21.11", "1.21.11-rc3", ...],
 *     "1.20": ["1.20.6", "1.20.5", ...]
 *   }
 * }
 * "versions" is a map of major-version-group → list of specific versions.
 */
@JsonClass(generateAdapter = true)
data class PaperProjectResponse(
    @Json(name = "project") val project: PaperProject,
    @Json(name = "versions") val versions: Map<String, List<String>>
)

@JsonClass(generateAdapter = true)
data class PaperProject(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String
)

/**
 * GET /v3/projects/paper/versions/{version}/builds
 *
 * Returns a **bare array** of builds — no wrapper object.
 * [
 *   {
 *     "id": 232,
 *     "time": "2025-06-09T10:18:55.778Z",
 *     "channel": "STABLE",
 *     "downloads": {
 *       "server:default": {
 *         "name": "paper-1.21.4-232.jar",
 *         "checksums": { "sha256": "5ee4f5..." },
 *         "size": 51437498,
 *         "url": "https://fill-data.papermc.io/v1/objects/.../paper-1.21.4-232.jar"
 *       }
 *     }
 *   }
 * ]
 */
@JsonClass(generateAdapter = true)
data class PaperBuild(
    @Json(name = "id") val id: Int,
    @Json(name = "time") val time: String,
    @Json(name = "channel") val channel: String,          // "STABLE" or "EXPERIMENTAL"
    @Json(name = "downloads") val downloads: Map<String, PaperDownloadFile>
)

@JsonClass(generateAdapter = true)
data class PaperDownloadFile(
    @Json(name = "name") val name: String,
    @Json(name = "checksums") val checksums: PaperChecksums,
    @Json(name = "size") val size: Long,
    @Json(name = "url") val url: String                   // direct download URL
)

@JsonClass(generateAdapter = true)
data class PaperChecksums(
    @Json(name = "sha256") val sha256: String
)

// ── API interface ─────────────────────────────────────────────────────────────

interface PaperApi {

    /** Returns project metadata including all version groups. */
    @GET("v3/projects/paper")
    suspend fun getProject(): PaperProjectResponse

    /**
     * Returns all builds for a given MC version as a bare list.
     * Builds are ordered oldest→newest by id.
     */
    @GET("v3/projects/paper/versions/{mcVersion}/builds")
    suspend fun getBuilds(@Path("mcVersion") mcVersion: String): List<PaperBuild>
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Flattens the version map into a sorted list of stable release versions
 * (newest first). Filters out release candidates (-rc) and pre-releases (-pre).
 */
fun PaperProjectResponse.stableVersionsSorted(): List<String> {
    val allVersions = versions.values.flatten()
    val stable = allVersions.filter { v ->
        !v.contains("-rc", ignoreCase = true) &&
        !v.contains("-pre", ignoreCase = true) &&
        !v.contains("-beta", ignoreCase = true) &&
        !v.contains("-alpha", ignoreCase = true)
    }
    return stable.sortedWith(versionComparatorDesc)
}

/**
 * Comparator that sorts Minecraft version strings newest-first using numeric
 * segment comparison so that "1.21.4" > "1.9.4" (not alphabetically).
 */
private val versionComparatorDesc = Comparator<String> { a, b ->
    val aSegs = a.split(".").mapNotNull { it.toIntOrNull() }
    val bSegs = b.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(aSegs.size, bSegs.size)) {
        val diff = (bSegs.getOrElse(i) { 0 }) - (aSegs.getOrElse(i) { 0 })
        if (diff != 0) return@Comparator diff
    }
    0
}

/** Returns the direct download URL for this build's server jar. */
fun PaperBuild.serverJarUrl(): String =
    downloads["server:default"]?.url
        ?: throw IllegalStateException("No server:default download in build $id")

/**
 * Returns the stable build with the highest build ID (channel == "STABLE",
 * case-insensitive), or the build with the highest ID overall if none are stable.
 *
 * Uses maxByOrNull { id } rather than lastOrNull so the result is correct
 * regardless of whether the API returns builds newest-first or oldest-first.
 * (Fill v3 currently returns newest-first, i.e. descending IDs.)
 */
fun List<PaperBuild>.latestStable(): PaperBuild? =
    filter { it.channel.equals("STABLE", ignoreCase = true) }
        .maxByOrNull { it.id }
        ?: maxByOrNull { it.id }

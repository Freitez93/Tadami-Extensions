package aniyomi.lib.vidhideextractor

import aniyomi.lib.playlistutils.PlaylistUtils
import aniyomi.lib.unpacker.autoUnpacker
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.UrlUtils
import keiyoushi.utils.parallelCatchingFlatMap
import keiyoushi.utils.useAsJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

class VidHideExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    suspend fun videosFromUrl(
        url: String,
        prefix: String = "VidHide - "
    ) = videosFromUrl(url) { "$prefix$it" }

    suspend fun videosFromUrl(
        url: String,
        videoNameGen: (String) -> String = { quality -> "VidHide - $quality" }
    ): List<Video> {
        val script = fetchAndExtractScript(url) ?: return emptyList()
        val playlists = extractVideoUrl(script, url)
        val subtitleList = extractSubtitles(script, url)

        return playlists.parallelCatchingFlatMap { videoUrl ->
            playlistUtils.extractFromHls(
                videoUrl,
                referer = url,
                videoNameGen = videoNameGen,
                subtitleList = subtitleList,
            )
        }
    }

    private suspend fun fetchAndExtractScript(url: String): String? = client.newCall(GET(url, headers)).awaitSuccess()
        .useAsJsoup()
        .select("script")
        .find { it.html().contains("eval(function(p,a,c,k,e,d)") }
        ?.html()
        ?.let(::autoUnpacker)

    private fun extractVideoUrl(script: String, baseUrl: String): List<String> {
        val matches = sourceOrder.findAll(script).associate {
            it.groupValues[1] to it.groupValues[2]
        }

        // Orden de prioridad: hls2, hls3, hls4
        val priorityOrder = listOf("hls2", "hls3", "hls4")
        for (key in priorityOrder) {
            val url = matches[key]
            if (url != null) {
                return if (url.startsWith("/")) {
                    listOfNotNull(UrlUtils.fixUrl(url, baseUrl))
                } else {
                    listOf(url)
                }
            }
        }

        // Si no se encuentra ninguna busca en general los m3u8.
        return sourceRegex.findAll(script).mapNotNull {
            UrlUtils.fixUrl(it.groupValues[1], baseUrl)
        }.toList()
    }

    private fun extractSubtitles(script: String, baseUrl: String): List<Track> = try {
        val subtitleStr = script
            .substringAfter("tracks")
            .substringAfter("[")
            .substringBefore("]")
        json.decodeFromString<List<TrackDto>>("[$subtitleStr]")
            .filter { it.kind.equals("captions", true) }
            .mapNotNull {
                UrlUtils.fixUrl(it.file, baseUrl)?.let { url ->
                    Track(url, it.label ?: "")
                }
            }
    } catch (_: SerializationException) {
        emptyList()
    }

    @Serializable
    private data class TrackDto(
        val file: String,
        val kind: String,
        val label: String? = null,
    )

    companion object {
        // Capture both `https://domain/master.m3u8?query` and `/domain/master.m3u8?query`
        private val sourceRegex = Regex(""""((?:https?:/)?/[^"]*m3u8[^"]*)"""")

        // Priority order: hls4, hls3, hls2
        private val sourceOrder = Regex(""""(hls4|hls3|hls2)"\s*:\s*"([^"]+)"""")
    }
}

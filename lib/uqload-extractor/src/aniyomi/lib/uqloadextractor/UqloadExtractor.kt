package aniyomi.lib.uqloadextractor

import aniyomi.lib.playlistutils.PlaylistUtils
import aniyomi.lib.unpacker.autoUnpacker
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class UqloadExtractor(private val client: OkHttpClient) {
    private val playlistUtils by lazy { PlaylistUtils(client, Headers.EMPTY) }
    companion object {
        const val BASE_URL = "https://uqload.is/"
        private val hostRegex by lazy { Regex("""https?://(?:www\.)?[^/]+/""") }
        private val VIDEO_REGEX by lazy { Regex("""["'](https?://[^"'\s]+\.(?:m3u8|mp4|mpd)[^"'\s]*)["']""") }
    }

    suspend fun videosFromUrl(
        url: String,
        prefix: String = "Uqload - ",
    ) = videosFromUrl(url) { "$prefix$it" }

    suspend fun videosFromUrl(
        url: String,
        videoNameGen: (String) -> String = { quality -> "Uqload - $quality" },
    ): List<Video> {
        val fixedUrl = if (url.startsWith(BASE_URL, true)) url else url.replace(hostRegex, BASE_URL)
        val response = client.newCall(GET(fixedUrl)).awaitSuccess()
        val document = response.useAsJsoup()
        val script = document.selectFirst("script:containsData(sources:)")?.data()
            ?: document.select("script").find { it.html().contains("eval(function(p,a,c,k,e,d)") }
                ?.html()
                ?.let(::autoUnpacker)
            ?: return emptyList()

        val videoUrl = VIDEO_REGEX.find(script)?.groupValues[1] ?: return emptyList()
        val videoHeaders = Headers.headersOf("Referer", BASE_URL)
        val quality = videoNameGen("MP4")
        return when {
            "m3u8" in videoUrl -> {
                playlistUtils.extractFromHls(videoUrl, BASE_URL, videoNameGen = videoNameGen)
            }

            "mp4" in videoUrl -> {
                listOf(Video(videoUrl = videoUrl, videoTitle = quality, headers = videoHeaders))
            }

            else -> emptyList()
        }
    }
}

package aniyomi.lib.okruextractor

import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class OkruExtractor(private val client: OkHttpClient, private val headers: Headers = Headers.EMPTY) {
    private val playlistUtils by lazy { PlaylistUtils(client) }

    private fun fixQuality(quality: String): String {
        val qualities = listOf(
            Pair("ultra", "2160p"),
            Pair("quad", "1440p"),
            Pair("full", "1080p"),
            Pair("hd", "720p"),
            Pair("sd", "480p"),
            Pair("low", "360p"),
            Pair("lowest", "240p"),
            Pair("mobile", "144p"),
        )
        return qualities.find { it.first == quality }?.second ?: quality
    }

    suspend fun videosFromUrl(
        url: String,
        prefix: String = "Okru - ",
        fixQualities: Boolean = true,
    ) = videosFromUrl(url, { "$prefix$it" }, fixQualities)

    suspend fun videosFromUrl(
        url: String,
        videoNameGen: (String) -> String = { quality -> "Okru - $quality" },
        fixQualities: Boolean = true,
    ): List<Video> {
        val document = client.newCall(GET(url, headers)).awaitSuccess().useAsJsoup()
        val videoString = document.selectFirst("div[data-options]")
            ?.attr("data-options")
            ?: return emptyList<Video>()

        return when {
            "ondemandHls" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandHls")
                playlistUtils.extractFromHls(playlistUrl, videoNameGen = videoNameGen)
            }

            "ondemandDash" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandDash")
                playlistUtils.extractFromDash(playlistUrl, videoNameGen = videoNameGen)
            }

            else -> videosFromJson(videoString, videoNameGen, fixQualities)
        }
    }

    private fun String.addPrefix(prefix: String) = prefix.takeIf(String::isNotBlank)
        ?.let { "$prefix $this" }
        ?: this

    private fun String.extractLink(attr: String) = substringAfter("$attr\\\":\\\"")
        .substringBefore("\\\"")
        .replace("\\\\u0026", "&")

    private fun videosFromJson(
        videoString: String,
        videoNameGen: (String) -> String = { quality -> "Okru - $quality" },
        fixQualities: Boolean = true,
    ): List<Video> {
        val arrayData = videoString.substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")

        return arrayData.split("{\\\"name\\\":\\\"").reversed().mapNotNull { data ->
            val videoUrl = data.extractLink("url")
            val quality = data.substringBefore("\\\"").let {
                if (fixQualities) fixQuality(it) else it
            }
            val videoQuality = videoNameGen(quality)

            if (videoUrl.startsWith("https://")) {
                Video(videoTitle = videoQuality, videoUrl = videoUrl)
            } else {
                null
            }
        }
    }
}

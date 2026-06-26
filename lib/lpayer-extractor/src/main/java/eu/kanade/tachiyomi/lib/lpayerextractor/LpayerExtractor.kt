package eu.kanade.tachiyomi.lib.lpayerextractor

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LpayerExtractor(private val client: OkHttpClient) {
    private val json: Json by injectLazy()
    private val playlistUtils by lazy { PlaylistUtils(client) }

    private val aesKey = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
    private val ivList = listOf("1234567890oiuytr", "0123456789abcdef")

    fun videosFromUrl(videoUrl: String, prefix: String = "LPayer - "): List<Video> {
        return runCatching {
            Log.d("LpayerExtractor", "Extracting videos from $videoUrl")

            val url = videoUrl.toHttpUrl()
            val baseUrl = "${url.scheme}://${url.host}"

            // Extract video ID from hash (fragment)
            val videoId = videoUrl.substringAfterLast("#").substringAfter("/")
            if (videoId.isEmpty() || videoId == videoUrl) return emptyList()

            // Build API URL and Headers
            val apiUrl = "$baseUrl/api/v1/video?id=$videoId"
            val headers = Headers.Builder()
                .add("Referer", videoUrl)
                .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            // Get encrypted data
            val encryptedData = client.newCall(GET(apiUrl, headers)).execute().use {
                it.body.string().trim()
            }
            Log.d("LpayerExtractor", "Encrypted data: $encryptedData")

            // Decrypt data
            val decryptedData = ivList.firstNotNullOfOrNull { iv ->
                decrypt(encryptedData, aesKey, iv)
            } ?: return emptyList()
            Log.d("LpayerExtractor", "DecryptedData data: $decryptedData")

            // Parse JSON
            val videoData = json.decodeFromString<LpayerVideoData>(decryptedData)

            // Get HLS URL
            var hlsUrl = videoData.hls
                ?: videoData.source
                ?: videoData.url
                ?: videoData.file
                ?: return emptyList()

            // Handle relative URLs
            if (hlsUrl.startsWith("/")) {
                hlsUrl = "$baseUrl$hlsUrl"
            }

            val subtitleList = videoData.subtitle?.mapNotNull { (lang, path) ->
                val subUrl = path.substringBefore("#").replace("\\/", "/")
                if (subUrl.isBlank()) return@mapNotNull null
                val absoluteSubUrl = if (subUrl.startsWith("/")) "$baseUrl$subUrl" else subUrl
                Track(absoluteSubUrl, lang)
            } ?: emptyList()

            // Parse M3U8 playlist for additional qualities
            if (".m3u8" in hlsUrl) {
                playlistUtils.extractFromHls(
                    playlistUrl = hlsUrl.replace("\\/", "/"),
                    referer = videoUrl,
                    videoNameGen = { "$prefix$it" },
                    subtitleList = subtitleList,
                )
            } else {
                listOf(
                    Video(
                        videoUrl = hlsUrl.replace("\\/", "/"),
                        videoTitle = "${prefix}Default",
                        headers = headers,
                        subtitleTracks = subtitleList,
                    ),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun decrypt(encryptedHex: String, key: ByteArray, iv: String): String? {
        return runCatching {
            // Remove all whitespace and non-hex characters
            val cleanedHex = encryptedHex.replace(Regex("[^0-9A-Fa-f]"), "")

            if (cleanedHex.isEmpty() || cleanedHex.length % 2 != 0) {
                Log.d("LpayerExtractor", "Invalid hex string length: ${cleanedHex.length}")
                return null
            }

            val encryptedBytes = cleanedHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val ivParameterSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        }.getOrNull()
    }
}

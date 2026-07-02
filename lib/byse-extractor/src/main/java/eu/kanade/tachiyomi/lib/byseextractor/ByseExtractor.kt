package eu.kanade.tachiyomi.lib.byseextractor

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.bodyString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ByseExtractor(private val client: OkHttpClient) {
    private val playlistUtils by lazy { PlaylistUtils(client) }
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun videosFromUrl(url: String, prefix: String) = videosFromUrl(url) { "${prefix}Byse - $it" }
    fun videosFromUrl(
        url: String,
        videoNameGen: (String) -> String = { quality -> "Byse - $quality" },
    ): List<Video> {
        val refererUrl = getBaseUrl(url)
        val playbackRoot = getPlayback(url) ?: return emptyList()
        val streamUrl = decryptPlayback(playbackRoot.playback) ?: return emptyList()

        val headers = Headers.Builder()
            .add("Referer", refererUrl)
            .build()

        return playlistUtils.extractFromHls(
            streamUrl,
            referer = url,
            videoHeaders = headers,
            masterHeaders = headers,
            videoNameGen = videoNameGen,
        )
    }

    private fun getBaseUrl(url: String): String = url.toHttpUrl().let { "${it.scheme}://${it.host}" }

    private fun getCodeFromUrl(url: String): String {
        val path = url.toHttpUrl().encodedPath
        return path.trimEnd('/').substringAfterLast('/')
    }

    private fun getPlayback(mainUrl: String): PlaybackRoot? {
        val details = getDetails(mainUrl) ?: return null
        val embedFrameUrl = details.embedFrameUrl
        val embedBase = getBaseUrl(embedFrameUrl)
        val code = getCodeFromUrl(embedFrameUrl)
        val playbackUrl = "$embedBase/api/videos/$code/embed/playback"
        val headers = Headers.Builder()
            .add("accept", "*/*")
            .add("accept-language", "en-US,en;q=0.5")
            .add("priority", "u=1, i")
            .add("referer", embedFrameUrl)
            .add("x-embed-parent", mainUrl)
            .build()

        return try {
            val response = client.newCall(GET(playbackUrl, headers)).execute().bodyString()
            json.decodeFromString<PlaybackRoot>(response)
        } catch (e: Exception) {
            Log.d("ByseExtractor", "Error al obtener la URL del servidor desde la API: ${e.message}")
            null
        }
    }

    private fun getDetails(mainUrl: String): DetailsRoot? {
        val base = getBaseUrl(mainUrl)
        val code = getCodeFromUrl(mainUrl)
        val url = "$base/api/videos/$code/embed/details"
        return try {
            val response = client.newCall(GET(url)).execute().bodyString()
            json.decodeFromString<DetailsRoot>(response)
        } catch (e: Exception) {
            Log.d("ByseExtractor", "Error al obtener la URL del servidor desde la API: ${e.message}")
            null
        }
    }

    private fun decryptPlayback(playback: Playback): String? = try {
        val keyBytes = buildAesKey(playback)
        val ivBytes = b64UrlDecode(playback.iv)
        val cipherBytes = b64UrlDecode(playback.payload)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        var jsonStr = String(plainBytes, Charsets.UTF_8)
        if (jsonStr.startsWith("\uFEFF")) jsonStr = jsonStr.substring(1)

        val root = json.decodeFromString<PlaybackDecrypt>(jsonStr)
        root.sources.firstOrNull()?.url
    } catch (e: Exception) {
        Log.d("ByseExtractor", "Error al obtener la URL del servidor desde la API: ${e.message}")
        null
    }

    private fun buildAesKey(playback: Playback): ByteArray {
        val p1 = b64UrlDecode(playback.keyParts[0])
        val p2 = b64UrlDecode(playback.keyParts[1])
        return p1 + p2
    }

    private fun b64UrlDecode(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE)

    @Serializable
    data class DetailsRoot(
        val id: Long,
        val code: String,
        val title: String,
        @SerialName("poster_url")
        val posterUrl: String,
        val description: String,
        @SerialName("created_at")
        val createdAt: String,
        @SerialName("owner_private")
        val ownerPrivate: Boolean,
        @SerialName("embed_frame_url")
        val embedFrameUrl: String,
    )

    @Serializable
    data class PlaybackRoot(
        val playback: Playback,
    )

    @Serializable
    data class Playback(
        val algorithm: String,
        val iv: String,
        val payload: String,
        @SerialName("key_parts")
        val keyParts: List<String>,
        @SerialName("expires_at")
        val expiresAt: String,
        @SerialName("decrypt_keys")
        val decryptKeys: DecryptKeys,
        val iv2: String,
        val payload2: String,
    )

    @Serializable
    data class DecryptKeys(
        @SerialName("edge_1")
        val edge1: String,
        @SerialName("edge_2")
        val edge2: String,
        @SerialName("legacy_fallback")
        val legacyFallback: String,
    )

    @Serializable
    data class PlaybackDecrypt(
        val sources: List<PlaybackDecryptSource>,
    )

    @Serializable
    data class PlaybackDecryptSource(
        val quality: String,
        val label: String,
        @SerialName("mime_type")
        val mimeType: String,
        val url: String,
        @SerialName("bitrate_kbps")
        val bitrateKbps: Long,
        val height: JsonElement? = null,
    )
}

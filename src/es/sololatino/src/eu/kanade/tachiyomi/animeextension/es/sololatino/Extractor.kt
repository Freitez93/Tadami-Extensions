package eu.kanade.tachiyomi.animeextension.es.sololatino

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Embed69(private val client: OkHttpClient) {
    suspend fun getLinks(url: String): Map<String, List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val res = client.newCall(GET(url)).execute().asJsoup()
                val pageHtml = res.html()

                // Extraer dataLink
                val dataLinkJson = res.selectFirst("script:containsData(dataLink)")?.data()
                    ?.substringAfter("dataLink =")
                    ?.substringBefore(";")
                    ?.trim()
                    ?: run {
                        Log.e("Embed69", "No se encontró dataLink en la página: $url")
                        return@withContext emptyMap()
                    }

                // Extraer POW_CHALLENGE y POW_SALT
                val challenge = getFirstMatch("""POW_CHALLENGE\s*=\s*['"]([^'"]+)['"]""", pageHtml)
                val powSalt = getFirstMatch("""POW_SALT\s*=\s*['"]([^'"]+)['"]""", pageHtml)
                if (challenge == null || powSalt == null) {
                    Log.e("Embed69", "No se pudo extraer POW_CHALLENGE o POW_SALT de: $url")
                    return@withContext emptyMap()
                }

                // Resolver PoW
                val aesKey = solveEmbed69PoW(challenge, powSalt)
                if (aesKey == null) {
                    Log.e("Embed69", "PoW falló para: $url")
                    return@withContext emptyMap()
                }

                // Parsear JSON de dataLink
                val serversByLang = parseServersByLang(dataLinkJson)
                if (serversByLang.isNullOrEmpty()) {
                    Log.e("Embed69", "JSON de dataLink no se pudo parsear o está vacío")
                    return@withContext emptyMap()
                }

                // Descifrar enlaces y agrupar por idioma
                val allLinksByLanguage = mutableMapOf<String, MutableList<String>>()
                serversByLang.forEach { lang ->
                    lang.sortedEmbeds.forEach { server ->
                        server.link?.let { encrypted ->
                            val decryptedUrl = decryptAESLocal(encrypted, aesKey)
                            if (!decryptedUrl.isNullOrBlank()) {
                                val fixedUrl = fixHostsLinks(decryptedUrl)
                                Log.d("Embed69", "$decryptedUrl -> $fixedUrl")
                                val language = lang.videoLanguage ?: "Unknown"
                                allLinksByLanguage.getOrPut(language) {
                                    mutableListOf()
                                }.add(fixedUrl)
                            }
                        }
                    }
                }
                allLinksByLanguage
            } catch (e: Exception) {
                Log.e("Embed69", "Error al procesar Embed69: ${e.message}")
                emptyMap()
            }
        }
    }

    // Resolver PoW (Proof of Work)
    private fun solveEmbed69PoW(challenge: String, salt: String): ByteArray? {
        val md = MessageDigest.getInstance("SHA-256")
        var nonce = 0L
        val maxAttempts = 500000L
        while (nonce < maxAttempts) {
            val input = "$challenge$nonce".toByteArray(Charsets.UTF_8)
            val hash = md.digest(input).joinToString("") { "%02x".format(it) }
            if (hash.startsWith("000")) {
                Log.d("Embed69", "PoW encontrado nonce=$nonce hash=${hash.take(8)}")
                return md.digest("$challenge$nonce$salt".toByteArray(Charsets.UTF_8))
            }
            nonce++
            if (nonce % 10000L == 0L) Thread.sleep(1)
        }
        return null
    }

    // Descifrar AES localmente
    private fun decryptAESLocal(encryptedBase64: String, aesKey: ByteArray): String? {
        return try {
            val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (raw.size < 17) {
                Log.e("Embed69", "AES decrypt: raw data too short (${raw.size})")
                return null
            }
            val iv = raw.copyOfRange(0, 16)
            val ciphertext = raw.copyOfRange(16, raw.size)
            if (ciphertext.isEmpty()) {
                Log.e("Embed69", "AES decrypt: ciphertext vacío")
                return null
            }

            val keySpec = SecretKeySpec(aesKey.copyOfRange(0, 32), "AES")
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w("Embed69", "PKCS5Padding falló: ${e.message}, probando NoPadding")
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
                val decrypted = cipher.doFinal(ciphertext)
                val padByte = decrypted.last().toInt() and 0xFF
                val padLen = if (padByte in 1..16 && decrypted.size >= padByte) padByte else 0
                val clean = if (padLen > 0) decrypted.copyOfRange(0, decrypted.size - padLen) else decrypted
                String(clean, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e("Embed69", "AES decrypt error: ${e.message}")
            null
        }
    }

    // Parsear JSON de dataLink
    private fun parseServersByLang(json: String): List<ServersByLang>? = try {
        val jsonArray = org.json.JSONArray(json)
        val result = mutableListOf<ServersByLang>()
        for (i in 0 until jsonArray.length()) {
            val fileObject = jsonArray.getJSONObject(i)
            val language = fileObject.optString("video_language")
            val embeds = fileObject.optJSONArray("sortedEmbeds") ?: continue
            val sortedEmbeds = mutableListOf<ServersByLang.Server>()
            for (j in 0 until embeds.length()) {
                val embedObj = embeds.getJSONObject(j)
                sortedEmbeds.add(
                    ServersByLang.Server(
                        servername = embedObj.optString("servername"),
                        link = embedObj.optString("link"),
                    ),
                )
            }
            result.add(ServersByLang(fileId = fileObject.optString("file_id"), videoLanguage = language, sortedEmbeds = sortedEmbeds))
        }
        result
    } catch (e: Exception) {
        Log.e("Embed69", "Error al parsear JSON: ${e.message}")
        null
    }

    // Data classes
    private data class ServersByLang(
        val fileId: String? = null,
        val videoLanguage: String? = null,
        val sortedEmbeds: List<Server> = emptyList(),
    ) {
        data class Server(
            val servername: String? = null,
            val link: String? = null,
        )
    }
}

class XupaLace(private val client: OkHttpClient) {
    fun getLinks(url: String): Map<String, List<String>> {
        return try {
            val document = client.newCall(GET(url)).execute().asJsoup()
            val mapUrl = mapOf(
                ".OD_LAT > li" to "LAT",
                ".OD_ES > li" to "ESP",
                ".OD_EN > li" to "SUB",
                "li[data-lang='0']" to "LAT",
                "li[data-lang='1']" to "ESP",
                "li[data-lang='2']" to "SUB",
            )

            val allLinksByLanguage = mutableMapOf<String, MutableList<String>>()
            mapUrl.forEach { (selector, language) ->
                val langLinks = document.select(selector)
                    .mapNotNull { element ->
                        runCatching {
                            val onclick = element.attr("onclick").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            extractUrlFromOnclick(onclick)
                        }.onFailure { error ->
                            Log.e("XupaLace", "Error al procesar onclick: ${error.message}", error)
                        }.getOrNull()
                    }

                if (langLinks.isNotEmpty()) {
                    langLinks.forEach { link ->
                        allLinksByLanguage.getOrPut(language) { mutableListOf() }
                            .add(fixHostsLinks(link))
                    }
                }
            }
            allLinksByLanguage
        } catch (e: Exception) {
            Log.e("XupaLace", "Error al procesar XupaLace: ${e.message}")
            emptyMap()
        }
    }

    // Extraer URL de onclick
    private fun extractUrlFromOnclick(onclick: String): String? {
        // Patrón 1: URL entre comillas
        getFirstMatch("""['"](https?:\/\/[^'"]+)['"]""", onclick)?.let { return it }
        // Patrón 2: .php?link=BASE64&servidor=...
        getFirstMatch("""\.php\?link=([^&]+)&servidor=""", onclick)?.let { encoded ->
            return try {
                String(Base64.decode(encoded, Base64.DEFAULT))
            } catch (e: IllegalArgumentException) {
                Log.w("XupaLace", "Base64 inválido: $encoded", e)
                null
            }
        }
        return null
    }

    // Función para obtener el primer match de un regex
    private fun getFirstMatch(pattern: String, input: String): String? = Regex(pattern).find(input)?.groupValues?.get(1)
}

// ================================ Funciones Auxiliares ================================
fun getFirstMatch(pattern: String, input: String): String? = pattern.toRegex().find(input)?.groupValues?.get(1)

private fun fixHostsLinks(url: String): String {
    val replacements = mapOf(
        "mivalyo.com" to "vidhidepro.com",
        "dinisglows.com" to "vidhidepro.com",
        "dhtpre.com" to "vidhidepro.com",
        "minochinos.com" to "vidhidepro.com",
        // "filemoon.link" to "filemoon.sx",
        // "bysedikamoum.com" to "filemoon.sx",
        "sblona.com" to "watchsb.com",
        "lulu.st" to "lulustream.com",
        "uqload.io" to "uqload.com",
        "do7go.com" to "dood.la",
    )
    var result = url
    for ((from, to) in replacements) {
        if (result.contains(from)) {
            result = result.replaceFirst(from, to)
            break
        }
    }
    return result
}

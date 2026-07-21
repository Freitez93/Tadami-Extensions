package eu.kanade.tachiyomi.animeextension.es.sololatino

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Embed69(private val client: OkHttpClient) {
    suspend fun getLinks(url: String): List<ServersByLang> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(GET(url)).execute().asJsoup()
            val pageHtml = response.html()

            // Extraer dataLink
            val dataLinkJson = response.selectFirst("script:containsData(dataLink)")?.data()
                ?.substringBetween("dataLink =", ";")
                ?: run {
                    throw Error("No se encontró dataLink en la página: $url")
                }

            // Extraer POW_CHALLENGE y POW_SALT
            val challenge = getFirstMatch("""POW_CHALLENGE\s*=\s*['"]([^'"]+)['"]""", pageHtml)
            val powSalt = getFirstMatch("""POW_SALT\s*=\s*['"]([^'"]+)['"]""", pageHtml)
            if (challenge == null || powSalt == null) {
                throw Error("No se pudo extraer POW_CHALLENGE o POW_SALT de: $url")
            }

            // Resolver PoW
            val aesKey = solveEmbed69PoW(challenge, powSalt)
                ?: throw Error("PoW falló para: $url")

            // Parsear JSON de dataLink
            val serversByLang = dataLinkJson.parseAs<List<ServersByLang>>()
            if (serversByLang.isEmpty()) {
                throw Error("JSON de dataLink no se pudo parsear o está vacío")
            }

            serversByLang.forEach { lang ->
                lang.sortedEmbeds.forEach { server ->
                    server.link = server.link?.let { encrypted ->
                        decryptAESLocal(encrypted, aesKey) ?: encrypted
                    }
                }
                lang.downloadEmbeds.forEach { server ->
                    server.link = server.link?.let { encrypted ->
                        decryptAESLocal(encrypted, aesKey) ?: encrypted
                    }
                }
            }
            serversByLang
        } catch (e: Exception) {
            Log.e("Embed69", "Error: ${e.message}")
            emptyList()
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
    private fun decryptAESLocal(encryptedBase64: String, aesKey: ByteArray): String? = try {
        val raw = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (raw.size < 17) {
            throw Error("raw data too short (${raw.size})")
        }
        val iv = raw.copyOfRange(0, 16)
        val ciphertext = raw.copyOfRange(16, raw.size)
        if (ciphertext.isEmpty()) {
            throw Error("ciphertext vacío")
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

    // Data classes
    @Serializable
    data class ServersByLang(
        @SerialName("file_id")
        val fileId: Long? = null,
        @SerialName("video_language")
        val videoLanguage: String? = null,
        val sortedEmbeds: List<Server> = emptyList(),
        val downloadEmbeds: List<Server> = emptyList(),
    )

    @Serializable
    data class Server(
        val servername: String? = null,
        var link: String? = null,
        val type: String? = null,
    )
}

// ================================ Funciones Auxiliares ================================
// Función para obtener el primer match de un regex
fun getFirstMatch(pattern: String, input: String): String? = pattern.toRegex().find(input)?.groupValues?.get(1)

// Funcion para obtener un texto entre dos delimitadores.
fun String.substringBetween(after: String, before: String): String {
    val startIndex = this.indexOf(after) + after.length
    val endIndex = this.indexOf(before, startIndex)
    return if (startIndex != -1 && endIndex != -1) {
        this.substring(startIndex, endIndex)
    } else {
        ""
    }
}

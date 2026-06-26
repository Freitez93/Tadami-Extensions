package eu.kanade.tachiyomi.animeextension.es.animeav1

import eu.kanade.tachiyomi.animesource.model.SAnime
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// Función para obtener la información de la película/Anime/OVA
fun getMediaInfo(scriptContent: String): MediaDateJSON? {
    // Buscar el JSON dentro del script
    val dataJsonString = Regex("""data:\s*(\[.*])""", RegexOption.DOT_MATCHES_ALL)
        .find(scriptContent)?.groupValues?.get(1) ?: return null
    return try {
        val cleanedJson = cleanJsToJson(dataJsonString)
        val dataArray = JSONArray(cleanedJson)
        var mediaData: MediaDateJSON? = null
        // Usamos un mapa mutable para acumular embeds y downloads sin sobrescribir
        val allEmbeds = mutableMapOf<String, List<EmbedInfo>>()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val dataObj = item.optJSONObject("data") ?: continue
            // 1. Extraer información de media (si existe y no se ha extraído ya)
            if (mediaData == null) {
                dataObj.optJSONObject("media")?.let { mediaObj ->
                    mediaData = parseMediaObject(mediaObj)
                }
            }
            // 2. Extraer urls de 'embeds' y 'downloads' dinámicamente
            listOf("embeds", "downloads").forEach { key ->
                extractUrls(dataObj, key)?.forEach { (category, items) ->
                    val currentList = allEmbeds[category] ?: emptyList()
                    // Filtrar URLs duplicadas usando distinctBy
                    allEmbeds[category] = (currentList + items).distinctBy { it.url }
                }
            }
        }
        // Construir el objeto final combinando info + embeds
        mediaData?.copy(
            embeds = allEmbeds.takeIf { it.isNotEmpty() }
        ) ?: allEmbeds.takeIf { it.isNotEmpty() }?.let {
            MediaDateJSON(embeds = it)
        }
    } catch (e: Exception) {
        Log.e("AnimeAV1", "Error al procesar JSON en getMediaInfo: ${e.message}", e)
        null
    }
}

// Parsea el objeto 'media' del JSON a nuestra data class MediaDateJSON.
private fun parseMediaObject(mediaObj: JSONObject): MediaDateJSON {
    // Extraer géneros, manejando nulos
    val genres = mediaObj.optJSONArray("genres")?.let { arr ->
        List(arr.length()) { i ->
            arr.optJSONObject(i)?.optString("name")
        }.filterNotNull()
    } ?: emptyList()
    // Determinar episodio inicial (default 1)
    val startEpisode = mediaObj.optJSONArray("episodes")
        ?.optJSONObject(0)?.optInt("number", 1) ?: 1
    // Convertir estado numérico a texto legible
    val statusText = when (mediaObj.optInt("status", 0)) {
        0 -> SAnime.COMPLETED
        1, 2 -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }
    return MediaDateJSON(
        mediaID = mediaObj.optInt("id"),
        title = mediaObj.optString("title"),
        slug = mediaObj.optString("slug"),
        startDate = mediaObj.optString("startDate"),
        status = statusText,
        type = mediaObj.optJSONObject("category")?.optString("name"),
        genre = genres,
        synopsis = mediaObj.optString("synopsis"),
        score = mediaObj.optInt("score"),
        malId = mediaObj.optInt("malId"),
        episodesCount = mediaObj.optInt("episodesCount"),
        startEpisode = startEpisode,
        recommendations = parseRecommendations(mediaObj.optJSONArray("relations"))
    )
}

// Función auxiliar para extraer y modelar las recomendaciones (relations).
private fun parseRecommendations(relationsArray: JSONArray?): List<MediaRelation>? {
    if (relationsArray == null) return null
    return List(relationsArray.length()) { i ->
        val item = relationsArray.optJSONObject(i)
        val dest = item?.optJSONObject("destination")
        if (dest != null) {
            MediaRelation(
                type = item.optInt("type", 0),
                title = dest.optString("title"),
                href = "/media/${dest.optString("slug")}",
                posterUrl = "https://cdn.animeav1.com/covers/${dest.optInt("id")}.jpg"
            )
        } else null
    }.filterNotNull()
}

// Función auxiliar para extraer URLs (embeds, downloads).
private fun extractUrls(dataObj: JSONObject, key: String): Map<String, List<EmbedInfo>>? {
    val containerObj = dataObj.optJSONObject(key) ?: return null
    return try {
        val resultMap = mutableMapOf<String, List<EmbedInfo>>()
        val keysIterator = containerObj.keys()
        // Iterar dinámicamente sobre todas las categorías de servidores (SUB, MEGA, etc.)
        while (keysIterator.hasNext()) {
            val category = keysIterator.next()
            val itemsArray = containerObj.optJSONArray(category) ?: continue
            val validItems = List(itemsArray.length()) { i ->
                itemsArray.optJSONObject(i)?.let { item ->
                    var url = item.optString("url")
                    // Solo incluimos si tiene URL válida
                    if (url.isNotBlank()) {
                        if ("pixeldrain" in url) url = url.replace("?embed", "")
                        EmbedInfo(
                            server = item.optString("server", "Unknow"),
                            url = url
                        )
                    } else null
                }
            }.filterNotNull()
            if (validItems.isNotEmpty()) {
                resultMap[category] = validItems
            }
        }
        resultMap.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.e("AnimeAV1", "Error al extraer '$key': ${e.message}")
        null
    }
}

// Limpia un string de JS para convertirlo en JSON válido.
private fun cleanJsToJson(js: String): String {
    return js.replace("void 0", "null").replace(
        Regex("""(?<=[{,])\s*(\w+)\s*:""")
    ) {
        "\"${it.groupValues[1]}\":"
    }.trim()
}

//-------------------------------------//
//              data class             //
//-------------------------------------//
// data class MediaDateJSON
data class MediaDateJSON(
    val mediaID: Int? = null,
    val malId: Int? = null,
    val title: String? = null,
    val type: String? = null,
    val status: Int = 0,
    val startDate: String? = null,
    val episodesCount: Int? = null,
    val startEpisode: Int? = null,
    val slug: String? = null,
    val score: Int? = null,
    val genre: List<String>? = null,
    val synopsis: String? = null,
    val embeds: Map<String, List<EmbedInfo>>? = null,
    val recommendations: List<MediaRelation>? = null
)
// data class MediaRelation
data class MediaRelation(
    val type: Int,
    val title: String,
    val href: String,
    val posterUrl: String
)
// data class EmbedInfo
data class EmbedInfo(
    val server: String,
    val url: String
)

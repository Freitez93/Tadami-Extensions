package eu.kanade.tachiyomi.animeextension.es.sololatino

import android.annotation.SuppressLint
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.lib.universalextractor.UniversalExtractor
import eu.kanade.tachiyomi.lib.uqloadextractor.UqloadExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import extensions.utils.Source
import keiyoushi.utils.parallelFlatMapBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale


class SoloLatino : Source() {
    override val name = "SoloLatino"
    override val baseUrl = "https://sololatino.net"
    override val lang = "es"
    override val supportsLatest = true
    override val id: Long = 2168637495373172929L

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/buscar?tipo=todo&orden=popularidad&page=$page")
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/buscar?tipo=todo&orden=recientes&page=$page")
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = SoloLatinoFilters.getSearchParameters(filters)
        var finalQuery = "$baseUrl/"
        // Si es una Plataforma los demas Filtros se ignoran
        if (params.platform.isNotEmpty()){
            finalQuery = finalQuery.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("red")
                .addPathSegment(params.platform)
                .addQueryParameter("año", params.year)
                .addQueryParameter("nota", params.note)
                .addQueryParameter("orden", params.sort)
                .addQueryParameter("page", page.toString())
                .build().toString()
        }
        // Si es un Tipo.
        else if (query.isNotEmpty()) {
            finalQuery = finalQuery.toHttpUrlOrNull()!!.newBuilder()
                .addPathSegment("buscar")
                .addQueryParameter("q", URLEncoder.encode(query, "utf-8"))
                .addQueryParameter("genero", params.genre)
                .addQueryParameter("año", params.year)
                .addQueryParameter("nota", params.note)
                .addQueryParameter("orden", params.sort)
                .addQueryParameter("page", page.toString())
                .build().toString()
        }
        // Por defecto
        else {
            finalQuery = finalQuery.toHttpUrlOrNull()!!.newBuilder()
                .let {
                    if (params.type.isNotEmpty()) { it.addPathSegment(params.type) }
                    else { it.addPathSegment("buscar") }
                }
                .addQueryParameter("genero", params.genre)
                .addQueryParameter("año", params.year)
                .addQueryParameter("nota", params.note)
                .let {
                    if (params.type.isNotEmpty()) {
                        val sort = when (params.sort) {
                            "fecha" -> "updated"
                            "popularidad" -> "popular"
                            "nota" -> "rating"
                            else -> params.sort
                        }
                        it.addQueryParameter("sort", sort)
                    } else {
                        it.addQueryParameter("orden", params.sort)
                    }
                }
                .addQueryParameter("page", page.toString())
                .build().toString()
        }
        return GET(finalQuery, headers)
    }
    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animeList = mutableListOf<SAnime>()
        doc.select("div.card").forEach { element ->
            val item = parseAnimeItem(element)
            if (item.title != "Unknown Title") {
                animeList.add(item)
            }
        }
        val hasNextPage = doc.select("a[rel=next]").isNotEmpty()
        return AnimesPage(animeList.distinctBy { it.url }, hasNextPage)
    }
    private fun parseAnimeItem(element: Element): SAnime = SAnime.create().apply {
        this.url = element.selectFirst("a")?.attr("href") ?: return@apply
        this.title = element.selectFirst("img")?.attr("alt") ?: "Unknown Title"
        this.thumbnail_url = element.selectFirst("img")
            ?.attr("src")
            ?: "https://sololatino.net/images/no-poster.jpg"
        //fetch_type = FetchType.Episodes
    }
    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            this.title = doc.selectFirst("div > img[style]")!!.attr("alt")
            this.thumbnail_url = doc.selectFirst("div > img[style]")
                ?.attr("src")
                ?.replace("/w500/", "/original/")
            this.background_url = doc.selectFirst("meta[property='og:image']")?.attr("content")
            this.description = doc.selectFirst("p[class*='leading-relaxed']")?.text()

            // Metadata parsing
            this.genre = doc.select("div.flex > a[href*='/genero/']")
                .joinToString(" ,") {
                    it.text()
                }
            // Strict author parsing to avoid metadata
            this.author = doc.selectFirst("a[href*='/persona/']:matches(Creador|Director) > img")
                ?.attr("alt")
            // Status is generally Completed for movies
            val statusText = doc.selectFirst("div.items-center > span.rounded")?.text() ?: "Ended"
            this.status = when (statusText) {
                "Returning Series" -> SAnime.ONGOING
                "Ended" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            // Extra info extraction for description
            val year = doc.selectFirst("title")?.text()
                ?.substringAfter("(")
                ?.substringBefore(")")
            val extraInfo = getExtraInfo(doc, year ?: "Unknow")
            this.description = (description + extraInfo).trim()
            this.fetch_type = FetchType.Episodes
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        val url = response.request.url.toString()
        val episodes = mutableListOf<SEpisode>()

        // Si Es Serie/Dorama/Anime se Extraen los Episodios.
        var epNumCount = 0
        doc.select("a[class*='ep-item']").mapNotNull {
            val epUrl = it.attr("href")
            val epName = it.selectFirst("p[class*=text-white]")?.text()
            val epTemp = epUrl.substringAfter("temporada-").substringBefore("/")
            val epNumb = epUrl.substringAfter("episodio-")
            val epTime = it.selectFirst("p[style='color:#404060']")?.text()
            epNumCount++
            episodes.add(SEpisode.create().apply {
                this.url = epUrl
                this.name = "S$epTemp:E$epNumb - $epName"
                this.date_upload = getDateLong("dd/MM/yyyy", epTime)
                this.episode_number = epNumCount.toFloat()
                //this.fillermark = false
                //this.scanlator = ""
                this.summary = it.selectFirst("p[class*=line-clamp-2]")?.text()
                this.preview_url = it.selectFirst("img")?.attr("src")
            })
        }
        // Retornamos la lista de Episodios
        return if (episodes.isNotEmpty()) {
            episodes.reversed()
        } else {
            val movieDate = doc.selectFirst("div.detail-field:contains(Estreno) > dd")?.text()
            val previewIm = doc.selectFirst("meta[property='og:image']")?.attr("content")
            listOf(SEpisode.create().apply {
                this.url = url
                this.name = "Pelicula"
                // Fecha de Estreno/Subida del Episodio.
                this.date_upload = getDateLong("dd 'de' MMMM 'de' yyyy", movieDate, Locale.US)
                this.episode_number = -1F
                //this.fillermark = false
                //this.scanlator = ""
                //this.summary = ""
                this.preview_url = previewIm
            })
        }
    }

    // =============================== Video ================================
    override fun videoListRequest(episode: SEpisode): Request = GET(episode.url, headers)
    override fun videoListParse(response: Response): List<Video> {
        val episodeUrl = response.request.url.toString()
        // Primero, obtenemos las cookies necesarias para autenticarnos con Sanctum
        client.newCall(GET("$baseUrl/sanctum/csrf-cookie", headers)).execute().close()

        // Luego, hacemos la solicitud a la página del contenido para obtener los enlaces de los servidores
        val episodeResponse = client.newCall(GET(episodeUrl, headers)).execute()
        val document = episodeResponse.asJsoup()
        val listHref = mutableListOf<String>()
        document.select("button[data-player-token], button[data-server-btn]").mapNotNull { it ->
            val playerToken = it.attr("data-player-token")
            // Si el botón tiene un token, hacemos una solicitud a la API.
            if (playerToken.isNotBlank()) {
                // Construir la URL de la API usando el playerToken y headers
                val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrlOrNull()!!)
                val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""

                val apiHeader = headers.newBuilder()
                    .add("Accept", "application/json")
                    .add("Content-Type", "application/json")
                    .add("X-XSRF-TOKEN", xsrfToken)
                    .add("X-Requested-With", "XMLHttpRequest")
                    .add("Referer", episodeUrl)
                    .build()

                // Realizar la solicitud a la API para obtener la URL del servidor
                try {
                    val body = "{\"t\":\"$playerToken\"}".toRequestBody("application/json".toMediaType())
                    client.newCall(POST("$baseUrl/api/player-url", apiHeader, body)).execute().use { apiResponse ->
                        val jsonObject = JSONObject(apiResponse.body.string())
                        val videoUrl = jsonObject.optString("url")
                        if (videoUrl.isNotBlank()) {
                            listHref.add(videoUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SoloLatino", "Error al obtener la URL del servidor desde la API: ${e.message}")
                }
            }
        }
        return listHref.parallelFlatMapBlocking { url ->
            when {
                url.contains("embed69.org") -> {
                    Embed69(client).getLinks(url).flatMap { (language, links) ->
                        serverVideoResolver(links, " $language")
                    }
                }
                url.contains("re.sololatino.net") -> {
                    XupaLace(client).getLinks(url).flatMap { (language, links) ->
                        serverVideoResolver(links, " $language")
                    }
                }
                else -> emptyList()
            }
        }
    }
    // ============================== Filters ===============================
    override fun getFilterList() = SoloLatinoFilters.FILTER_LIST

    // ============================= Preferences ============================
    @SuppressLint("ApplySharedPref")
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Calidad Preferida"
            entries = QUALITIES
            entryValues = QUALITIES
            setDefaultValue(QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                if (index != -1) {
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                } else {
                    true
                }
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_server"
            title = "Servidor Preferido"
            entries = SERVERS
            entryValues = SERVERS
            setDefaultValue(SERVER_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                if (index != -1) {
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                } else {
                    true
                }
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_lang"
            title = "Idioma Preferido"
            entries = LANGUAGES_DISPLAY
            entryValues = LANGUAGES_VALUES
            setDefaultValue(LANGNGUAGE_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                if (index != -1) {
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                } else {
                    true
                }
            }
        }.also(screen::addPreference)
    }

    //
    // ========================= Funciones Auxiliares =======================
    private fun getExtraInfo(node: Element, year: String): String {
        var output = "\n\n – Año: $year"
        arrayOf(
            "Temporadas", "Estreno", "Último aire", "Duración", "País",
            "Idioma Original", "Título original", "Clasificación", "Certificación"
        ).forEach {
            val dataFound = node.selectFirst("div.detail-field:contains($it) > dd")?.text()
            if (dataFound != null) {
                output += if (it == "Temporadas") {
                    " • $dataFound temp."
                } else {
                    "\n – $it: $dataFound"
                }
            }
        }
        return output
    }
    private fun getDateLong(format: String, date: String?, locale: Locale = Locale.getDefault()): Long {
        if (date == null) return 0L
        val dateFormat = SimpleDateFormat(format, locale)
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    // Extractores.
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val uqloadExtractor by lazy { UqloadExtractor(client) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val mp4UploadExtractor by lazy { Mp4uploadExtractor(client) }
    private val universalExtractor by lazy { UniversalExtractor(client) }
    private val wolfStreamExtractor by lazy { WolfstreamExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private fun serverVideoResolver(urls: List<String>, prefix: String = ""): List<Video> {
        return urls.parallelFlatMapBlocking { url ->
            runCatching {
                Log.d("SoloLatino", "Resolviendo URL: $url")
                when {
                    "voe" in url -> voeExtractor.videosFromUrl(url, "$prefix ")
                    "uqload" in url -> uqloadExtractor.videosFromUrl(url, prefix)
                    "mp4upload" in url -> mp4UploadExtractor.videosFromUrl(url, headers, "$prefix ")
                    "wolfstream" in url -> wolfStreamExtractor.videosFromUrl(url, "$prefix ")
                    "filemoon" in url -> filemoonExtractor.videosFromUrl(url, "$prefix Filemoon:")
                    "vidhide" in url -> vidHideExtractor.videosFromUrl(url, videoNameGen = { "$prefix VidHide:$it" })
                    "streamwish" in url -> streamWishExtractor.videosFromUrl(url, "$prefix StreamWish")
                    else -> universalExtractor.videosFromUrl(url, headers, prefix = "$prefix ")
                }
            }.getOrDefault(emptyList()) // Manejo seguro de fallos
        }.sort()
    }
    // Ordenar los videos
    private fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", QUALITY_DEFAULT)!!
        val lang = preferences.getString("preferred_lang", LANGNGUAGE_DEFAULT)!!
        val server = preferences.getString("preferred_server", SERVER_DEFAULT)!!
        return sortedWith(
            compareBy(
                { it.videoTitle.contains(lang, true) },
                { it.videoTitle.contains(server, true) },
                { it.videoTitle.contains(quality) },
            ),
        ).reversed()
    }

    companion object {
        // Elementos divididos apropiadamente en lugar de un string aglomerado
        private val QUALITIES = arrayOf("1080p", "720p", "480p", "360p")
        private const val QUALITY_DEFAULT = "720p"

        private val SERVERS = arrayOf("StreamWish", "Uqload", "Hglink", "Mp4Upload", "Voe", "VidHide")
        private const val SERVER_DEFAULT = "VidHide"

        private val LANGUAGES_VALUES = arrayOf("ESP", "LAT", "SUB")
        private val LANGUAGES_DISPLAY = arrayOf("Español", "Español Latino", "Subtitulado")
        private const val LANGNGUAGE_DEFAULT = "LAT"
    }
}

package eu.kanade.tachiyomi.animeextension.es.sololatino

import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.filemoonextractor.FilemoonExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.streamwishextractor.StreamWishExtractor
import aniyomi.lib.universalextractor.UniversalExtractor
import aniyomi.lib.uqloadextractor.UqloadExtractor
import aniyomi.lib.vidhideextractor.VidHideExtractor
import aniyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.Source
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class SoloLatino : Source() {
    override val name = "SoloLatino"
    override val baseUrl = "https://sololatino.net"
    override val lang = "es"
    override val supportsLatest = true

    // Extractores Utilizados.
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val uqloadExtractor by lazy { UqloadExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client, headers) }
    private val universalExtractor by lazy { UniversalExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/buscar?tipo=todo&orden=popularidad&page=$page")
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/buscar?tipo=todo&orden=recientes&page=$page")
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = SoloLatinoFilters.getSearchParameters(filters)
        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder().apply {
            when {
                // Caso 1: Búsqueda por texto (query)
                // Ejemplo: /buscar?q=the&genero=accion&año=2020&nota=7&orden=az
                query.isNotEmpty() -> {
                    addPathSegment("buscar")
                    addQueryParameter("q", query)
                    addQueryParameter("genero", params.genre)
                    addQueryParameter("orden", params.sort)
                }

                // Caso 2: Filtrado por Plataforma (ignora tipo y género)
                // Ejemplo: /red/netflix?año=0&nota=8&orden=popularidad
                params.platform.isNotEmpty() -> {
                    addPathSegment("red")
                    addPathSegment(params.platform)
                    addQueryParameter("orden", params.sort)
                }

                // Caso 3: Filtrado por Tipo o Catálogo general
                // Ejemplo: /series?genero=&año=0&nota=0&sort=popular
                else -> {
                    if (params.type.isNotEmpty()) {
                        addPathSegment(params.type)
                        // El sitio cambia el nombre del parámetro 'orden' a 'sort'
                        // y sus valores cuando se filtra por un tipo específico.
                        val sortValue = when (params.sort) {
                            "fecha" -> "updated"
                            "popularidad" -> "popular"
                            "nota" -> "rating"
                            else -> params.sort
                        }
                        addQueryParameter("sort", sortValue)
                    } else {
                        addPathSegment("buscar")
                        addQueryParameter("orden", params.sort)
                    }
                    addQueryParameter("genero", params.genre)
                }
            }

            // Parámetros comunes que se aplican a todas las peticiones
            (params.year != "0").also { addQueryParameter("año", params.year) }
            (params.note != "0").also { addQueryParameter("nota", params.note) }
            addQueryParameter("page", page.toString())
        }

        return GET(url.build().toString(), headers)
    }
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val nextPage = document.select("a[rel=next]").any()
        val animeList = document.select("div.card").map {
            val url = it.selectFirst("a")!!.attr("href")
            SAnime.create().apply {
                this.url = absUrl(url)
                this.title = it.selectFirst("img")?.attr("alt") ?: "Not Title"
                this.thumbnail_url = it.selectFirst("img")?.attr("src")
                    ?: "https://sololatino.net/images/no-poster.jpg"
                // this.fetch_type = FetchType.Episodes
            }
        }
        return AnimesPage(animeList, nextPage)
    }

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        val synopsis = doc.selectFirst("p[class*='leading-relaxed']")?.text()
        return SAnime.create().apply {
            this.title = doc.selectFirst("div > img[style]")!!.attr("alt")
            this.thumbnail_url = doc.selectFirst("div > img[style]")?.attr("src")
            this.background_url = doc.selectFirst("meta[property='og:image']")?.attr("content")
            this.description = buildString {
                append("${synopsis ?: "Sin descripción disponible."}\n")
                // Extra info extraction for description
                doc.getInfo("Estreno|Año")?.also { append("\n – Estreno: $it") }
                doc.getInfo("Temporadas")?.also { append(" • $it temp.") }
                doc.getInfo("Último aire")?.also { append("\n – Último Episodio: $it") }
                doc.getInfo("Título original")?.also { append("\n – Título Original: $it") }
                doc.getInfo("Idioma original")?.also { append("\n – Idioma Original: $it") }
                doc.getInfo("Duración")?.also { append("\n – Duración: $it") }
                doc.getInfo("País")?.also { append("\n – País: $it") }
                doc.getInfo("Clasificación|Certificación")?.also { append("\n – Clasificación: $it") }
            }

            // Metadata parsing
            this.genre = doc.select("div.flex > a[href*='/genero/']")
                .joinToString { it.text() }
            // Strict author parsing to avoid metadata
            this.author = doc.selectFirst("a[href*='/persona/']:matches(Creador|Director) > img")
                ?.joinToString { it.attr("alt") }
            // Status is generally Completed for movies
            val statusText = doc.selectFirst("div.items-center > span.rounded")?.text() ?: "Ended"
            this.status = when (statusText) {
                "Returning Series" -> SAnime.ONGOING
                "Ended" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            // this.fetch_type = FetchType.Episodes
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        val url = response.request.url.toString()
        val movieDate = doc.selectFirst("div.detail-field:contains(Estreno) > dd")?.text()
        val previewIm = doc.selectFirst("meta[property='og:image']")?.attr("content")
        val episodes = mutableListOf<SEpisode>()

        // Si Es Serie/Dorama/Anime se Extraen los Episodios.
        var epNumCount = 0
        doc.select("a[class*='ep-item']").mapNotNull { epEle ->
            val epHref = epEle.attr("href")
            val epName = epEle.selectFirst("p[class*=text-white]")?.text()
            val epTemp = epHref.substringAfter("temporada-").substringBefore("/")
            val epNumb = epHref.substringAfter("episodio-")
            val epTime = epEle.selectFirst("p[style='color:#404060']")?.text()
            epNumCount++
            episodes.add(
                SEpisode.create().apply {
                    this.url = epHref
                    this.name = "S$epTemp:E$epNumb - $epName"
                    this.date_upload = getDateLong("dd/MM/yyyy", epTime)
                    this.episode_number = epNumCount.toFloat()
                    // this.fillermark = false
                    // this.scanlator = ""
                    this.summary = epEle.selectFirst("p[class*=line-clamp-2]")?.text()
                    this.preview_url = epEle.selectFirst("img")?.attr("src")
                        ?.takeIf { it.isNotBlank() }
                        ?: previewIm
                },
            )
        }
        // Retornamos la lista de Episodios
        return if (episodes.isNotEmpty()) {
            episodes.reversed()
        } else {
            listOf(
                SEpisode.create().apply {
                    this.url = url
                    this.name = "Pelicula"
                    // Fecha de Estreno/Subida del Episodio.
                    this.date_upload = getDateLong("dd 'de' MMMM 'de' yyyy", movieDate, Locale.US)
                    this.episode_number = -1F
                    // this.fillermark = false
                    // this.scanlator = ""
                    // this.summary = ""
                    this.preview_url = previewIm
                },
            )
        }
    }

    // ============================ Hoster ========================================
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        return try {
            val response = client.newCall(GET(episode.url, headers)).await()
            val document = response.asJsoup()
            document.apiPlayerToken("button[data-player-token]")
                .flatMap { url ->
                    if (url.contains("embed69.org") || url.contains("xupalace.org")) {
                        Embed69(client).getLinks(url).flatMap { (_, hostLang, hostersByLang) ->
                            hostersByLang.mapNotNull { hoster ->
                                val hostLink = hoster.link ?: return@mapNotNull null
                                val hostName = getHosterName(hostLink)
                                Hoster(
                                    hosterName = "$hostName ✦ $hostLang",
                                    hosterUrl = hostLink,
                                    internalData = hostName,
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                }.sortHosters()
        } catch (e: Exception) {
            Log.e(name, "No se pudo obtener la lista de hosters para ${episode.url}")
            emptyList()
        }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        val hosterUrl = hoster.hosterUrl
        val hosterName = hoster.internalData
        Log.d("SoloLatino", "Resolviendo URL: $hosterName -> $hosterUrl")
        return when (hosterName) {
            "Voe" -> voeExtractor.videosFromUrl(hosterUrl, "$hosterName:")

            "Uqload" -> uqloadExtractor.videosFromUrl(hosterUrl, "$hosterName:")

            "Mp4Upload" -> mp4uploadExtractor.videosFromUrl(hosterUrl, "$hosterName:")

            "FileMoon" -> filemoonExtractor.videosFromUrl(hosterUrl, "$hosterName:")

            "VidHide" -> vidHideExtractor.videosFromUrl(hosterUrl, "$hosterName:")

            "StreamWish" -> {
                var firstCall = streamWishExtractor.videosFromUrl(hosterUrl, "$hosterName:")
                if (firstCall.isEmpty()) {
                    universalExtractor.videosFromUrl(hosterUrl, headers, videoNameGen = { "$hosterName:$it" })
                } else {
                    firstCall
                }
            }

            else -> universalExtractor.videosFromUrl(hosterUrl, headers, prefix = "")
        }.sortVideos() // Manejo seguro de fallos
    }

    // ============================== Filters ===============================
    override fun getFilterList() = SoloLatinoFilters.FILTER_LIST

    // ================================ Sorts ===============================

    /**
     * Standardized hoster sorting based on language tags and user preferences.
     */
    override fun List<Hoster>.sortHosters(): List<Hoster> {
        val prefLang = preferences.getString("preferred_lang", PREF_LANG_DEFAULT)!!
        val prefHost = preferences.getString("preferred_host", PREF_HOST_DEFAULT)!!
        return sortedWith(
            compareByDescending<Hoster> {
                it.hosterName.contains(prefHost, true)
            }.thenByDescending {
                it.hosterName.contains(prefLang, true)
            },
        )
    }

    /**
     * Standardized video sorting based on user preferences.
     */
    override fun List<Video>.sortVideos(): List<Video> {
        val prefHost = preferences.getString("preferred_host", PREF_HOST_DEFAULT)!!
        val prefQlty = preferences.getString("preferred_qlty", PREF_QLTY_DEFAULT)!!
        return this.map {
            it.copy(
                preferred = it.videoTitle.contains(prefQlty, true) && it.videoTitle.contains(prefHost, true),
            )
        }
    }

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_host"
            title = "Servidor Preferido"
            entries = PREF_HOST_VALUES
            entryValues = PREF_HOST_VALUES
            setDefaultValue(PREF_HOST_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_lang"
            title = "Idioma Preferido"
            entries = PREF_LANG_DISPLAY
            entryValues = PREF_LANG_VALUES
            setDefaultValue(PREF_LANG_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Calidad Preferida"
            entries = PREF_QLTY_DISPLAY
            entryValues = PREF_QLTY_VALUES
            setDefaultValue(PREF_QLTY_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    //
    // ========================= Funciones Auxiliares =======================
    // Extrae informacion extra en la pagina.
    private fun Element.getInfo(key: String): String? {
        val dataFound = this.selectFirst("div.detail-field:matches($key) > dd")
            ?.text()
            ?.takeIf(String::isNotBlank)
        return dataFound
    }

    // Transforma una fecha en formato String a la fecha en formato Long
    private fun getDateLong(format: String, date: String?, locale: Locale = Locale.getDefault()): Long {
        if (date == null) return 0L
        val dateFormat = SimpleDateFormat(format, locale)
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Obtenert una URL absoluta usando baseUrl.
    private fun absUrl(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> baseUrl + path
        else -> path
    }

    // Obtiene el hoster de la url
    private fun getHosterName(url: String): String = when {
        "voe" in url -> "Voe"
        "uqload" in url -> "Uqload"
        "mp4upload" in url -> "Mp4Upload"
        listOf("byse", "filemoon").any { it in url } -> "FileMoon"
        listOf("hglink", "streamwish").any { it in url } -> "StreamWish"
        listOf("minochinos", "vidhide").any { it in url } -> "VidHide"
        else -> "Default"
    }

    private fun getXsrfToken(): String {
        // Primero, obtenemos las cookies necesarias para autenticarnos con Sanctum
        client.newCall(GET("$baseUrl/sanctum/csrf-cookie", headers)).execute().close()
        return client.cookieJar.loadForRequest(baseUrl.toHttpUrlOrNull()!!).find {
            it.name == "XSRF-TOKEN"
        }?.value?.let {
            java.net.URLDecoder.decode(it, "UTF-8")
        } ?: ""
    }

    private fun Element.apiPlayerToken(css: String): List<String> {
        val episodeUrl = this.select("meta[property='og:url']").attr("content")
        val xsrfToken = getXsrfToken()
        return this.select(css).mapNotNull { it ->
            val playerToken = it.attr("data-player-token")
            // Si el botón tiene un token, hacemos una solicitud a la API.
            if (playerToken.isNotBlank()) {
                // Construir la URL de la API usando el playerToken y headers
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
                    client.newCall(
                        POST("$baseUrl/api/player-url", apiHeader, body),
                    ).execute().use { apiResponse ->
                        JSONObject(apiResponse.body.string()).optString("url")
                    }
                } catch (e: Exception) {
                    Log.e("SoloLatino", "Error al obtener la URL del servidor desde la API: ${e.message}")
                    null
                }
            } else {
                null
            }
        }
    }

    companion object {
        // Elementos divididos apropiadamente en lugar de un string aglomerado
        private val PREF_QLTY_VALUES = arrayOf("1080", "720", "480", "360")
        private val PREF_QLTY_DISPLAY = arrayOf("1080p", "720p", "480p", "360p")
        private const val PREF_QLTY_DEFAULT = "720p"

        private val PREF_HOST_VALUES = arrayOf("StreamWish", "Mp4Upload", "FileMoon", "Uqload", "VidHide", "Voe")
        private const val PREF_HOST_DEFAULT = "VidHide"

        private val PREF_LANG_VALUES = arrayOf("ESP", "LAT", "SUB")
        private val PREF_LANG_DISPLAY = arrayOf("🇪🇸 Español", "🇲🇽 Latino", "🇪🇺 Subtitulado")
        private const val PREF_LANG_DEFAULT = "LAT"
    }
}

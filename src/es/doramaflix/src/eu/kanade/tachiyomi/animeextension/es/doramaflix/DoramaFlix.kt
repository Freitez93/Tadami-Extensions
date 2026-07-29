package eu.kanade.tachiyomi.animeextension.es.doramaflix

import android.annotation.SuppressLint
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import aniyomi.lib.doodextractor.DoodExtractor
import aniyomi.lib.filemoonextractor.FilemoonExtractor
import aniyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import aniyomi.lib.okruextractor.OkruExtractor
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
import eu.kanade.tachiyomi.network.await
import keiyoushi.utils.Source
import keiyoushi.utils.bodyString
import keiyoushi.utils.parallelFlatMap
import keiyoushi.utils.parseGraphQLAs
import okhttp3.Request
import okhttp3.Response
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DoramaFlix : Source() {
    override val name = "DoramaFlix"
    override val lang = "es"
    override val baseUrl = "https://doramasflix.in"
    override val supportsLatest = true

    // Extractores Utilizados.
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val filemoonExtractor by lazy { FilemoonExtractor(client) }
    private val uqloadExtractor by lazy { UqloadExtractor(client) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client, headers) }
    private val universalExtractor by lazy { UniversalExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client, headers) }
    private val doodExtractor by lazy { DoodExtractor(client) }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = DoramaFlixLib.fetchDoramas(
        page = page,
        perPage = 32,
        sort = "POPULARITY_DESC",
        isTVShow = false,
    )
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = DoramaFlixLib.fetchDoramas(
        page = page,
        perPage = 32,
        sort = "_ID_DESC",
        isTVShow = false,
    )
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = DoramaFlixFilters.getSearchParameters(filters)
        Log.d(name, params.toString())
        return when {
            // Caso 1: Búsqueda por texto (query)
            query.isNotEmpty() -> {
                DoramaFlixLib.fetchSearchAll(query, 10)
            }

            // Caso 2: Filtrado por Tipo doramas
            params.type == "doramas" -> DoramaFlixLib.fetchDoramas(
                page = page,
                perPage = 32,
                sort = params.sort,
                isTVShow = false,
                year = params.year,
                country = params.country,
                languages = params.language,
                networks = params.network,
                genres = params.genre,
                labels = params.label,
            )

            // Caso 2: Filtrado por Tipo peliculas
            params.type == "peliculas" -> DoramaFlixLib.fetchMovies(
                page = page,
                perPage = 32,
                sort = params.sort,
                year = params.year,
                country = params.country,
                languages = params.language,
                networks = params.network,
                genres = params.genre,
                labels = params.label,
            )

            // Caso 3: Catálogo general
            else -> {
                DoramaFlixLib.fetchSearchAll("My Stange Hero", 10)
            }
        }
    }
    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.bodyString()
        Log.d(name, "searchAnimeParse -> $body")
        return try {
            val dataJson = body.parseGraphQLAs<PaginationModel>()
            val nextPage = dataJson.pagination?.hasNextPage ?: false
            val animeList = dataJson.items.map {
                SAnime.create().apply {
                    this.url = siteUrl(it.typename, it.slug, it.id)
                    this.title = it.name
                    this.thumbnail_url = it.getPoster("w500")
                    // this.fetch_type = FetchType.Episodes
                }
            }
            AnimesPage(animeList, nextPage)
        } catch (e: Exception) {
            Log.d(name, e.toString())
            AnimesPage(emptyList(), false)
        }
    }

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request {
        val contentID = anime.url.split("#").getOrNull(1) ?: ""
        val isDorama = anime.url.contains("/doramas-online/", true)
        return if (isDorama) {
            DoramaFlixLib.fetchDoramaDetail(contentID)
        } else {
            DoramaFlixLib.fetchMovieDetail(contentID)
        }
    }
    override fun animeDetailsParse(response: Response): SAnime {
        val body = response.bodyString()
        return try {
            val dataJson = body.parseGraphQLAs<PaginationDetail>().getDetail
                ?: throw Error("DetailMovie o DetailDorama no existen.")
            SAnime.create().apply {
                this.url = siteUrl(dataJson.typename, dataJson.slug, dataJson.id)
                this.title = dataJson.name
                this.thumbnail_url = dataJson.getPoster("original")
                this.background_url = dataJson.getBackdrop("original")
                this.description = buildString {
                    append("${dataJson.overview?.trim() ?: "Sin descripción disponible."}\n")
                    // Extra info extraction for description
                    append("\n – Estreno: ${dataJson.getRelease}")
                    dataJson.numberOfSeasons?.also { append(" • $it temp.") }
                    dataJson.lastAirDate?.also { append("\n – Último Episodio: $it") }
                    dataJson.nameEs?.also { append("\n – Título Español: $it") }
                    dataJson.names?.also { append("\n – Título Alt: $it") }
                    append("\n – Idiomas: ${dataJson.getLanguage.joinToString(", ")}")
                    append("\n – País: ${dataJson.getCountry}")
                    dataJson.getDuration?.also { append("\n – Duración: $it") }
                    append("\n – Rating: ${"%.1f".format(dataJson.rating)}")
                    append("\n – Etiquetas: ${dataJson.labels.joinToString { it.name }}")
                    append("\n – Plataformas: ${dataJson.networks.joinToString { it.name }}")
                }

                // Metadata parsing
                this.genre = dataJson.genres.joinToString { it.name }
                // Strict author parsing to avoid metadata
                this.author = dataJson.createdBy.joinToString { it.name }
                // Status is generally Completed for movies
                this.status = when {
                    dataJson.typename == "Movie" -> SAnime.COMPLETED
                    dataJson.isFinish -> SAnime.COMPLETED
                    dataJson.premiere -> SAnime.ONGOING
                    else -> SAnime.UNKNOWN
                }
                // this.fetch_type = FetchType.Episodes
            }
        } catch (e: Exception) {
            Log.d(name, "Error en animeDetailsParse: ${e.message ?: e}")
            SAnime.create()
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request {
        val contentID = anime.url.split("#").getOrNull(1) ?: ""
        val isDorama = anime.url.contains("/doramas-online/", true)
        return if (isDorama) {
            DoramaFlixLib.fetchEpisodes(contentID)
        } else {
            DoramaFlixLib.fetchMovieDetail(contentID)
        }
    }
    override fun episodeListParse(response: Response): List<SEpisode> {
        val body = response.bodyString()
        Log.d(name, "episodeListParse: $body")

        val episodes = mutableListOf<SEpisode>()
        var epdIndex = 0
        return try {
            val bodyParse = body.parseGraphQLAs<PaginationEpisodeWrapper>()
            val dataMovie = bodyParse.detailMovie
            bodyParse.getItems?.forEach { episode ->
                epdIndex++
                episodes.add(
                    SEpisode.create().apply {
                        this.url = siteUrl(episode.typename, episode.slug, episode.serieId)
                        this.name = episode.getName
                        this.date_upload = parseDateToLong(episode.getRelease, "dd-MM-yyyy")
                        this.episode_number = epdIndex.toFloat()
                        // this.fillermark = false
                        // this.scanlator = ""
                        this.summary = episode.overview
                        this.preview_url = episode.getPreview()
                    },
                )
            }
            if (dataMovie != null) {
                episodes.add(
                    SEpisode.create().apply {
                        this.url = siteUrl(dataMovie.typename, dataMovie.slug, dataMovie.id)
                        this.name = "Pelicula"
                        this.date_upload = parseDateToLong(dataMovie.getRelease, "yyyy-MM-dd")
                        this.episode_number = epdIndex.toFloat()
                        // this.fillermark = false
                        // this.scanlator = ""
                        this.summary = "Duración: ${dataMovie.getDuration}"
                        this.preview_url = dataMovie.getBackdrop("w500")
                    },
                )
            }
            episodes.reversed()
        } catch (e: Exception) {
            Log.d(name, "Error en episodeListParse: ${e.message ?: e}")
            episodes
        }
    }

    // ============================ Hoster ========================================
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val request = if (episode.url.contains("/episodios/", true)) {
            val doramaId = episode.url.substringAfterLast("/").split("#").getOrNull(0) ?: ""
            DoramaFlixLib.fetchLinksOnline(doramaId, true)
        } else {
            val movieId = episode.url.split("#").getOrNull(1) ?: ""
            DoramaFlixLib.fetchLinksOnline(movieId, false)
        }

        return try {
            val response = client.newCall(request).await()
            val document = response.bodyString()
            val dataJson = document.parseGraphQLAs<PaginationLinkOnlineWrapper>()

            // 1. Agrupamos por idioma
            val groupedLinks = dataJson.getLinkOnline.groupBy { it.getLanguage }
                .mapValues { entry ->
                    // 2. Extraemos solo los pares válidos (NombreServidor to URL), ignorando nulos
                    entry.value.mapNotNull { item ->
                        val url = item.embed ?: item.link
                        if (!url.isNullOrEmpty()) {
                            "${item.getNameServer}<@>$url"
                        } else {
                            null
                        }
                    }
                }

            // 3. Mapeamos a la clase Hoster, serializando la lista a un String
            groupedLinks.map { (language, urls) ->
                Hoster(
                    hosterName = language,
                    // Genera: 'NombreServidor<@>URL<#>NombreServidor<@>URL'
                    hosterUrl = urls.joinToString("<#>") { it },
                )
            }.sortHosters()
        } catch (e: Exception) {
            Log.e(name, "No se pudo obtener la lista de hosters para ${episode.url}", e)
            emptyList()
        }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> = try {
        // 1. Dividimos el String que creamos en partes de 'NombreServidor<@>URL'
        val listServer = hoster.hosterUrl.split("<#>")
        val serverLang = hoster.hosterName

        // 2. Usamos flatMap para construir la lista de videos de forma limpia y sin bugs de mutación
        listServer.parallelFlatMap { nameAndUrl ->
            val (serverName, url) = nameAndUrl.split("<@>") // Dividimos el Nombre de la Url
            Log.d(name, "Resolviendo URL: $serverLang $serverName -> $url")
            when (serverName) {
                "Voe" -> voeExtractor.videosFromUrl(url, "$serverName:")

                "Uqload" -> uqloadExtractor.videosFromUrl(url, "$serverName:")

                "Mp4Upload" -> mp4uploadExtractor.videosFromUrl(url, "$serverName:")

                "Filemoon" -> filemoonExtractor.videosFromUrl(url, "$serverName:")

                "VidHide" -> vidHideExtractor.videosFromUrl(url, "$serverName:")

                "Dood" -> listOfNotNull(doodExtractor.videoFromUrl(url, "$serverName:"))

                "Ok" -> okruExtractor.videosFromUrl(url, "Okru:")

                "Streamwish" -> {
                    val firstCall = streamWishExtractor.videosFromUrl(url, "$serverName:")
                    firstCall.ifEmpty {
                        universalExtractor.videosFromUrl(
                            url,
                            headers,
                            videoNameGen = { "$serverName:$it" },
                        )
                    }
                }

                else -> universalExtractor.videosFromUrl(url, headers, prefix = "")
            }
        }.sortVideos() // Aplicamos el ordenamiento al final sobre toda la lista plana
    } catch (e: Exception) {
        Log.e(name, "Error al procesar los videos del hoster: ${hoster.hosterName}", e)
        emptyList()
    }

    // ============================== Filters ===============================
    override fun getFilterList() = DoramaFlixFilters.FILTER_LIST

    // ================================ Sorts ===============================

    /**
     * Standardized hoster sorting based on language tags and user preferences.
     */
    override fun List<Hoster>.sortHosters(): List<Hoster> {
        // Usamos elvis para dar un valor por defecto seguro en caso de que la preferencia sea nula
        val prefLang = preferences.getString("preferred_lang", PREF_LANG_DEFAULT) ?: PREF_LANG_DEFAULT

        return sortedWith(
            compareByDescending<Hoster> {
                // Esto devuelve true/false. Al ser Descending, los true (coincidencias) van primero.
                it.hosterName.contains(prefLang, ignoreCase = true)
            }.thenBy {
                // Ordena alfabéticamente tanto los que coinciden como los que no.
                it.hosterName
            },
        )
    }

    /**
     * Standardized video sorting based on user preferences.
     */
    override fun List<Video>.sortVideos(): List<Video> {
        // 1. Seguridad nula: usamos ?: en lugar de !! para evitar crashes
        val prefHost = preferences.getString("preferred_host", PREF_HOST_DEFAULT) ?: PREF_HOST_DEFAULT
        val prefQlty = preferences.getString("preferred_qlty", PREF_QLTY_DEFAULT) ?: PREF_QLTY_DEFAULT

        return this.sortedWith(
            compareByDescending<Video> {
                // 1º: Priorizar el servidor preferido (ej. "Voe")
                it.videoTitle.contains(prefHost, ignoreCase = true)
            }.thenByDescending {
                // 2º: Priorizar la calidad preferida (ej. "1080", "720")
                it.videoTitle.contains(prefQlty, ignoreCase = true)
            }.thenBy {
                // 3º: Desempate alfabético por nombre del servidor (fallback)
                it.videoTitle.substringBefore(":").trim()
            },
        )
    }

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_lang"
            title = "Idioma Preferido"
            entries = PREF_LANG_VALUES
            entryValues = PREF_LANG_VALUES
            setDefaultValue(PREF_LANG_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_host"
            title = "Servidor Preferido"
            entries = PREF_HOST_VALUES
            entryValues = PREF_HOST_VALUES
            setDefaultValue(PREF_HOST_DEFAULT)
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
    // Transforma una fecha en formato String a la fecha en formato Long
    @SuppressLint("NewApi")
    fun parseDateToLong(dateString: String?, formatter: String = "dd-MM-yyyy"): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            Instant.parse(dateString).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDate.parse(dateString, DateTimeFormatter.ofPattern(formatter))
                    .atStartOfDay() // Convierte a las 00:00 horas
                    .toInstant(ZoneOffset.UTC) // Define la zona horaria en UTC
                    .toEpochMilli() // Obtiene el valor en milisegundos (Long)
            } catch (_: Exception) {
                0L
            }
        }
    }

    // Obtenert una URL absoluta usando baseUrl.
    private fun siteUrl(type: String, slug: String, id: String): String = when (type.lowercase()) {
        "movie" -> "$baseUrl/peliculas-online/$slug#$id"
        "dorama" -> "$baseUrl/doramas-online/$slug#$id"
        "episode" -> "$baseUrl/episodios/$slug#$id"
        else -> ""
    }

    companion object {
        // Elementos divididos apropiadamente en lugar de un string aglomerado
        private val PREF_QLTY_VALUES = arrayOf("1080", "720", "480", "360")
        private val PREF_QLTY_DISPLAY = arrayOf("1080p", "720p", "480p", "360p")
        private const val PREF_QLTY_DEFAULT = "720"

        private val PREF_HOST_VALUES = arrayOf(
            "Ok", "Voe", "Dood", "Uqload", "Mp4Upload",
            "Streamwish", "Filemoon", "VidHide",
        )
        private const val PREF_HOST_DEFAULT = "Voe"

        private val PREF_LANG_VALUES = arrayOf(
            "Castellano", "Latino", "Portugués", "Coreano", "Japones",
            "Mandarín", "Tailandes", "Filipino", "Indones", "Vietnamita",
        )
        private const val PREF_LANG_DEFAULT = "Latino"
    }
}

package eu.kanade.tachiyomi.animeextension.all.xchina

import androidx.preference.PreferenceScreen
import aniyomi.lib.javcoverfetcher.JavCoverFetcher
import aniyomi.lib.javcoverfetcher.JavCoverFetcher.fetchHDCovers
import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.LazyMutable
import keiyoushi.utils.Source
import keiyoushi.utils.addListPreference
import keiyoushi.utils.delegate
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XChina : Source() {
    override val name = "xChina"
    override val lang = "all"
    override var baseUrl: String by preferences.delegate(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)
    override val supportsLatest = true

    // Variables de inicializacion.
    private var docHeaders by LazyMutable { newHeaders() }
    private var playlistExtractor by LazyMutable { PlaylistUtils(client, docHeaders) }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/videos/series-5f904550b8fcc/sort-read/$page.html", docHeaders)
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/videos/series-5f904550b8fcc/$page.html", docHeaders)
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val genre = filters.firstInstanceOrNull<GenreList>()?.selected
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected
        val fixQuery = query.replace(" ", "+").trim()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("videos")
            // Prioritize query search, then genre filter, then default category
            when {
                fixQuery.isNotBlank() -> addPathSegment("keyword-$fixQuery")
                genre != null -> addPathSegment(genre)
                else -> addPathSegment("series-6206216719462")
            }
            // Append sort order if it's not the default
            sort?.let { addPathSegment(it) }
            // Pagination
            addPathSegment("$page.html")
        }.build().toString()
        return GET(url, docHeaders)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val nextPage = document.selectFirst("a.next") != null
        val animeList = document.select("div.item.video").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            SAnime.create().apply {
                url = absUrl(link.attr("href"))
                title = link.attr("title")
                    .replace("&amp;#39;", "'")
                    .replace("&amp;quot;", "")
                    .replace("quot;", "")
                thumbnail_url = IMG_REGEX.find(element.html())?.groupValues?.get(1)
            }
        }
        return AnimesPage(animeList, nextPage)
    }

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request = GET(anime.url, docHeaders)
    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()

        // Recolectamos la Información de la Película/Anime/OVA
        val jpnTitle = document.getData("i.fa-film", "a")
        val webImage = document.select("div.screenshot-container img").map { it.attr("src") }
        val synopsis = document.getData("i.fa-address-card", "div.text")
        val director = document.getData("i.fa-clapperboard", "a")
        val maker = document.getData("i.fa-file-video", "a")
        val tags1 = document.getData("i.fa-video-camera", "a")
        val tags2 = document.getData("i.fa-tags", "div.text")
        // Obtener caratula en HD
        val coverHD = jpnTitle?.takeIf { preferences.fetchHDCovers }?.let {
            JavCoverFetcher.getCoverByTitle(it)
        }
        return SAnime.create().apply {
            // this.url
            // this.title
            this.artist = document.select("div.model-item").joinToString { it.text() }
            this.author = listOfNotNull(director, maker).joinToString()
            this.description = buildString {
                append("${synopsis ?: "No description found."}\n")
                // Informacion extra del AV.
                document.getData("i.fa-calendar-days", "a")?.also { append("\n – Release date: $it") }
                jpnTitle?.also { append("\n – Original Title: $it") }
                document.getData("i.fa-file", "div.text")?.also { append("\n – Code: $it") }
                artist?.also { append("\n – Actress: $it") }
                document.getData("i.fa-layer-group", "a")?.also { append("\n – Series: $it") }
                maker?.also { append("\n – Maker: $it") }
                director?.also { append("\n – Director: $it") }
            }
            this.genre = listOfNotNull(tags1, tags2).joinToString()
            this.status = SAnime.COMPLETED
            this.thumbnail_url = coverHD ?: webImage[0]
            this.background_url = webImage[1]
            // this.update_strategy
            // this.fetch_type = FetchType.Episodes
            // this.season_number
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val baseHref = response.request.url.toString()
        val webImage = document.select("div.screenshot-container img").map { it.attr("src") }
        val duration = document.selectFirst("meta[property='og:duration']")?.attr("content")?.toIntOrNull()
        val episodes = mutableListOf<SEpisode>()
        // Se Extraen los Episodios de MediaInfo.
        val dateText = document.getData("i.fa-calendar-days", "a")?.trim()
        episodes.add(
            SEpisode.create().apply {
                this.url = baseHref
                this.name = "Movie"
                this.date_upload = getDateLong("yyyy.MM.dd", dateText)
                this.episode_number = -1F
                // this.fillermark = false
                // this.scanlator = "MissAV"
                duration?.also {
                    this.summary = "Duration: ${secondsToTime(duration)}"
                }
                this.preview_url = webImage.getOrNull(1) ?: webImage.getOrNull(0)
            },
        )
        // Retornamos la lista de Episodios
        return episodes.reversed()
    }

    // =============================== Video ================================
    override fun videoListRequest(episode: SEpisode): Request = GET(absUrl(episode.url), headers)
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val masterPlaylist = M3U8_REGEX.find(document.html())?.groupValues?.get(1)
            ?: return emptyList()
        return playlistExtractor.extractFromHls(
            playlistUrl = masterPlaylist,
            referer = "$baseUrl/",
            videoNameGen = { quality -> "xChina:$quality" },
        )
    }

    // ============================== Filters ===============================
    override fun getFilterList() = getFilters()

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_DOMAIN_KEY,
            title = PREF_DOMAIN_TITLE,
            entries = PREF_DOMAIN_ENTRIES,
            entryValues = PREF_DOMAIN_VALUES,
            default = PREF_DOMAIN_DEFAULT,
            summary = "%s",
            restartRequired = true,
        ) {
            baseUrl = it
            docHeaders = newHeaders()
            playlistExtractor = PlaylistUtils(client, docHeaders)
        }

        JavCoverFetcher.addPreferenceToScreen(screen)
    }

    // ========================= Funciones Auxiliares =======================
    // Obtiene una información del AV.
    private fun Element.getData(css: String, find: String): String? {
        // 1. Intentamos buscar primero los elementos 'a' que cumplen la condición
        val elements = this.selectFirst("div.item:has($css)")
            ?.select(find)
        // 2. Ahora 'elements' contiene o los 'a' (si existían) o los 'div.text'
        return elements?.joinToString {
            it.text().replace(" / ", ", ")
        }?.takeIf(String::isNotBlank)
    }

    // Obtenert una URL absoluta usando baseUrl.
    private fun absUrl(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> baseUrl + path
        else -> path
    }

    // Transforma la fecha en Date Long
    fun getDateLong(format: String, date: String?, locale: Locale = Locale.getDefault()): Long {
        if (date == null) return 0L
        val dateFormat = SimpleDateFormat(format, locale)
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Transforma segundos a hh:mm:ss
    private fun secondsToTime(seconds: Int): String {
        // 1. Calcular las horas dividiendo entre los segundos de una hora (3600)
        val horas = seconds / 3600
        // 2. Obtener los segundos restantes con el residuo (%) y dividirlos entre 60
        val minutos = (seconds % 3600) / 60
        // 3. Obtener los segundos finales con el residuo de la división entre 60
        val segundos = seconds % 60
        // 4. Formatear el texto para asegurar los dos dígitos por cada elemento
        // Resultado: "01h 01m 05s"
        return if (horas > 0) {
            "%02dh %02dm %02ds".format(horas, minutos, segundos)
        } else {
            "%02dm %02ds".format(minutos, segundos)
        }
    }

    // Armar headers con baseUrl
    private fun newHeaders(): Headers = headers.newBuilder().apply {
        set("Origin", baseUrl)
        set("Referer", "$baseUrl/")
    }.build()

    // Obtener la primera key en una lista o Null
    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? = filterIsInstance<T>().firstOrNull()

    companion object {
        // Variables de baseUrl
        private const val PREF_DOMAIN_KEY = "preferred_domain"
        private const val PREF_DOMAIN_TITLE = "Preferred lang for titles and synopses (restart)"
        private val PREF_DOMAIN_ENTRIES = listOf(
            "🇨🇳 Simplified Chinese", "🇹🇼 Traditional Chinese",
            "🇰🇷 Korean", "🇺🇸 English", "🇪🇸 Spanish",
        )
        private val PREF_DOMAIN_VALUES = listOf(
            "https://xchina.co", "https://tw.xchina.co", "https://kr.xchina.co",
            "https://en.xchina.co", "https://es.xchina.co",
        )
        private val PREF_DOMAIN_DEFAULT = PREF_DOMAIN_ENTRIES.first()

        // Variables Otros.
        private val IMG_REGEX = """url\(['"]?(.*?)['"]?\)""".toRegex()
        private val M3U8_REGEX = """src:\s*['"](https:.*m3u8.*)['"]""".toRegex()
    }
}

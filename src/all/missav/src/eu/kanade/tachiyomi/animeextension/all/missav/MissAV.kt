package eu.kanade.tachiyomi.animeextension.all.missav

import android.util.Log
import androidx.preference.PreferenceScreen
import aniyomi.lib.javcoverfetcher.JavCoverFetcher
import aniyomi.lib.javcoverfetcher.JavCoverFetcher.fetchHDCovers
import aniyomi.lib.playlistutils.PlaylistUtils
import aniyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
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

class MissAV : Source() {
    override val name = "MissAV"
    override val lang = "all"
    override var baseUrl: String by preferences.delegate(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)
    override val supportsLatest = true

    // Variables de inicializacion.
    private var docHeaders by LazyMutable { newHeaders() }
    private var playlistExtractor by LazyMutable { PlaylistUtils(client, docHeaders) }

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/en/today-hot?page=$page", docHeaders)
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/en/new?page=$page", docHeaders)
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val genre = filters.firstInstanceOrNull<GenreList>()?.selected
            if (query.isNotEmpty()) {
                addEncodedPathSegments("en/search/$query")
            } else if (genre != null) {
                addEncodedPathSegments(genre)
            } else {
                addEncodedPathSegments("en/new")
            }
            filters.firstInstanceOrNull<SortFilter>()?.selected?.let {
                addQueryParameter("sort", it)
            }
            addQueryParameter("page", page.toString())
        }.build().toString()

        return GET(url, docHeaders)
    }
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val nextPage = document.select("a[rel=next]").any()
        val animeList = document.select("div[class*='thumbnail']").mapNotNull {
            val url = it.selectFirst("a")!!.attr("href")
            val code = it.selectFirst("a")!!.attr("alt")
            val title = it.selectFirst("img")!!.attr("alt")
            SAnime.create().apply {
                this.url = absUrl(url)
                this.title = getTitle(code, title)
                this.thumbnail_url = "https://fourhoi.com/$code/cover-n.jpg"
                // this.fetch_type = FetchType.Episodes
            }
        }
        return AnimesPage(animeList, nextPage)
    }

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val baseCode = response.request.url.toString().substringAfterLast("/")
        var webCover = "https://fourhoi.com/$baseCode/cover-n.jpg"
        if (preferences.fetchHDCovers) {
            val codeClean = getTitle(baseCode, getCode = true)
            webCover = JavCoverFetcher.getCoverById(codeClean) ?: webCover
            Log.d(name, "fetchHDCovers: $webCover")
        }
        // Recolectamos la Información de la Película/Anime/OVA
        val synopsis = document.selectFirst("div.mb-1")?.text()
        val director = document.getInfo("Director:")
        val maker = document.getInfo("Maker:")
        return SAnime.create().apply {
            // this.title = document.select("h1.text-base").text()
            this.artist = document.getInfo("Actress:")
            this.author = listOfNotNull(director, maker).joinToString()
            this.description = buildString {
                append("${synopsis ?: "No description found."}\n")
                // Informacion extra del AV.
                document.getInfo("Release date:")?.also { append("\n – Release date: $it") }
                document.getInfo("Title")?.also { append("\n – Original Title: $it") }
                document.getInfo("Code")?.also { append("\n – Code: $it") }
                document.getInfo("Actress")?.also { append("\n – Actress: $it") }
                document.getInfo("Actor")?.also { append("\n – Actor: $it") }
                document.getInfo("Series")?.also { append("\n – Series: $it") }
                document.getInfo("Maker")?.also { append("\n – Maker: $it") }
                document.getInfo("Director")?.also { append("\n – Director: $it") }
                document.getInfo("Label")?.also { append("\n – Label: $it") }
            }
            this.genre = document.getInfo("Genre:")
            this.status = SAnime.COMPLETED
            this.thumbnail_url = webCover
            // this.background_url = "https://fourhoi.com/$baseCode/preview.mp4"
            // this.update_strategy
            // this.fetch_type = FetchType.Episodes
            // this.season_number
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val takeHref = response.request.url.toString()
        val baseCode = takeHref.substringAfterLast("/")
        val episodes = mutableListOf<SEpisode>()

        // Se Extraen duration dateText y masterPlaylist.
        val duration = document.select("meta[property='og:video:duration']").attr("content").toInt()
        val dateText = document.select("time[datetime]").text().trim()
        val unpacker = document.select("script").find { it.html().contains("eval(function(p,a,c,k,e,d)") }
            ?.html()
            ?.let(Unpacker::unpack) ?: ""
        val masterPlaylist = getFirstMatch(Regex("""['"](https:.*?playlist\.m3u8)['"]"""), unpacker)
        episodes.add(
            SEpisode.create().apply {
                this.url = masterPlaylist ?: takeHref
                this.name = "Movie"
                this.date_upload = getDateLong("yyyy-MM-dd", dateText)
                this.episode_number = -1F
                // this.fillermark = false
                // this.scanlator = "MissAV"
                this.summary = "Duration: ${secondsToTime(duration)}"
                this.preview_url = "https://fourhoi.com/$baseCode/cover-t.jpg"
            },
        )
        // Retornamos la lista de Episodios
        return episodes.reversed()
    }

    // ============================ Hoster ========================================
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> = listOf(
        Hoster(
            hosterName = "MissAv",
            hosterUrl = episode.url,
        ),
    )

    override suspend fun getVideoList(hoster: Hoster): List<Video> = playlistExtractor.extractFromHls(
        playlistUrl = hoster.hosterUrl,
        referer = "$baseUrl/",
        videoNameGen = { quality -> "$quality" },
    ).sortVideos()

    // ============================== Filters ===============================
    override fun getFilterList() = getFilters()

    // ================================ Sorts ===============================

    /**
     * Standardized video sorting based on user preferences.
     */
    override fun List<Video>.sortVideos(): List<Video> {
        val prefQlty = preferences.getString("preferred_qlty", PREF_QLTY_DEFAULT)!!
        return this.map {
            Log.d(name, it.headers.toString())
            it.copy(
                preferred = it.videoTitle.contains(prefQlty, true),
            )
        }
    }

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_DOMAIN_KEY,
            title = PREF_DOMAIN_TITLE,
            entries = PREF_DOMAIN_ENTRIES,
            entryValues = PREF_DOMAIN_ENTRIES,
            default = PREF_DOMAIN_DEFAULT,
            summary = "%s",
            restartRequired = true,
        ) {
            baseUrl = it
            docHeaders = newHeaders()
            playlistExtractor = PlaylistUtils(client, docHeaders)
        }

        screen.addListPreference(
            key = PREF_QLTY_KEY,
            title = PREF_QLTY_TITLE,
            entries = PREF_QLTY_ENTRIES,
            entryValues = PREF_QLTY_VALUES,
            default = PREF_QLTY_DEFAULT,
            summary = "%s",
        )

        JavCoverFetcher.addPreferenceToScreen(screen)
    }

    // ========================= Funciones Auxiliares =======================
    // Obtiene una informacion del AV.
    private fun Element.getInfo(key: String): String? {
        val dataFound = select("span:containsOwn($key) ~ :is(time, span, a)")
            .joinToString(", ") { el ->
                el.text().trim()
            }.takeIf(String::isNotBlank)
        return dataFound
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

    // Arrelga el Titulo con etiquetas
    private fun getTitle(code: String, title: String = "", getCode: Boolean = false): String {
        val tags = mutableListOf<String>() // Lista mutable para acumular tags
        var codeClean: String = code
        listOf(
            "-ENGLISH-SUBTITLE" to "[SUB-EN]",
            "-CHINESE-SUBTITLE" to "[SUB-CH]",
            "-UNCENSORED-LEAK" to "[UNCENSORED]",
        ).forEach { (text, tag) ->
            if (code.contains(text, ignoreCase = true)) {
                tags.add(tag)
                codeClean = codeClean.replace(text, "", ignoreCase = true)
            }
        }
        return if (getCode) {
            codeClean
        } else {
            // Une todos los tags en una sola cadena
            "${tags.joinToString("")} $title".trim()
        }
    }

    // Armar headers con baseUrl
    private fun newHeaders(): Headers = headers.newBuilder().apply {
        set("Origin", baseUrl)
        set("Referer", "$baseUrl/")
        set("User-Agent", UA)
    }.build()

    // Obtener la primera key en una lista o Null
    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? = filterIsInstance<T>().firstOrNull()

    // Función para obtener el primer match de un regex
    fun getFirstMatch(pattern: Regex, input: String): String? = pattern.find(input)?.groupValues?.get(1)

    companion object {
        // Variables de baseUrl
        private const val PREF_DOMAIN_KEY = "preferred_domain"
        private const val PREF_DOMAIN_TITLE = "Preferred domain (requires app restart)"
        private val PREF_DOMAIN_ENTRIES = listOf("https://missav.live", "https://missav.ai", "https://missav.ws")
        private val PREF_DOMAIN_DEFAULT = PREF_DOMAIN_ENTRIES.first()

        // Variables de Calidad
        private const val PREF_QLTY_KEY = "preferred_quality"
        private const val PREF_QLTY_TITLE = "Preferred quality"
        private val PREF_QLTY_VALUES = listOf("720", "480", "360")
        private val PREF_QLTY_ENTRIES = listOf("720p", "480p", "360p")
        private val PREF_QLTY_DEFAULT = PREF_QLTY_VALUES.first()

        //
        private val UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.3 Aniyomi/0.15.3"
    }
}

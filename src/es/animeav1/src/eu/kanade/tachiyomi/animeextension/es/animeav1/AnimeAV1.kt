package eu.kanade.tachiyomi.animeextension.es.animeav1

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.lpayerextractor.LpayerExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.pixeldrainextractor.PixelDrainExtractor
import eu.kanade.tachiyomi.lib.streamwishextractor.StreamWishExtractor
import eu.kanade.tachiyomi.lib.universalextractor.UniversalExtractor
import eu.kanade.tachiyomi.lib.vidhideextractor.VidHideExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.Source
import keiyoushi.utils.parallelCatchingFlatMapBlocking
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.plus
import kotlin.collections.set

class AnimeAV1 : Source() {
    override val name = "AnimeAV1"
    override val baseUrl = "https://animeav1.com"
    override val lang = "es"
    override val supportsLatest = true
    // override val id: Long = 2168637495373172929L

    // ============================== Popular ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/catalogo?order=popular&page=$page", headers)
    override fun popularAnimeParse(response: Response): AnimesPage = searchAnimeParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/catalogo?order=latest_released&page=$page", headers)
    override fun latestUpdatesParse(response: Response): AnimesPage = searchAnimeParse(response)

    // ============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnimeAv1Filters.getSearchParameters(filters)
        return when {
            query.isNotBlank() -> GET("$baseUrl/catalogo?search=$query&page=$page", headers)

            params.filter.isNotBlank() -> GET(
                "$baseUrl/catalogo${params.getQuery().run {
                    if (isNotBlank()) "$this&page=$page" else this
                }}",
                headers,
            )

            else -> popularAnimeRequest(page)
        }
    }
    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val nextPage = document.select("a[href*='page=']:last-child:contains(»)").any()
        val animeList = document.select("article[class*='group/item']").map {
            val url = it.selectFirst("a[href*='/media']")!!.attr("href")
            SAnime.create().apply {
                this.url = baseUrl + url
                this.title = it.selectFirst("h3, div.text-subs.uppercase")!!.text()
                this.thumbnail_url = it.selectFirst("img")?.attr("abs:src")
                // this.fetch_type = FetchType.Episodes
            }
        }
        return AnimesPage(animeList, nextPage)
    }

    // =========================== Anime Details ============================
    override fun animeDetailsRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: ""
        // Recolectamos la Información de la Película/Anime/OVA
        val animeJson = """(?s)data:(\{media:.*\}),uses""".getFirstMatch(scriptContent)
            ?.parseAs<AnimeAV1Data>()?.media ?: return SAnime.create()
        val aniZipRes = aniZipData(animeJson.malId)
        return SAnime.create().apply {
            this.title = animeJson.title ?: aniZipRes.titles?.get("x-jat")!!
            // this.artist
            // this.author
            this.description = buildString {
                append("${animeJson.synopsis ?: "Sin descripción disponible."}\n")
                // Extra info extraction for description
                animeJson.startDate?.also { append("\n – Se Estreno: $it") }
                animeJson.endDate?.also { append("\n – Finalizo En: $it") }
                document.selectFirst("span:contains(Temporada)")?.text().also { append("\n – $it") }
                animeJson.category?.also { append("\n – Tipo de Anime: ${it.name}") }
                animeJson.malId?.also { append("\n – MyAnimeList ID: $it") }
                animeJson.score?.also { append("\n – Calificacion: $it") }
            }
            this.genre = animeJson.genres?.joinToString(", ") { it.name!! }
            this.status = when (animeJson.status) {
                0 -> SAnime.COMPLETED
                1, 2 -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
            this.thumbnail_url = aniZipRes.images?.find { it?.coverType == "Poster" }?.url
                ?: "https://cdn.animeav1.com/covers/${animeJson.id}.jpg"
            this.background_url = aniZipRes.images?.find { it?.coverType == "Fanart" }?.url
                ?: "https://cdn.animeav1.com/backdrops/${animeJson.id}.jpg"
            // this.update_strategy
            // this.fetch_type = FetchType.Episodes
            // this.season_number
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: ""
        // Recolectamos la Información de la Película/Anime/OVA
        val animeJson = """(?s)data:(\{media:.*\}),uses""".getFirstMatch(scriptContent)
            ?.parseAs<AnimeAV1Data>()?.media ?: return emptyList()
        val aniZipRes = aniZipData(animeJson.malId)
        val episodes = mutableListOf<SEpisode>()
        val epStart = animeJson.episodes?.firstOrNull()?.number ?: 1
        val epCount = animeJson.episodesCount ?: 1

        // Se Extraen los Episodios de MediaInfo.
        if (animeJson.episodes != null) {
            for (i in epStart..epCount) {
                val aniDb = aniZipRes.episodes?.get("$i")
                val index = if (epStart == 0) i + 1 else i
                val title = aniDb?.title?.get("x-jat") ?: aniDb?.title?.get("en")
                episodes.add(
                    SEpisode.create().apply {
                        this.url = "$baseUrl/media/${animeJson.slug}/$i"
                        this.name = "E$index - $title"
                        this.date_upload = getDateLong("yyyy-MM-dd", aniDb?.airDate)
                        this.episode_number = i.toFloat()
                        // this.fillermark = false
                        // this.scanlator = ""
                        // this.summary = ""
                        this.preview_url = aniDb?.image
                            ?: "https://cdn.animeav1.com/screenshots/${animeJson.id}/$i.jpg"
                    },
                )
            }
        }
        // Retornamos la lista de Episodios
        return episodes.reversed()
    }

    // =============================== Video ================================
    override fun videoListRequest(episode: SEpisode): Request = GET(absUrl(episode.url), headers)
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: ""

        val animeData = """(?s)data:(\{media:.*\}),uses""".getFirstMatch(scriptContent)
            ?.parseAs<AnimeAV1Data>() ?: return emptyList()
        val videoList = mutableListOf<Video>()
        val allEmbeds = mutableMapOf<String, List<EmbedInfo>>()

        // 1. Extraer urls de 'embeds' y 'downloads' dinámicamente
        listOfNotNull(animeData.embeds, animeData.downloads).forEach { container ->
            container.forEach { (category, items) ->
                val currentList = allEmbeds[category] ?: emptyList()
                val newItems = items.mapNotNull { it.toEmbedInfo() }
                // Filtrar URL duplicadas usando distinctBy
                allEmbeds[category] = (currentList + newItems).distinctBy { it.url }
            }
        }
        allEmbeds.map { (lang, embdes) ->
            videoList.addAll(
                embdes.parallelCatchingFlatMapBlocking {
                    serverVideoResolver(it.url, lang, it.server)
                },
            )
        }
        return videoList.sort()
    }

    // ============================== Filters ===============================
    override fun getFilterList() = AnimeAv1Filters.FILTER_LIST

    // ============================= Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_lang"
            title = "Idioma Preferido"
            entries = LANGUAGES_DISPLAY
            entryValues = LANGUAGES_VALUES
            setDefaultValue(LANGNGUAGE_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_server"
            title = "Servidor Preferido"
            entries = SERVERS
            entryValues = SERVERS
            setDefaultValue(SERVER_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Calidad Preferida"
            entries = QUALITIES
            entryValues = QUALITIES
            setDefaultValue(QUALITY_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    // ========================= Funciones Auxiliares =======================
    // Extractores.
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val lpayerExtractor by lazy { LpayerExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client, headers) }
    private val universalExtractor by lazy { UniversalExtractor(client) }
    private val pixelDrainExtractor by lazy { PixelDrainExtractor(client) }
    private val streamWishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val yourUploadExtractor by lazy { YourUploadExtractor(client) }
    private suspend fun serverVideoResolver(url: String, prefix: String = "", serverName: String? = ""): List<Video> {
        val pdrain = listOf("pdrain", "pixeldrain")
        val pzilla = listOf("player.zilla", "hls")
        val vidhide = listOf("streamhide", "streamvid", "vidhide", "minochinos", "earnvids")
        return runCatching {
            val source = (serverName?.ifEmpty { url } ?: url).lowercase()
            when {
                pdrain.any { it in source } -> pixelDrainExtractor.videosFromUrl(url, "$prefix ")

                pzilla.any { it in source } -> {
                    val m3u8 = url.replace("play/", "m3u8/")
                    listOf(Video(videoTitle = "$prefix HLS:1080p", videoUrl = m3u8))
                }

                vidhide.any { it in source } -> vidHideExtractor.videosFromUrl(url) { "$prefix VidHide:$it" }

                "voe" in source -> voeExtractor.videosFromUrl(url, "$prefix ")

                "upnshare" in source -> lpayerExtractor.videosFromUrl(url, prefix = "$prefix UPNShare:")

                "mp4upload" in source -> mp4uploadExtractor.videosFromUrl(url) { "$prefix MP4Upload:$it" }

                "streamwish" in source -> streamWishExtractor.videosFromUrl(url) { "$prefix StreamWish:$it" }

                "yourupload" in source -> yourUploadExtractor.videoFromUrl(url, headers = headers, prefix = "$prefix ")

                else -> universalExtractor.videosFromUrl(url, headers, prefix = "$prefix ")
            }
        }.getOrNull() ?: emptyList()
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
                { Regex("""(\d+)p""").find(it.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    // Obtenert una URL absoluta usando baseUrl.
    private fun absUrl(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> baseUrl + path
        else -> path
    }

    // Obtiene Meta datos de AniZip
    private fun aniZipData(id: Int?): AniZipResponse {
        if (id == null) return AniZipResponse()
        return try {
            val response = client.newCall(GET("https://api.ani.zip/mappings?mal_id=$id")).execute()
            response.body.string().parseAs<AniZipResponse>()
        } catch (_: Exception) {
            AniZipResponse()
        }
    }

    // Limpia un string de JS para convertirlo en JSON válido.
    private fun cleanJsToJson(js: String): String = js
        .replace("void 0", "null")
        .replace(Regex("""(?<=[{,])\s*(\w+)\s*:""")) {
            "\"${it.groupValues[1]}\":"
        }.trim()

    // Obtener DateTime en Long
    private fun getDateLong(format: String, date: String?, locale: Locale = Locale.getDefault()): Long {
        if (date == null) return 0L
        val dateFormat = SimpleDateFormat(format, locale)
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // Función para obtener el primer match de un regex
    private fun String.getFirstMatch(input: String): String? {
        val foundMatch = Regex(this).find(input)?.groupValues?.get(1)
        return if (foundMatch !== null) {
            cleanJsToJson(foundMatch)
        } else {
            null
        }
    }

    companion object {
        // Elementos divididos apropiadamente en lugar de un string aglomerado
        private val QUALITIES = arrayOf("1080p", "720p", "480p", "360p")
        private const val QUALITY_DEFAULT = "720p"

        private val SERVERS = arrayOf(
            "PixelDrain",
            "StreamWish",
            "YourUpload",
            "MP4Upload",
            "UPNShare",
            "VidHide",
            "Voe",
            "HLS",
        )
        private const val SERVER_DEFAULT = "HLS"

        private val LANGUAGES_VALUES = arrayOf("SUB", "", "DUB")
        private val LANGUAGES_DISPLAY = arrayOf("SUB", "All", "DUB")
        private const val LANGNGUAGE_DEFAULT = "SUB"
    }
}

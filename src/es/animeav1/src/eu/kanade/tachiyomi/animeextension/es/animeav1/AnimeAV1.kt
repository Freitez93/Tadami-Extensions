package eu.kanade.tachiyomi.animeextension.es.animeav1

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
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
import okhttp3.Request
import okhttp3.Response

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
                this.fetch_type = FetchType.Episodes
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
        val mediaInfo = getMediaInfo(scriptContent) ?: return SAnime.create()
        return SAnime.create().apply {
            this.title = mediaInfo.title ?: document.select("h1[class*=text-lead]").text()
            // this.artist
            // this.author
            this.description = mediaInfo.synopsis
            this.genre = mediaInfo.genre?.joinToString(", ")
            this.status = mediaInfo.status
            this.thumbnail_url = "https://cdn.animeav1.com/covers/${mediaInfo.mediaID}.jpg"
            this.background_url = "https://cdn.animeav1.com/backdrops/${mediaInfo.mediaID}.jpg"
            // this.update_strategy
            this.fetch_type = FetchType.Episodes
            // this.season_number

            // Extra Info.
            val extraMeta = document.select("div:contains(Temporada)").text().split("•")
            val extraInfo = getExtraInfo(mediaInfo, extraMeta)
            this.description = (description + extraInfo).trim()
        }
    }

    // ============================== Episodes ==============================
    override fun episodeListRequest(anime: SAnime): Request = GET(anime.url, headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(__sveltekit)")?.data() ?: ""
        // Recolectamos la Información de la Película/Anime/OVA
        val mediaInfo = getMediaInfo(scriptContent)
        val episodes = mutableListOf<SEpisode>()
        val epStart = mediaInfo?.startEpisode ?: 1
        val epCount = mediaInfo?.episodesCount ?: 1

        // Se Extraen los Episodios de MediaInfo.
        if (mediaInfo != null) {
            for (i in epStart..epCount) {
                val epUrl = "$baseUrl/media/${mediaInfo.slug}/$i"
                episodes.add(
                    SEpisode.create().apply {
                        setUrlWithoutDomain(epUrl)
                        this.name = "Episode ${if (epStart == 0) i + 1 else i}"
                        this.date_upload = 0
                        this.episode_number = i.toFloat()
                        // this.fillermark = false
                        // this.scanlator = ""
                        // this.summary = ""
                        this.preview_url = "https://cdn.animeav1.com/screenshots/${mediaInfo.mediaID}/$i.jpg"
                    },
                )
            }
        }
        // Retornamos la lista de Episodios
        return episodes.reversed()
    }

    // =============================== Video ================================
    override fun videoListRequest(episode: SEpisode): Request = GET(episode.url, headers)
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(__sveltekit)")?.data()
            ?: return emptyList()
        val mediaData = getMediaInfo(script) ?: return emptyList()
        val videoList = mutableListOf<Video>()
        mediaData.embeds?.map { (lang, embdes) ->
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
            key = "preferred_quality"
            title = "Calidad Preferida"
            entries = QUALITIES
            entryValues = QUALITIES
            setDefaultValue(QUALITY_DEFAULT)
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
    }

    //
    // ========================= Funciones Auxiliares =======================
    private fun getExtraInfo(meta: MediaDateJSON, extra: List<String>): String {
        var output = "\n\n – Fecha de inicio: ${meta.startDate}"
        output += "\n – ${extra[2].trim()}"
        output += "\n – Tipo de Anime: ${meta.type}"
        output += "\n – MyAnimeList ID: ${meta.malId}"
        output += "\n – Calificacion: ${meta.score}"
        return output
    }

    // Extractores.
    private val voeExtractor by lazy { VoeExtractor(client, headers) }
    private val lpayerExtractor by lazy { LpayerExtractor(client) }
    private val vidHideExtractor by lazy { VidHideExtractor(client, headers) }
    private val mp4uploadExtractor by lazy { Mp4uploadExtractor(client) }
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
                    listOf(Video(videoTitle = "$prefix HLS", videoUrl = m3u8))
                }

                vidhide.any { it in source } -> vidHideExtractor.videosFromUrl(url, videoNameGen = { "$prefix VidHide:$it" })

                "voe" in source -> voeExtractor.videosFromUrl(url, "$prefix ")

                "upnshare" in source -> lpayerExtractor.videosFromUrl(url, prefix = "$prefix UPNShare:")

                "mp4upload" in source -> mp4uploadExtractor.videosFromUrl(url, headers, prefix = "$prefix ")

                "streamwish" in source -> streamWishExtractor.videosFromUrl(url, videoNameGen = { "$prefix StreamWish:$it" })

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
                { it.videoTitle.contains(quality) },
                { it.videoTitle.contains(server, true) },
                { Regex("""(\d+)p""").find(it.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
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

package eu.kanade.tachiyomi.animeextension.es.animeav1

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AniZipResponse(
    val titles: Map<String, String?>? = null,
    val episodes: Map<String, AniZipEpisode?>? = null,
    val episodeCount: Int? = null,
    val specialCount: Int? = null,
    val images: List<AniZipImage?>? = null,
    val mappings: AniZipMappings? = null,
)

@Serializable
data class AniZipEpisode(
    val episode: String? = null,
    val episodeNumber: Int? = null,
    val absoluteEpisodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val title: Map<String, String?>? = null,
    val length: Int? = null,
    val runtime: Int? = null,
    @SerialName("airdate")
    val airDate: String? = null,
    val rating: String? = null,
    @SerialName("anidbEid")
    val aniDbEpisodeId: Long? = null,
    val tvdbShowId: Long? = null,
    val tvdbId: Long? = null,
    val overview: String? = null,
    val image: String? = null,
)

@Serializable
data class AniZipImage(
    val coverType: String? = null,
    val url: String? = null,
)

@Serializable
data class AniZipMappings(
    @SerialName("animeplanet_id")
    val animePlanetId: String? = null,
    @SerialName("kitsu_id")
    val kitsuId: Long? = null,
    @SerialName("mal_id")
    val myAnimeListId: Long? = null,
    val type: String? = null,
    @SerialName("anilist_id")
    val aniListId: Long? = null,
    @SerialName("anisearch_id")
    val aniSearchId: Long? = null,
    @SerialName("anidb_id")
    val aniDbId: Long? = null,
    @SerialName("notifymoe_id")
    val notifyMoeId: String? = null,
    @SerialName("livechart_id")
    val liveChartId: Long? = null,
    @SerialName("thetvdb_id")
    val theTvDbId: Long? = null,
    @SerialName("imdb_id")
    val imdbId: String? = null,
    @SerialName("themoviedb_id")
    val theMovieDbId: String? = null,
)

// -------------------------------------//
//               DTO AnimeAV1           //
// ------------------------------------ //

@Serializable
data class AnimeAV1Response(
    val type: String? = null,
    val data: AnimeAV1Data? = null,
)

@Serializable
data class AnimeAV1Data(
    val media: AnimeAV1Media? = null,
    val episode: AnimeAV1Episode? = null,
    val embeds: Map<String, List<AnimeAV1Embed>>? = null,
    val downloads: Map<String, List<AnimeAV1Embed>>? = null,
)

@Serializable
data class AnimeAV1Media(
    val id: Int? = null,
    val categoryId: Int? = null,
    val title: String? = null,
    val aka: Map<String, String>? = null,
    val genres: List<AnimeAV1Genre>? = null,
    val synopsis: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val trailer: String? = null,
    val status: Int? = null,
    val runtime: Int? = null,
    val startDate: String? = null,
    val nextDate: String? = null,
    val endDate: String? = null,
    val waitDays: Int? = null,
    val featured: Boolean? = null,
    val mature: Boolean? = null,
    val episodesCount: Int? = null,
    val score: Double? = null,
    val votes: Int? = null,
    val slug: String? = null,
    val malId: Int? = null,
    val category: AnimeAV1Category? = null,
    val episodes: List<AnimeAV1EpisodeSimple>? = null,
    val relations: List<AnimeAV1Relation>? = null,
)

@Serializable
data class AnimeAV1Genre(
    val id: Int? = null,
    val name: String? = null,
    val type: Int? = null,
    val slug: String? = null,
    val malId: Int? = null,
)

@Serializable
data class AnimeAV1Category(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class AnimeAV1EpisodeSimple(
    val id: Int? = null,
    val number: Int? = null,
)

@Serializable
data class AnimeAV1Episode(
    val id: Int? = null,
    val mediaId: Int? = null,
    val title: String? = null,
    val number: Int? = null,
    val season: Int? = null,
    val filler: Boolean? = null,
    val publishedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class AnimeAV1Relation(
    val type: Int? = null,
    val destination: AnimeAV1Destination? = null,
)

@Serializable
data class AnimeAV1Destination(
    val id: Int? = null,
    val slug: String? = null,
    val title: String? = null,
    val startDate: String? = null,
)

@Serializable
data class AnimeAV1Embed(
    val server: String? = null,
    val url: String? = null,
) {
    fun toEmbedInfo(): EmbedInfo? {
        if (url.isNullOrBlank()) return null
        val finalUrl = if ("pixeldrain" in url) url.replace("?embed", "") else url
        return EmbedInfo(
            server = server ?: "Unknown",
            url = finalUrl,
        )
    }
}

data class EmbedInfo(
    val server: String,
    val url: String,
)

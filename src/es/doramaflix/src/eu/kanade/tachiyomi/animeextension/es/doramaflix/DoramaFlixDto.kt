package eu.kanade.tachiyomi.animeextension.es.doramaflix

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================
// ===== MODELOS DE DATOS (SERIALIZABLE) =====
// ============================================

// --------------------------
// Doramas, Peliculas
// --------------------------

@Serializable
data class SearchWrapper(
    val data: PaginationModel = PaginationModel(),
)

@Serializable
data class PaginationModel(
    val paginationDorama: PaginationDorama? = null,
    val paginationMovie: PaginationDorama? = null,
    val searchDorama: List<Item> = emptyList(),
    val searchMovie: List<Item> = emptyList(),
) {
    // Propiedad computada que devuelve el valor List<Item>
    val items: List<Item> get() = paginationDorama?.items
        ?: paginationMovie?.items
        ?: (searchDorama + searchMovie)

    // Propiedad computada que devuelve el valor PageInfo
    val pagination: PageInfo? get() = paginationDorama?.pageInfo ?: paginationMovie?.pageInfo
}

@Serializable
data class PaginationDorama(
    val count: Long = 0,
    val pageInfo: PageInfo,
    val items: List<Item> = emptyList(),
    @SerialName("__typename")
    val typename: String? = null,
)

@Serializable
data class PageInfo(
    val currentPage: Int,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    @SerialName("__typename")
    val typename: String? = null,
)

@Serializable
data class Item(
    @SerialName("_id")
    val id: String,
    val slug: String,
    val name: String,
    val names: String?,
    @SerialName("name_es")
    val nameEs: String?,
    @SerialName("poster_path")
    val posterPath: String?,
    val poster: String? = null,
    val isTVShow: Boolean? = null,
    val rating: Float = 0F,
    @SerialName("tmdb_id")
    val tmdbID: Int = 0,
    val languages: List<String> = emptyList(),
    @SerialName("__typename")
    val typename: String,
) {
    // Función que recibe el tamaño y devuelve la URL de la portada
    fun getPoster(size: String = "w500"): String = poster?.takeIf {
        it.isNotEmpty()
    } ?: "https://image.tmdb.org/t/p/$size$posterPath"
}

// --------------------------
// Detalles de Dorama y Película
// --------------------------
@Serializable
data class DetailWrapper(
    val data: PaginationDetail = PaginationDetail(),
)

@Serializable
data class PaginationDetail(
    val detailMovie: DoramaDetail? = null,
    val detailDorama: DoramaDetail? = null,
) {
    // Propiedad computada que devuelve la información de Movie o Dorama
    val getDetail: DoramaDetail? get() = detailMovie ?: detailDorama
}

@Serializable
data class DoramaDetail(
    @SerialName("_id")
    val id: String,
    val slug: String,
    val name: String,
    val names: String?,
    @SerialName("name_es")
    val nameEs: String?,
    val overview: String?,
    val languages: List<String> = emptyList(),
    val country: String = "Unknown",
    val popularity: Float = 0F,
    val rating: Float = 0F,
    @SerialName("tmdb_id")
    val tmdbID: Int = 0,
    val poster: String?,
    val backdrop: String?,
    @SerialName("poster_path")
    val posterPath: String?,
    @SerialName("backdrop_path")
    val backdropPath: String?,
    @SerialName("first_air_date")
    val firstAirDate: String?,
    @SerialName("last_air_date")
    val lastAirDate: String?,
    @SerialName("release_date")
    val releaseDate: String?,
    val runtime: Int?,
    val status: String?,
    val premiere: Boolean = false,
    val isFinish: Boolean = true,
    @SerialName("number_of_seasons")
    val numberOfSeasons: Int? = null,
    @SerialName("created_by")
    val createdBy: List<CreatedBy> = emptyList(),
    val cast: List<Cast> = emptyList(),
    val genres: List<GLN> = emptyList(),
    val labels: List<GLN> = emptyList(),
    val networks: List<GLN> = emptyList(),
    @SerialName("__typename")
    val typename: String,
) {
    // Propiedad computada que devuelve el valor firstAirDate o releaseDate
    // first_air_date, last_air_date y release_date tienen un formato de fecha: "yyyy-MM-dd"
    val getRelease: String get() = firstAirDate ?: releaseDate ?: "---"

    // Propiedad computada que devuelve un string stylo 1h 23m de 123m
    val getDuration: String? get() = runtime?.let {
        "${it / 60}h ${it % 60}m"
    }

    // Propiedad computada que reemplaza country la Primera letra en Mayuscula.
    val getCountry: String get() = country.replaceFirstChar { it.uppercase() }

    // Propiedad computada que devuelve el valor firstAirDate o releaseDate
    val getLanguage: List<String> get() = languages.map {
        when (it) {
            "36" -> "Inglés"
            "37" -> "Castellano"
            "38" -> "Latino"
            "192" -> "Subtitulado"
            "1327" -> "Portugués"
            "13109" -> "Coreano"
            "13110" -> "Japones"
            "13111" -> "Mandarín"
            "13112" -> "Tailandes"
            "13113" -> "Filipino"
            "13114" -> "Indones"
            "343422" -> "Vietnamita"
            else -> "Unknown"
        }
    }

    // Función que recibe el tamaño y devuelve la URL de la portada
    fun getPoster(size: String = "w500"): String = poster?.takeIf { it.isNotEmpty() }
        ?: "https://image.tmdb.org/t/p/$size$posterPath"

    // Función que recibe el tamaño y devuelve la URL de la portada
    fun getBackdrop(size: String = "w500"): String = backdrop?.takeIf { it.isNotEmpty() }
        ?: "https://image.tmdb.org/t/p/$size$backdropPath"
}

// --------------------------
// Created_by
// --------------------------
@Serializable
data class CreatedBy(
    val name: String,
    @SerialName("original_name")
    val originalName: String?,
    @SerialName("profile_path")
    val profilePath: String?,
    val job: String?,
)

// --------------------------
// Cast
// --------------------------

@Serializable
data class Cast(
    val name: String,
    @SerialName("profile_path")
    val profilePath: String?,
    val character: String?,
)

// --------------------------
// Genres - Labels - Networks
// --------------------------
@Serializable
data class GLN(
    @SerialName("_id")
    val id: String?,
    val name: String,
    val slug: String,
    @SerialName("__typename")
    val typename: String?,
)
// --------------------------
// Episodios
// --------------------------

@Serializable
data class PaginationEpisodeWrapper(
    val paginationEpisode: PaginationEpisode? = null,
    val detailMovie: DoramaDetail? = null,
) {
    // Propiedad computada que devuelve la fecha de estreno.
    val getItems: List<Episode>? get() = paginationEpisode?.items
}

@Serializable
data class PaginationEpisode(
    val items: List<Episode>? = emptyList(),
)

@Serializable
data class Episode(
    @SerialName("_id")
    val id: String,
    val slug: String,
    val name: String,
    @SerialName("name_es")
    val nameEs: String?,
    @SerialName("serie_id")
    val serieId: String,
    @SerialName("serie_name")
    val serieName: String,
    @SerialName("serie_name_es")
    val serieNameEs: String?,
    @SerialName("still_path")
    val stillPath: String = "",
    @SerialName("season_number")
    val seasonNumber: Int = 0,
    @SerialName("episode_number")
    val episodeNumber: Int = 0,
    val overview: String?,
    @SerialName("date_string")
    val dateString: String?,
    @SerialName("air_date")
    val airDate: String?,
    @SerialName("__typename")
    val typename: String,
) {
    companion object {
        private val EPISODE_REGEX = Regex("""Episodio|\dx\d{1,2}""")
    }

    // Propiedad computada que devuelve el valor de Name si no es un nombre generico.
    val getName: String get() = if (name.contains(EPISODE_REGEX)) {
        "S$seasonNumber:E$episodeNumber - Episodio $episodeNumber"
    } else {
        "S$seasonNumber:E$episodeNumber - $name"
    }

    // Propiedad computada que devuelve la fecha de estreno.
    val getRelease: String? get() = dateString?.takeIf(String::isNotBlank) ?: airDate

    // Función que recibe el tamaño y devuelve la URL de la portada
    fun getPreview(size: String = "w500"): String = when {
        stillPath.startsWith("/") -> "https://image.tmdb.org/t/p/$size$stillPath"
        stillPath.startsWith("https:") -> stillPath.replace("/original/", "/$size/")
        else -> ""
    }
}

// --------------------------
// Enlaces de Episodio
// --------------------------
@Serializable
data class PaginationLinkOnlineWrapper(
    val detailEpisode: PaginationLinkOnline? = null,
    val detailMovie: PaginationLinkOnline? = null,
) {
    val getLinkOnline: List<LinkOnline> get() = detailEpisode?.linksOnline
        ?: detailMovie?.linksOnline
        ?: emptyList()
}

@Serializable
data class PaginationLinkOnline(
    @SerialName("links_online")
    val linksOnline: List<LinkOnline> = emptyList(),
)

@Serializable
data class LinkOnline(
    val server: String,
    val lang: String,
    val page: String,
    val link: String?,
    val embed: String?,
    @SerialName("subtitle_mode")
    val subtitleMode: String?,
    val subtitles: List<Subtitle> = emptyList(),
) {
    // Propiedad computada que devuelve el nombre en string del idioma
    val getLanguage: String get() = when (lang) {
        "36" -> "Inglés"
        "37" -> "Castellano"
        "38" -> "Latino"
        "192" -> "Subtitulado"
        "1327" -> "Portugués"
        "13109" -> "Coreano"
        "13110" -> "Japones"
        "13111" -> "Mandarín"
        "13112" -> "Tailandes"
        "13113" -> "Filipino"
        "13114" -> "Indones"
        "343422" -> "Vietnamita"
        else -> lang
    }

    // Propiedad computada que devuelve el nombre en string del servidor
    val getNameServer: String get() = when (server) {
        "0807" -> "Streamlare"
        "0808" -> "VIP"
        "1113" -> "Ok"
        "1230" -> "Voe"
        "1233" -> "Uqload"
        "1234" -> "Mp4Upload"
        "1237" -> "ED HD"
        "1238" -> "ED Movil"
        "1239" -> "StreamSB"
        "1999" -> "HD+"
        "2671" -> "Mystream"
        "3889" -> "Mixdrop"
        "4721" -> "Primeload"
        "7286" -> "Dood"
        "8309" -> "Streamtape"
        "14463" -> "UpStream"
        "38585" -> "Streamwish"
        "55532" -> "Vudeo"
        "85740" -> "Dailymotion"
        "958695" -> "Filemoon"
        "453634" -> "Mega"
        "576857" -> "VidHide"
        "765758" -> "Amazon"
        "754645645" -> "Wolfstream"
        else -> server
    }
}

@Serializable
data class Subtitle(
    @SerialName("language_code")
    val languageCode: String,
    val type: String?,
)

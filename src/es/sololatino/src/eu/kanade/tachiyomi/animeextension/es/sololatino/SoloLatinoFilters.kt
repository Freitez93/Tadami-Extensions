package eu.kanade.tachiyomi.animeextension.es.sololatino

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object SoloLatinoFilters {
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified R> AnimeFilterList.getFirst(): R = first { it is R } as R

    private inline fun <reified R> AnimeFilterList.asUriPart(): String = getFirst<R>().let {
        (it as UriPartFilter).toUriPart()
    }

    class PlatformFilter : UriPartFilter("Plataformas", AnimesOnlineNinjaData.PLATFORMS)
    class TypeFilter : UriPartFilter("Tipo", AnimesOnlineNinjaData.TYPES)
    class GenreFilter : UriPartFilter("Generos", AnimesOnlineNinjaData.GENRES)
    class YearFilter : UriPartFilter("Año", AnimesOnlineNinjaData.YEARS)
    class NoteFilter : UriPartFilter("Nota", AnimesOnlineNinjaData.NOTE)
    class SortFilter : UriPartFilter("Ordenar", AnimesOnlineNinjaData.SORT)

    class OtherOptionsGroup :
        AnimeFilter.Group<UriPartFilter>(
            "Otros filtros",
            listOf(
                GenreFilter(),
                YearFilter(),
                NoteFilter(),
                SortFilter(),
            ),
        )

    private inline fun <reified R> AnimeFilter.Group<UriPartFilter>.getItemUri(): String = state.first { it is R }.toUriPart()

    val FILTER_LIST get() = AnimeFilterList(
        PlatformFilter(),
        TypeFilter(),
        AnimeFilter.Separator(),
        AnimeFilter.Header("Estos filtros afectan a la busqueda por texto"),
        OtherOptionsGroup(),
    )

    data class FilterSearchParams(
        val platform: String = "",
        val type: String = "",
        val genre: String = "",
        val year: String = "0",
        val note: String = "0",
        val sort: String = "",
    )

    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()
        val others = filters.getFirst<OtherOptionsGroup>()
        return FilterSearchParams(
            filters.asUriPart<PlatformFilter>(),
            filters.asUriPart<TypeFilter>(),
            others.getItemUri<GenreFilter>(),
            others.getItemUri<YearFilter>(),
            others.getItemUri<NoteFilter>(),
            others.getItemUri<SortFilter>(),
        )
    }

    private object AnimesOnlineNinjaData {
        val EVERY = Pair("<Seleccionar>", "")
        val EVERYZERO = Pair("<Seleccionar>", "0")

        val PLATFORMS = arrayOf(
            EVERY,
            Pair("Netflix", "netflix"),
            Pair("Amazon Prime Video", "amazon-prime-video"),
            Pair("Tokyo Mx", "tokyo-mx"),
            Pair("Disney+", "disney"),
            Pair("Apple TV+", "apple-tv"),
            Pair("Tv Tokyo", "tv-tokyo"),
            Pair("At-X", "at-x"),
            Pair("Hulu", "hulu"),
            Pair("HBO Max", "hbo-max"),
            Pair("HBO", "hbo"),
            Pair("Bs11", "bs11"),
            Pair("Nbc", "nbc"),
        )

        val TYPES = arrayOf(
            Pair("Tipo: Todo", ""),
            Pair("Películas", "peliculas"),
            Pair("Series", "series"),
            Pair("Animes", "animes"),
            Pair("Doramas", "doramas"),
        )

        val GENRES = arrayOf(
            Pair("Género: Todos", ""),
            Pair("Acción", "accion"),
            Pair("Action & Adventure", "action-adventure"),
            Pair("Animación", "animacion"),
            Pair("Anime", "anime"),
            Pair("Aventura", "aventura"),
            Pair("Bélica", "belica"),
            Pair("Ciencia Ficción", "ciencia-ficcion"),
            Pair("Comedia", "comedia"),
            Pair("Crimen", "crimen"),
            Pair("Documental", "documental"),
            Pair("Drama", "drama"),
            Pair("Familia", "familia"),
            Pair("Fantasía", "fantasia"),
            Pair("Historia", "historia"),
            Pair("Kids", "kids"),
            Pair("Misterio", "misterio"),
            Pair("Música", "musica"),
            Pair("News", "news"),
            Pair("Película De Tv", "pelicula-de-tv"),
            Pair("Reality", "reality"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi & Fantasy", "sci-fi-fantasy"),
            Pair("Soap", "soap"),
            Pair("Suspense", "suspense"),
            Pair("Talk", "talk"),
            Pair("Terror", "terror"),
            Pair("War & Politics", "war-politics"),
            Pair("Western", "western"),
        )

        val YEARS = arrayOf(EVERYZERO) + (2026 downTo 1979).map {
            Pair(it.toString(), it.toString())
        }.toTypedArray()

        val NOTE = arrayOf(
            Pair("Nota: Todas", "0"),
            Pair("9+ Obra maestra", "9"),
            Pair("8+ Excelente", "8"),
            Pair("7+ Muy buena", "7"),
            Pair("6+ Buena", "6"),
            Pair("5+ Regular", "5"),
        )

        val SORT = arrayOf(
            Pair("Más populares", "popularidad"),
            Pair("Más recientes", "fecha"),
            Pair("Mejor nota", "nota"),
            Pair("Año: nuevo a viejo", "año-desc"),
            Pair("Año: viejo a nuevo", "año-asc"),
            Pair("A-Z", "az"),
        )
    }
}

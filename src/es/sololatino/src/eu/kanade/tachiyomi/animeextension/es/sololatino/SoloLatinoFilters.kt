package eu.kanade.tachiyomi.animeextension.es.sololatino

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Filtros para el catálogo de SoloLatino.
 * Proporciona una interfaz estructurada para manejar los filtros de búsqueda del sitio.
 */
object SoloLatinoFilters {

    /**
     * Filtro de selección simple que asocia un nombre visible con un valor para la URL.
     */
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // --- Filtros Individuales ---
    class PlatformFilter : UriPartFilter("Plataformas", Data.PLATFORMS)
    class TypeFilter : UriPartFilter("Tipo", Data.TYPES)
    class GenreFilter : UriPartFilter("Géneros", Data.GENRES)
    class YearFilter : UriPartFilter("Año", Data.YEARS)
    class NoteFilter : UriPartFilter("Nota", Data.NOTE)
    class SortFilter : UriPartFilter("Ordenar", Data.SORT)

    /**
     * Estructura de la lista de filtros que se muestra en la aplicación.
     */
    val FILTER_LIST get() = AnimeFilterList(
        PlatformFilter(),
        TypeFilter(),
        AnimeFilter.Separator(),
        AnimeFilter.Header("Estos filtros afectan a la búsqueda por texto"),
        GenreFilter(),
        YearFilter(),
        NoteFilter(),
        SortFilter(),
    )

    /**
     * Clase de datos para transportar los valores seleccionados en los filtros.
     */
    data class FilterSearchParams(
        val platform: String = "",
        val type: String = "",
        val genre: String = "",
        val year: String = "0",
        val note: String = "0",
        val sort: String = "popularidad",
    )

    /**
     * Extrae y procesa los filtros seleccionados para convertirlos en parámetros de búsqueda.
     */
    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams {
        if (filters.isEmpty()) return FilterSearchParams()

        return FilterSearchParams(
            platform = filters.asUriPart<PlatformFilter>(),
            type = filters.asUriPart<TypeFilter>(),
            genre = filters.asUriPart<GenreFilter>(),
            year = filters.asUriPart<YearFilter>(),
            note = filters.asUriPart<NoteFilter>(),
            sort = filters.asUriPart<SortFilter>(),
        )
    }

    // --- Funciones de Extensión para facilitar la extracción ---
    private inline fun <reified R> AnimeFilterList.getFirst(): R = first { it is R } as R

    private inline fun <reified R> AnimeFilterList.asUriPart(): String = (getFirst<R>() as UriPartFilter).toUriPart()

    /**
     * Datos estáticos que alimentan las opciones de los filtros.
     */
    private object Data {
        private val ANY = "<Seleccionar>" to ""
        private val ANY_ZERO = "<Seleccionar>" to "0"

        val PLATFORMS = arrayOf(
            ANY,
            "Netflix" to "netflix",
            "Amazon Prime Video" to "amazon-prime-video",
            "Tokyo Mx" to "tokyo-mx",
            "Disney+" to "disney",
            "Apple TV+" to "apple-tv",
            "Tv Tokyo" to "tv-tokyo",
            "At-X" to "at-x",
            "Hulu" to "hulu",
            "HBO Max" to "hbo-max",
            "HBO" to "hbo",
            "Bs11" to "bs11",
            "Nbc" to "nbc",
        )

        val TYPES = arrayOf(
            ANY,
            "Películas" to "peliculas",
            "Series" to "series",
            "Animes" to "animes",
            "Doramas" to "doramas",
        )

        val GENRES = arrayOf(
            ANY,
            "Acción" to "accion",
            "Action & Adventure" to "action-adventure",
            "Animación" to "animacion",
            "Anime" to "anime",
            "Aventura" to "aventura",
            "Bélica" to "belica",
            "Ciencia Ficción" to "ciencia-ficcion",
            "Comedia" to "comedia",
            "Crimen" to "crimen",
            "Documental" to "documental",
            "Drama" to "drama",
            "Familia" to "familia",
            "Fantasía" to "fantasia",
            "Historia" to "historia",
            "Kids" to "kids",
            "Misterio" to "misterio",
            "Música" to "musica",
            "News" to "news",
            "Película De Tv" to "pelicula-de-tv",
            "Reality" to "reality",
            "Romance" to "romance",
            "Sci-Fi & Fantasy" to "sci-fi-fantasy",
            "Soap" to "soap",
            "Suspense" to "suspense",
            "Talk" to "talk",
            "Terror" to "terror",
            "War & Politics" to "war-politics",
            "Western" to "western",
        )

        val YEARS = arrayOf(ANY_ZERO) + (2026 downTo 1979).map {
            it.toString() to it.toString()
        }.toTypedArray()

        val NOTE = arrayOf(
            ANY_ZERO,
            "9+ Obra maestra" to "9",
            "8+ Excelente" to "8",
            "7+ Muy buena" to "7",
            "6+ Buena" to "6",
            "5+ Regular" to "5",
        )

        val SORT = arrayOf(
            "Más populares" to "popularidad",
            "Más recientes" to "fecha",
            "Mejor nota" to "nota",
            "Año: nuevo a viejo" to "año-desc",
            "Año: viejo a nuevo" to "año-asc",
            "A-Z" to "az",
            "Z-A" to "za",
        )
    }
}

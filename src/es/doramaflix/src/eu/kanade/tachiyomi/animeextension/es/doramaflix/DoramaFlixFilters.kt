package eu.kanade.tachiyomi.animeextension.es.doramaflix

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Filtros para el catálogo de DoramasFlix.
 * Proporciona una interfaz estructurada para manejar los filtros de búsqueda del sitio.
 */
object DoramaFlixFilters {

    /**
     * Filtro de selección simple que asocia un nombre visible con un valor para la URL.
     */
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    /**
     * Clase auxiliar para los valores de las casillas de verificación.
     */
    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    /**
     * Filtro de casillas de verificación que permite seleccionar múltiples valores.
     */
    open class CheckBoxFilterList(name: String, val vals: Array<Pair<String, String>>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, vals.map { CheckBoxVal(it.first, false) }) {
        fun getCheckedValues(): List<String> = state.mapIndexedNotNull { index, checkbox ->
            if (checkbox.state) vals[index].second else null
        }
    }

    // --- Filtros de Casillas de Verificación (Múltiple Selección) ---
    class NetworkFilter : CheckBoxFilterList("Plataformas", Data.NETWORKS)
    class GenreFilter : CheckBoxFilterList("Géneros", Data.GENRES)
    class LabelFilter : CheckBoxFilterList("Etiquetas", Data.LABELS)

    // --- Filtros de Selección Simple (Única Selección) ---
    class StatusFilter : UriPartFilter("Estado", Data.STATUS)
    class TypeFilter : UriPartFilter("Tipo", Data.TYPES)
    class SortFilter : UriPartFilter("Ordenar", Data.SORTS)
    class LanguageFilter : UriPartFilter("Idiomas", Data.LANGUAGES)
    class CountryFilter : UriPartFilter("Países", Data.COUNTRYS)

    // --- Filtros de Escritura Manual ---
    class YearFilter : AnimeFilter.Text("Year")

    /**
     * Estructura de la lista de filtros que se muestra en la aplicación.
     */
    val FILTER_LIST get() = AnimeFilterList(
        AnimeFilter.Header("Filtros de Búsqueda"),
        AnimeFilter.Separator(),
        // Filtros existentes (mantener compatibilidad)
        TypeFilter(),
        // StatusFilter(), Omitimos Status
        SortFilter(),
        CountryFilter(),
        LanguageFilter(),
        AnimeFilter.Separator(),
        // Filtro de Año Escrito Manual.
        YearFilter(),
        AnimeFilter.Separator(),
        // Filtros de Seleccion Mutiples.
        NetworkFilter(),
        AnimeFilter.Separator(),
        GenreFilter(),
        AnimeFilter.Separator(),
        LabelFilter(),
        AnimeFilter.Separator(),
    )

    /**
     * Clase de datos para transportar los valores seleccionados en los filtros.
     */
    data class FilterSearchParams(
        val network: List<String>? = emptyList(),
        val genre: List<String>? = emptyList(),
        val label: List<String>? = emptyList(),
        val type: String = "doramas",
        // val status: String? = null,
        val sort: String? = "POPULARITY_DESC",
        val country: String? = "",
        val language: String? = "",
        val year: String? = "",
    )

    /**
     * Extrae y procesa los filtros seleccionados para convertirlos en parámetros de búsqueda.
     */
    internal fun getSearchParameters(filters: AnimeFilterList): FilterSearchParams = FilterSearchParams(
        network = filters.getFirstOrNull<NetworkFilter>()?.getCheckedValues(),
        genre = filters.getFirstOrNull<GenreFilter>()?.getCheckedValues(),
        label = filters.getFirstOrNull<LabelFilter>()?.getCheckedValues(),
        type = filters.getFirstOrNull<TypeFilter>()?.toUriPart() ?: "doramas",
        // status = filters.getFirstOrNull<StatusFilter>()?.toUriPart(),
        sort = filters.getFirstOrNull<SortFilter>()?.toUriPart() ?: "POPULARITY_DESC",
        country = filters.getFirstOrNull<CountryFilter>()?.toUriPart(),
        language = filters.getFirstOrNull<LanguageFilter>()?.toUriPart(),
        year = filters.getFirstOrNull<YearFilter>()?.state,
    )

    // --- Funciones de Extensión para facilitar la extracción ---
    private inline fun <reified R> AnimeFilterList.getFirstOrNull(): R? = firstOrNull { it is R } as R?

    /**
     * Datos estáticos que alimentan las opciones de los filtros.
     * NOTA: Los valores de LABELS, LANGUAGES y COUNTRYS están completados con valores comunes para sitios de dramas.
     */
    private object Data {
        private val ANY = "<Seleccionar>" to ""

        val GENRES = arrayOf(
            "Drama" to "drama",
            "Documental" to "documental",
            "Comedia" to "comedia",
            "Política" to "politica",
            "Crimen" to "crimen",
            "Misterio" to "misterio",
            "Reality" to "reality",
            "Familia" to "familia",
            "Aventura" to "aventura",
            "Fantasía" to "fantasia",
            "Romance" to "romance",
            "Western" to "western",
            "Ciencia ficción" to "ciencia-ficcion",
            "Animación" to "animacion",
            "Terror" to "terror",
            "Acción" to "accion",
            "Kids" to "kids",
            "Soap" to "soap",
            "Thriller" to "thriller",
            "History" to "history",
            "War" to "war",
            "Music" to "music",
            "News" to "news",
        )

        val LABELS = arrayOf(
            "Seres de fantasía" to "seres-de-fantasia",
            "Comedia Romantica" to "comedia-romantica",
            "Supernatural" to "supernatural",
            "Triangulo amoroso" to "triangulo-amoroso",
            "Escolar" to "escolar",
            "Histórico" to "historico",
            "Psicológico" to "psicologico",
            "Universidad" to "universidad",
            "Juvenil" to "juvenil",
            "Para llorar" to "para-llorar",
            "Oficina" to "oficina",
            "Fiscales" to "fiscales",
            "Ejército" to "ejercito",
            "Médico" to "medico",
            "Policial" to "policial",
            "Belleza" to "belleza",
            "Música" to "musica",
            "Fantasmas" to "fantasmas",
            "Viajes en el tiempo" to "viajes-en-el-tiempo",
            "Catástrofe" to "catastrofe",
            "BL" to "bl",
            "Negocios" to "negocios",
            "Amistad" to "amistad",
            "Vida" to "vida",
            "Leyes" to "leyes",
            "Deportes" to "deportes",
            "Comida" to "comida",
            "Suspenso" to "suspenso",
            "Guerra" to "guerra",
            "Lucha" to "lucha",
            "Robots" to "robots",
            "Familiar" to "familiar",
            "Girl power" to "girl-power",
            "Bad boys" to "bad-boys",
            "KShow" to "kshow",
            "Vampiros" to "vampiros",
            "Minidrama" to "minidrama",
            "Fantasia" to "fantasia",
            "Live action" to "live-action",
            "Venganza" to "venganza",
            "cocina" to "cocina",
            "Investigación" to "investigacion",
            "Detectives" to "detectives",
            "Diseño" to "diseno",
            "Empresa" to "empresa",
            "Legal" to "legal",
            "Ficción" to "ficcion",
            "Biográfico" to "biografico",
            "Medicina" to "medicina",
            "Obnibus" to "obnibus",
            "Bélico" to "belico",
            "Melodrama" to "melodrama",
            "Idols" to "idols",
            "Entretenimiento" to "entretenimiento",
            "Pérdida de memoria" to "perdida-de-memoria",
            "Maquillaje" to "maquillaje",
            "Web-Drama" to "web-drama",
            "Secretos" to "secretos",
            "GL" to "gl",
            "+18" to "18",
            "Superhéroes" to "superheroes",
            "Magia" to "magia",
            "Independiente" to "independiente",
            "Hacker" to "hacker",
            "Videojuegos" to "videojuegos",
            "Kpop" to "kpop",
            "Marvel" to "marvel",
            "Carros" to "carros",
            "Hechos reales" to "hechos-reales",
            "Zombies" to "zombies",
            "Magos" to "magos",
            "Animales" to "animales",
            "Autos" to "autos",
            "Eróticas" to "eroticas",
            "Oscar" to "oscar",
            "Navidad" to "navidad",
            "Realeza" to "realeza",
            "Dibujos" to "dibujos",
            "Enfermedad" to "enfermedad",
            "Futbol" to "futbol",
            "Muñecos" to "munecos",
            "Dc Comics" to "dc-comics",
            "LGBT" to "lgbt",
            "Premios Óscar" to "premios-oscar",
            "Telenovela" to "telenovela",
            "TikTok" to "tiktok",
            "Series" to "series",
            "Del Odio al Amor" to "del-odio-al-amor",
            "Guardaespaldas" to "guardaespaldas",
            "Época" to "época",
            "Anime" to "anime",
        )

        val LANGUAGES = arrayOf(
            ANY,
            "Inglés" to "36",
            "Castellano" to "37",
            "Latino" to "38",
            "Subtitulado" to "192",
            "Portugués" to "1327",
            "Coreano" to "13109",
            "Japones" to "13110",
            "Mandarín" to "13111",
            "Tailandes" to "13112",
            "Filipino" to "13113",
            "Indones" to "13114",
            "Vietnamita" to "343422",
        )

        val COUNTRYS = arrayOf(
            ANY,
            "Corea del Sur" to "corea",
            "China" to "china",
            "Japon" to "japon",
            "Tailandia" to "tailandia",
            "Filipinas" to "filipinas",
            "Indonesia" to "indonesia",
            "Taiwan" to "taiwan",
            "Vietnam" to "vietnam",
            "Hong Kong" to "hong-kong",
            "Malasia" to "malasia",
            "Singapur" to "singapur",
            "EE UU" to "ee-uu",
        )

        // Mantener los datos existentes para los filtros que no se modificaron
        val NETWORKS = arrayOf(
            ANY,
            "HBO" to "hbo",
            "Netflix" to "netflix",
            "BBC One" to "bbc-one",
            "Amazon" to "amazon",
            "Channel 4" to "channel-4",
            "Disney+" to "disney",
            "NBC" to "nbc",
            "Syfy" to "syfy",
            "ABC" to "abc",
            "The CW" to "the-cw",
            "AMC" to "amc",
            "Starz" to "starz",
            "Antena 3" to "antena-3",
            "FOX" to "fox",
            "Telecinco" to "telecinco",
            "Lifetime" to "lifetime",
            "RÚV" to "ruv",
            "Atresplayer Premium" to "atresplayer-premium",
            "The WB" to "the-wb",
            "ORF" to "orf",
            "FOX España" to "fox-espana",
            "SVT1" to "svt1",
            "Cartoon Network" to "cartoon-network",
            "USA Network" to "usa-network",
            "TV3" to "tv3",
            "laSexta" to "lasexta",
            "BBC Two" to "bbc-two",
            "Univision" to "univision",
            "FX" to "fx",
            "CBS" to "cbs",
            "ABC Family" to "abc-family",
            "Freeform" to "freeform",
            "ESPN" to "espn",
            "Adult Swim" to "adult-swim",
            "MTV" to "mtv",
            "Apple TV+" to "apple-tv",
            "Pantaya" to "pantaya",
            "Hulu" to "hulu",
            "DC Universe" to "dc-universe",
            "HBO Max" to "hbo-max",
            "Caracol TV" to "caracol-tv",
            "Telemundo" to "telemundo",
            "CBC Television" to "cbc-television",
            "Showtime" to "showtime",
            "Comedy Central" to "comedy-central",
            "Nickelodeon" to "nickelodeon",
            "Disney XD" to "disney-xd",
            "Movistar+" to "movistar",
            "La 1" to "la-1",
            "A&E" to "aande",
            "History" to "history",
            "tvN" to "tvn",
            "#0" to "0",
            "HBO Latin America" to "hbo-latin-america",
            "BBC America" to "bbc-america",
            "HBO España" to "hbo-espana",
            "MTV Latin America" to "mtv-latin-america",
            "BBC Three" to "bbc-three",
            "TNT" to "tnt",
            "E4" to "e4",
            "OnStyle" to "onstyle",
            "Mnet" to "mnet",
            "SBS" to "sbs",
            "KBS W" to "kbs-w",
            "KBS2" to "kbs2",
            "JTBC" to "jtbc",
            "MBN" to "mbn",
            "MBC" to "mbc",
            "OCN" to "ocn",
            "wavve" to "wavve",
            "Hunan Television" to "hunan-television",
            "Mango TV" to "mango-tv",
            "iQiyi" to "iqiyi",
            "Youku" to "youku",
            "Tencent Video" to "tencent-video",
            "KBS1" to "kbs1",
            "MBC Drama" to "mbc-drama",
            "TBS" to "tbs",
            "Sohu" to "sohu",
            "Jiangsu Television" to "jiangsu-television",
            "Dragon TV" to "dragon-tv",
            "Zhejiang Television" to "zhejiang-television",
            "TV Tokyo" to "tv-tokyo",
            "Showcase" to "showcase",
            "Movierock" to "movierock",
            "Jung Ji-woo Film" to "jung-ji-woo-film",
            "CGV Arthouse" to "cgv-arthouse",
            "Myung Films" to "myung-films",
            "CJ Entertainment" to "cj-entertainment",
            "about Film" to "about-film",
            "FILM COMPANY HAEGEURIM" to "film-company-haegeurim",
            "Jeonwonsa Film" to "jeonwonsa-film",
            "Contents Panda" to "contents-panda",
            "MOVement" to "movement",
            "KM Culture Co." to "km-culture-co",
            "Showbox" to "showbox",
            "Michigan Venture Capital" to "michigan-venture-capital",
            "M-Line Distribution" to "m-line-distribution",
            "Megabox Plus M" to "megabox-plus-m",
        )

        val TYPES = arrayOf(
            "Doramas" to "doramas",
            "Peliculas" to "peliculas",
            "Variedades" to "variedades",
        )

        val STATUS = arrayOf(
            ANY,
            "En emisión" to "emision",
            "Finalizada" to "finalizada",
            "Pendiente" to "pendiente",
            "Cancelada" to "cancelada",
        )

        val SORTS = arrayOf(
            "Recientes" to "_ID_DESC",
            "Antiguos" to "_ID_ASC",
            "Populares" to "POPULARITY_DESC",
            "Mejor Valorados" to "RATING_DESC",
            "Mas Votados" to "RATING_COUNT_DESC",
            "Mas Vistos" to "VIEWS_COUNT_DESC",
            "Mas Favoritos" to "FAVS_COUNT_DESC",
        )
    }
}

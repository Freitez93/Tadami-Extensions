package eu.kanade.tachiyomi.animeextension.es.doramaflix

import android.util.Log
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DoraFlixLib — Cliente GraphQL para la API de DoramasFlix.
 *
 * Proporciona métodos para consultar la API de DoramasFlix:
 * - Listar doramas y películas (paginación)
 * - Buscar contenido
 * - Obtener detalles de películas y doramas
 * - Listar temporadas y episodios
 * - Obtener enlaces de episodios
 */
object DoramaFlixLib {

    // ========================== CONSTANTES ==========================
    private const val DEBUG_ACTIVE = false
    private const val DEBUG_TAG = "DoramasFlix"
    private const val DORAMAS_API = "https://sv1.fluxcedene.net/api/gql"

    // private const val DORAMAS_API = "https://doraflix.fluxcedene.net/api/gql"
    private val TYPE_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val graphQLHeaders = Headers.Builder()
        .add("authority", "sv1.fluxcedene.net")
        .add("accept", "application/json, text/plain, */*")
        .add("content-type", "application/json;charset=UTF-8")
        .add("origin", "https://doramasflix.in")
        .add("platform", "doramasflix")
        .add("authorization", "Bear")
        .build()

    // ============================================
    // ===== MÉTODOS PRINCIPALES DE CONSULTA =====
    // ============================================

    /**
     * Lista doramas con paginación.
     */
    fun fetchDoramas(
        page: Int = 1,
        perPage: Int = 32,
        sort: String? = "POPULARITY_DESC",
        isTVShow: Boolean = false,
        year: String? = "",
        country: String? = "",
        languages: String? = "",
        networks: List<String>? = emptyList(),
        genres: List<String>? = emptyList(),
        labels: List<String>? = emptyList(),
    ): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            page = page,
            perPage = perPage,
            sort = sort,
            filter = Filters(
                isTVShow,
                year = year?.takeIf(String::isNotBlank),
                country = country?.takeIf(String::isNotBlank),
                languages = languages?.takeIf(String::isNotBlank),
                networks = networks?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
                genres = genres?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
                labels = labels?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
            ),
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query listDoramas(
                $page: Int,
                $perPage: Int,
                $sort: SortFindManyDoramaInput,
                $filter: FilterFindManyDoramaInput
            ) {
                paginationDorama(page: $page, perPage: $perPage, sort: $sort, filter: $filter) {
                    count
                    pageInfo { currentPage hasNextPage hasPreviousPage }
                    items {
                        _id slug name names name_es
                        poster_path poster isTVShow
                        rating tmdb_id languages __typename
                    }
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchDoramas")
    }

    /**
     * Lista de películas con paginación.
     */
    fun fetchMovies(
        page: Int = 1,
        perPage: Int = 32,
        sort: String? = "POPULARITY_DESC",
        year: String? = "",
        country: String? = "",
        languages: String? = "",
        networks: List<String>? = emptyList(),
        genres: List<String>? = emptyList(),
        labels: List<String>? = emptyList(),
    ): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            page = page,
            perPage = perPage,
            sort = sort,
            filter = Filters(
                year = year?.takeIf(String::isNotBlank),
                country = country?.takeIf(String::isNotBlank),
                languages = languages?.takeIf(String::isNotBlank),
                networks = networks?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
                genres = genres?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
                labels = labels?.takeIf { it.isNotEmpty() }?.map { GenericSlug(it) },
            ),
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query listMovies(
                $page: Int,
                $perPage: Int,
                $sort: SortFindManyMovieInput,
                $filter: FilterFindManyMovieInput
            ) {
                paginationMovie(page: $page, perPage: $perPage, sort: $sort, filter: $filter) {
                    count
                    pageInfo { currentPage hasNextPage hasPreviousPage }
                    items {
                        _id slug name names name_es
                        poster_path poster rating tmdb_id
                        languages __typename
                    }
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchMovies")
    }

    /**
     * Busca doramas y películas.
     */
    fun fetchSearchAll(input: String, limit: Int = 5): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            input = input,
            limit = limit,
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query searchAll($input: String!, $limit: Int!) {
                searchDorama(input: $input, limit: $limit) {
                    _id slug name names name_es poster_path poster
                    isTVShow rating tmdb_id languages __typename
                }
                searchMovie(input: $input, limit: $limit) {
                    _id slug name names name_es poster_path poster
                    rating tmdb_id languages __typename
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchSearchAll")
    }

    /**
     * Obtiene detalles de una película.
     */
    fun fetchMovieDetail(movieID: String?): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            moviesID = movieID,
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query detailMovieExtra($moviesID: MongoID!) {
                detailMovie(filter: {_id: $moviesID}) {
                    _id slug name names name_es overview languages country
                    popularity rating tmdb_id poster backdrop poster_path
                    backdrop_path release_date runtime status created_by cast
                    genres { _id slug name }
                    labels { _id slug name }
                    networks { _id slug name }
                    __typename
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchMovieDetail")
    }

    /**
     * Obtiene detalles de un dorama.
     */
    fun fetchDoramaDetail(doramaID: String): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            doramaID = doramaID,
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query detailDorama($doramaID: MongoID!) {
                detailDorama(filter: {_id: $doramaID}) {
                    _id slug name names name_es overview languages country popularity
                    rating tmdb_id poster backdrop poster_path backdrop_path first_air_date
                    last_air_date number_of_seasons status premiere isFinish created_by cast
                    genres { _id slug name }
                    labels { _id slug name }
                    networks { _id slug name }
                    __typename
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchDoramaDetail")
    }

    /**
     * Lista de episodios de un Dorama.
     */
    fun fetchEpisodes(doramaID: String): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            doramaID = doramaID,
        )

        // Creamos la query para la peticion.
        val queryBody = $$"""
            query listEpisodesPagination($doramaID: MongoID!) {
                paginationEpisode(
                    page: 1,
                    perPage: 1000,
                    sort: SEASON_NUMBER_ASC,
                    filter: {serie_id: $doramaID}
                ) {
                    items {
                        _id slug name name_es serie_id serie_name serie_name_es
                        still_path episode_number season_number overview date_string
                        air_date __typename
                    }
                }
            }
        """.trimIndent()

        return graphQLPost(queryBody, variables, "fetchDoramaDetail")
    }

    /**
     * Obtiene enlaces de un episodio.
     */
    fun fetchLinksOnline(id: String, episode: Boolean = false): Request {
        // Contruimos las variables con una Class
        val variables = GraphQLVariables(
            doramaID = id,
        )

        // Creamos la query para la peticion.
        val queryBody = when {
            episode ->
                $$"""
                query GetEpisodeLinks($doramaID: String!) {
                    detailEpisode(filter: {slug: $doramaID}) {
                        links_online
                    }
                }
            """

            else ->
                $$"""
                query GetEpisodeLinks($doramaID: MongoID!) {
                    detailMovie(filter: {_id: $doramaID}){
                        links_online
                    }
                }
            """
        }.trimIndent()

        return graphQLPost(queryBody, variables, "fetchLinksOnline")
    }

    private fun graphQLPost(
        query: String,
        variables: GraphQLVariables? = null,
        operationName: String? = "graphQLPost",
    ): Request {
        val bodyRequest = GraphQLRequest(query, variables)
        val jsonRequest = json.encodeToString(bodyRequest)
        if (DEBUG_ACTIVE) Log.d(DEBUG_TAG, "$operationName -> $jsonRequest")
        return POST(
            DORAMAS_API,
            graphQLHeaders,
            jsonRequest.toRequestBody(TYPE_MEDIA),
        )
    }

    @Serializable
    data class GraphQLRequest(
        val query: String,
        val variables: GraphQLVariables? = null,
    )

    @Serializable
    data class GraphQLVariables(
        val page: Int? = null,
        val perPage: Int? = null,
        val sort: String? = null,
        val input: String? = null,
        val limit: Int? = null,
        val filter: Filters? = null,
        // otras variables
        val doramaID: String? = null,
        val moviesID: String? = null,
    )

    @Serializable
    data class Filters(
        val isTVShow: Boolean? = null,
        val year: String? = null,
        val country: String? = null,
        val languages: String? = null,
        val networks: List<GenericSlug>? = null,
        val genres: List<GenericSlug>? = null,
        val labels: List<GenericSlug>? = null,
    )

    @Serializable
    data class GenericSlug(
        val slug: String,
    )
}

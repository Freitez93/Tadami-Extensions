package eu.kanade.tachiyomi.lib.lpayerextractor

import kotlinx.serialization.Serializable
@Serializable
data class LpayerVideoData(
    val hls: String? = null,
    val source: String? = null,
    val url: String? = null,
    val file: String? = null,
    val subtitle: Map<String, String>? = null,
)

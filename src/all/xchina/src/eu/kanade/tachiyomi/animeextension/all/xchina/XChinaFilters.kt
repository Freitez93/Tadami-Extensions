package eu.kanade.tachiyomi.animeextension.all.xchina

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : AnimeFilter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class SortFilter : SelectFilter("Sort by", SORT) {
    companion object {
        val SORT = listOf(
            Pair("Most recently", ""),
            Pair("Most viewed", "sort-read"),
            Pair("Most commented", "sort-comment"),
            Pair("Recent commented", "sort-recent"),
            Pair("Longest", "sort-length"),
        )
    }
}

class GenreList : SelectFilter("Genres", GENRES) {
    companion object {
        val GENRES = listOf(
            Pair("< Select Genre >", ""),
            // Chinese AV
            Pair("ChAV: Model Media", "series-5f904550b8fcc"),
            Pair("ChAV: Independent Creators", "series-61bf6e439fed6"),
            Pair("ChAV: TXVLOG", "series-61014080dbfde"),
            Pair("ChAV: Peach Media", "series-5fe8403919165"),
            Pair("ChAV: Star Media", "series-6054e93356ded"),
            Pair("ChAV: timi Media", "series-60153c49058ce"),
            Pair("ChAV: Banana Video", "series-65e5f74e4605c"),
            Pair("ChAV: 91mv", "series-5fe840718d665"),
            Pair("ChAV: JingDong Films", "series-60126bcfb97fa"),
            Pair("ChAV: 1024xb", "series-6072997559b46"),
            Pair("ChAV: iDoL", "series-63d134c7a0a15"),
            Pair("ChAV: Other Chinese AV", "series-63986aec205d8"),
            Pair("ChAV: IBiZa Media", "series-64e9cce89da21"),
            Pair("ChAV: XSJ", "series-63490362dac45"),
            Pair("ChAV: ED Mosaic", "series-63732f5c3d36b"),
            Pair("ChAV: Elephant Media", "series-65bcaa9688514"),
            Pair("ChAV: QQ Media", "series-6230974ada989"),
            Pair("ChAV: Loli Club", "series-6360ca9706ecb"),
            Pair("ChAV: SATV", "series-633ef3ef07d33"),
            Pair("ChAV: Tiktok Adult", "series-6248705dab604"),
            Pair("ChAV: HuLu Films", "series-6193d27975579"),
            Pair("ChAV: Utopia", "series-637750ae0ee71"),
            Pair("ChAV: Eros Media", "series-6405b6842705b"),
            Pair("ChAV: Lobo Media", "series-60589daa8ff97"),
            Pair("ChAV: 91 Qiezi", "series-639c8d983b7d5"),
            Pair("ChAV: Strawberry Media", "series-671ddc0b358ca"),
            Pair("ChAV: JVID", "series-6964cfbda328b"),
            Pair("ChAV: AI Videos", "series-69f3977abc9f7"),
            Pair("ChAV: YOYO", "series-64eda52c1c3fb"),
            Pair("ChAV: Rubbernecking", "series-671dd88d06dd3"),
            Pair("ChAV: Bili Media", "series-64458e7da05e6"),
            Pair("ChAV: Yingxiu Media", "series-6560dc053c99f"),
            Pair("ChAV: Watermelon Media", "series-648e1071386ef"),
            Pair("ChAV: Springfever", "series-64be8551bd0f1"),
            // Japanese AV
            Pair("JpAV: Censored AV", "series-6395aba3deb74"),
            Pair("JpAV: Uncensored AV", "series-6395ab7fee104"),
            Pair("JpAV: AV Commentary", "series-6608638e5fcf7"),
            // Model Shooting
            Pair("ModelShooting: Pans Videos", "series-63963186ae145"),
            Pair("ModelShooting: Others", "series-63963534a9e49"),
            Pair("ModelShooting: Dancing", "series-64edbeccedb2e"),
            Pair("ModelShooting: MetCN", "series-63ed0f22e9177"),
            Pair("ModelShooting: Guo Ge Works", "series-6396315ed2e49"),
            Pair("ModelShooting: SweatGirl", "series-68456564f2710"),
            Pair("ModelShooting: FYNC Works", "series-6396319e6b823"),
            Pair("ModelShooting: Art Endless", "series-6754a97d2b343"),
            Pair("ModelShooting: Huang Fu", "series-668c3b2de7f1c"),
            Pair("ModelShooting: Riyue Club", "series-63ab1dd83a1c6"),
            // Amateur
            Pair("Amateur: Whoring", "series-63965bf7b7f51"),
            Pair("Amateur: Living Show", "series-63965bd5335fc"),
            // Erotic Movies
            Pair("EroticMovies: Chinese Movies", "series-6396492fdb1a0"),
            Pair("EroticMovies: Asian Movies", "series-6396494584b57"),
            Pair("EroticMovies: Western Movies", "series-63964959ddb1b"),
            // Other Videos
            Pair("Other: Asian Videos", "series-63963ea949a82"),
            Pair("Other: Scandals", "series-63963de3f2a0f"),
            Pair("Other: Western Videos", "series-6396404e6bdb5"),
            Pair("Other: Non-porn", "series-66643478ceedd"),
        )
    }
}

fun getFilters() = AnimeFilterList(
    SortFilter(),
    GenreList(),
    AnimeFilter.Separator(),
    AnimeFilter.Header("Genre filters ignored with text search!!"),
)

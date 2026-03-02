package repo.plusorca.cloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*

class ShortMax : MainAPI() {
    override var mainUrl = "https://api.sansekai.my.id"
    override var name = "ShortMax 👍"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    companion object {
        private const val TIMEOUT = 30000L
        private val mapper = jacksonObjectMapper()
    }

    override val mainPage = mainPageOf(
        "/api/shortmax/foryou" to "Untukmu",
        "/api/shortmax/latest" to "Terbaru",
        "/api/shortmax/rekomendasi" to "Rekomendasi",
    )

    private fun DramaItem.toSearchResult(): SearchResponse? {
        val id = bookId ?: return null
        val title = bookName?.trim().orEmpty()
        if (title.isBlank()) return null

        return newTvSeriesSearchResponse(
            title,
            id,
            TvType.AsianDrama
        ) {
            this.posterUrl = coverWap?.fixUrl()
        }
    }

    private suspend fun fetchMainItems(path: String, page: Int): List<DramaItem> {
        val url = if (path.startsWith("http")) path else "$mainUrl$path"
        val finalUrl = if (url.contains("?")) "$url&page=$page" else "$url?page=$page"
        
        return try {
            val response = app.get(finalUrl, timeout = TIMEOUT).text
            
            // Coba parse sebagai list langsung
            tryParseJson<List<DramaItem>>(response)?.let { return it }
            
            // Coba parse sebagai wrapper object
            val wrapper = tryParseJson<MainPageWrapper>(response)
            wrapper?.data?.let { return it }
            wrapper?.result?.let { return it }
            wrapper?.list?.let { return it }
            
            // Coba parse sebagai Map dengan pendekatan berbeda (tanpa mapper)
            val mapWrapper = tryParseJson<Map<String, Any>>(response)
            mapWrapper?.let { map ->
                val dataField = map["data"] ?: map["result"] ?: map["list"]
                if (dataField != null) {
                    // Gunakan toString() sebagai alternatif sederhana
                    // atau coba parse langsung dengan tipe yang sesuai
                    @Suppress("UNCHECKED_CAST")
                    if (dataField is List<*>) {
                        return (dataField as List<Map<String, Any>>).mapNotNull { item ->
                            DramaItem(
                                bookId = item["bookId"]?.toString(),
                                bookName = item["bookName"]?.toString(),
                                coverWap = item["coverWap"]?.toString(),
                                chapterCount = (item["chapterCount"] as? Int) ?: (item["chapterCount"]?.toString()?.toIntOrNull()),
                                introduction = item["introduction"]?.toString(),
                                tags = (item["tags"] as? List<String>),
                                score = (item["score"] as? Double) ?: (item["score"]?.toString()?.toDoubleOrNull()),
                                releaseYear = (item["releaseYear"] as? Int) ?: (item["releaseYear"]?.toString()?.toIntOrNull())
                            )
                        }
                    }
                }
            }
            
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val items = fetchMainItems(request.data, page)
                .distinctBy { it.bookId }
                .mapNotNull { it.toSearchResult() }
            newHomePageResponse(request.name, items)
        } catch (e: Exception) {
            newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        return try {
            // Coba search endpoint jika ada
            val searchUrl = "$mainUrl/api/shortmax/search?q=${q.urlEncoded()}"
            val searchResponse = app.get(searchUrl, timeout = TIMEOUT).text
            
            val searchResults = tryParseJson<List<DramaItem>>(searchResponse)
            if (!searchResults.isNullOrEmpty()) {
                return searchResults.mapNotNull { it.toSearchResult() }
            }
            
            // Fallback: filter dari halaman utama
            val sourcePaths = listOf(
                "/api/shortmax/foryou",
                "/api/shortmax/latest",
                "/api/shortmax/rekomendasi",
            )

            sourcePaths
                .flatMap { path -> fetchMainItems(path, 1) }
                .distinctBy { it.bookId }
                .filter {
                    it.bookName.orEmpty().contains(q, true) ||
                    it.introduction.orEmpty().contains(q, true)
                }
                .mapNotNull { it.toSearchResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val bookId = when {
            url.contains("/book/") -> url.substringAfterLast("/book/").substringBefore("?")
            url.contains("/") -> url.substringAfterLast("/").substringBefore("?")
            else -> url
        }
        
        if (bookId.isBlank()) throw ErrorLoadingException("Invalid URL/ID")

        val detailBody = try {
            app.get("$mainUrl/api/shortmax/detail/$bookId", timeout = TIMEOUT).text
        } catch (e: Exception) {
            throw ErrorLoadingException("Gagal memuat detail: ${e.message}")
        }

        val episodeBody = try {
            app.get("$mainUrl/api/shortmax/allepisode/$bookId", timeout = TIMEOUT).text
        } catch (e: Exception) {
            throw ErrorLoadingException("Gagal memuat episode: ${e.message}")
        }

        val detail = parseDetail(detailBody)
            ?: throw ErrorLoadingException("Detail tidak ditemukan")
            
        val chapters = parseChapters(episodeBody)
            .sortedBy { it.chapterIndex ?: Int.MAX_VALUE }

        val episodes = chapters.mapIndexed { index, chapter ->
            val number = (chapter.chapterIndex ?: index) + 1
            newEpisode(
                LoadData(
                    bookId = bookId,
                    chapterId = chapter.chapterId,
                    chapterIndex = chapter.chapterIndex
                ).toJsonData()
            ) {
                name = chapter.chapterName?.takeIf { it.isNotBlank() } ?: "EP $number"
                episode = number
                posterUrl = chapter.chapterImg?.fixUrl()
            }
        }

        return newTvSeriesLoadResponse(
            name = detail.bookName ?: "ShortMax",
            url = url,
            type = TvType.AsianDrama,
            episodes = episodes
        ) {
            posterUrl = detail.coverWap?.fixUrl()
            plot = detail.introduction
            tags = detail.tags.orEmpty()
            rating = detail.score?.toFloat()
            year = detail.releaseYear
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = parseJson<LoadData>(data)
        val bookId = parsed.bookId ?: return false

        val chapters = parseChapters(
            app.get("$mainUrl/api/shortmax/allepisode/$bookId", timeout = TIMEOUT).text
        )
        
        val chapter = chapters.firstOrNull { 
            !parsed.chapterId.isNullOrBlank() && it.chapterId == parsed.chapterId 
        } ?: chapters.firstOrNull { 
            parsed.chapterIndex != null && it.chapterIndex == parsed.chapterIndex 
        } ?: return false

        val directLinks = chapter.cdnList.orEmpty()
            .sortedByDescending { it.isDefault ?: 0 }
            .flatMap { cdn ->
                cdn.videoPathList.orEmpty().mapNotNull { video ->
                    val videoUrl = video.videoPath?.trim().orEmpty()
                    if (videoUrl.isBlank()) return@mapNotNull null
                    Triple(cdn.cdnDomain.orEmpty(), video.quality ?: 0, videoUrl)
                }
            }
            .distinctBy { it.third }

        directLinks.forEach { (cdnDomain, quality, videoUrl) ->
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = buildString {
                        append("ShortMax")
                        if (quality > 0) append(" ${quality}p")
                        if (cdnDomain.isNotBlank()) append(" - ${cdnDomain.removePrefix("https://").removePrefix("http://")}")
                    },
                    url = videoUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.quality = if (quality > 0) quality else Qualities.Unknown.value
                    this.referer = "$mainUrl/"
                    this.headers = mapOf("Origin" to mainUrl)
                }
            )
        }

        return directLinks.isNotEmpty()
    }

    private fun LoadData.toJsonData(): String = this.toJson()

    private fun parseDetail(body: String): DramaItem? {
        return tryParseJson<DramaItem>(body)
            ?: tryParseJson<DetailWrapper>(body)?.result
            ?: tryParseJson<DetailWrapper>(body)?.data
            ?: tryParseJson<Map<String, DramaItem>>(body)?.values?.firstOrNull()
    }

    private fun parseChapters(body: String): List<Chapter> {
        val direct = tryParseJson<List<Chapter>>(body)
        if (!direct.isNullOrEmpty()) return direct

        val wrapped = tryParseJson<EpisodeWrapper>(body)
        return wrapped?.result
            ?: wrapped?.data
            ?: wrapped?.list
            ?: wrapped?.chapterList
            ?: emptyList()
    }

    private fun String?.fixUrl(): String? {
        return this?.let {
            when {
                it.startsWith("//") -> "https:$it"
                it.startsWith("/") -> "$mainUrl$it"
                !it.startsWith("http") -> "https://$it"
                else -> it
            }
        }
    }

    // Data Classes
    data class LoadData(
        @JsonProperty("bookId") val bookId: String? = null,
        @JsonProperty("chapterId") val chapterId: String? = null,
        @JsonProperty("chapterIndex") val chapterIndex: Int? = null,
    )

    data class DramaItem(
        @JsonProperty("bookId") val bookId: String? = null,
        @JsonProperty("bookName") val bookName: String? = null,
        @JsonProperty("coverWap") val coverWap: String? = null,
        @JsonProperty("chapterCount") val chapterCount: Int? = null,
        @JsonProperty("introduction") val introduction: String? = null,
        @JsonProperty("tags") val tags: List<String>? = null,
        @JsonProperty("score") val score: Double? = null,
        @JsonProperty("releaseYear") val releaseYear: Int? = null,
    )

    data class MainPageWrapper(
        @JsonProperty("data") val data: List<DramaItem>? = null,
        @JsonProperty("result") val result: List<DramaItem>? = null,
        @JsonProperty("list") val list: List<DramaItem>? = null,
    )

    data class DetailWrapper(
        @JsonProperty("result") val result: DramaItem? = null,
        @JsonProperty("data") val data: DramaItem? = null,
    )

    data class Chapter(
        @JsonProperty("chapterId") val chapterId: String? = null,
        @JsonProperty("chapterIndex") val chapterIndex: Int? = null,
        @JsonProperty("chapterName") val chapterName: String? = null,
        @JsonProperty("chapterImg") val chapterImg: String? = null,
        @JsonProperty("cdnList") val cdnList: List<CdnItem>? = null,
    )

    data class EpisodeWrapper(
        @JsonProperty("result") val result: List<Chapter>? = null,
        @JsonProperty("data") val data: List<Chapter>? = null,
        @JsonProperty("list") val list: List<Chapter>? = null,
        @JsonProperty("chapterList") val chapterList: List<Chapter>? = null,
    )

    data class CdnItem(
        @JsonProperty("cdnDomain") val cdnDomain: String? = null,
        @JsonProperty("isDefault") val isDefault: Int? = null,
        @JsonProperty("videoPathList") val videoPathList: List<VideoPath>? = null,
    )

    data class VideoPath(
        @JsonProperty("quality") val quality: Int? = null,
        @JsonProperty("videoPath") val videoPath: String? = null,
    )
}

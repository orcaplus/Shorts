package repo.plusorca.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson

class GoodShort : MainAPI() {
    override var name = "GoodShort"
    override var mainUrl = "https://goodshort.dramabos.my.id"
    override var lang = "id"
    override var hasMainPage = true
    override var hasDownloadSupport = true
    override var hasQuickSearch = false
    override var supportedTypes = setOf(TvType.TvSeries)

    private val gson = Gson()
    private val apiBase = "https://goodshort.dramabos.my.id"

    // ==================== DATA CLASSES ====================
    
    data class NavResponse(
        val success: Boolean,
        val data: NavData
    )

    data class NavData(
        val list: List<NavItem>
    )

    data class NavItem(
        val channelId: Int,
        val title: String,
        val pageType: String
    )

    data class HomeResponse(
        val success: Boolean,
        val data: HomeData
    )

    data class HomeData(
        val records: List<HomeRecord>,
        val current: Int,
        val pages: Int,
        val total: Int,
        val size: Int
    )

    data class HomeRecord(
        val items: List<DramaItem>?,
        val style: String?,
        val name: String?
    )

    data class DramaItem(
        val bookId: String,
        @SerializedName("bookName") val title: String,
        val cover: String,
        val introduction: String,
        val chapterCount: Int,
        val labels: List<String>?,
        @SerializedName("viewCountDisplay") val viewCount: String?,
        @SerializedName("firstChapterId") val firstEpisodeId: Long?,
        val cornerText: String?,
        val grade: String?
    )

    data class SearchResponse(
        val data: SearchData
    )

    data class SearchData(
        val searchResult: SearchResult
    )

    data class SearchResult(
        val records: List<SearchItem>
    )

    data class SearchItem(
        val bookId: String,
        val bookName: String,
        val cover: String,
        val introduction: String,
        val chapterCount: Int,
        val labels: List<String>?,
        val viewCountDisplay: String?,
        val grade: String?
    )

    data class HotResponse(
        val success: Boolean,
        val data: List<HotItem>
    )

    data class HotItem(
        val action: String,
        val tags: String
    )

    data class BookDetailResponse(
        val success: Boolean,
        val data: BookDetailData
    )

    data class BookDetailData(
        val book: BookInfo,
        val list: List<ChapterInfo>
    )

    data class BookInfo(
        val bookId: String,
        val bookName: String,
        val cover: String,
        val introduction: String,
        val chapterCount: Int,
        val labels: List<String>?,
        val viewCountDisplay: String?,
        val grade: String?,
        val writeStatus: String?
    )

    data class ChapterInfo(
        val id: Long,
        val chapterName: String,
        val index: Int,
        val playCountDisplay: String?,
        val playTime: Int?,
        val multiVideos: List<VideoQuality>?
    )

    data class VideoQuality(
        val type: String,
        val filePath: String
    )

    data class PlayResponse(
        val status: Int,
        val data: PlayData
    )

    data class PlayData(
        val id: Long,
        val index: Int,
        val multiVideos: List<VideoQuality>
    )

    // ==================== MAIN PAGE ====================
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePageList = mutableListOf<HomePageList>()
        
        try {
            // Ambil navigasi untuk kategori
            val navResponse = parseJson<NavResponse>(app.get("$apiBase/nav?lang=in").text)
            
            navResponse?.data?.list?.forEach { navItem ->
                if (navItem.channelId > 0 && navItem.channelId != 99) {
                    try {
                        val homeUrl = "$apiBase/home?lang=in&channel=${navItem.channelId}&page=1&size=20"
                        val response = parseJson<HomeResponse>(app.get(homeUrl).text)
                        
                        response?.data?.records?.forEach { record ->
                            record.items?.let { items ->
                                if (items.isNotEmpty()) {
                                    val dramaList = items.mapNotNull { item ->
                                        newSearchResponse(item.title) {
                                            this.posterUrl = item.cover.ifEmpty { null }
                                            this.plot = item.introduction
                                            this.url = "/drama/${item.bookId}"
                                        }
                                    }
                                    
                                    if (dramaList.isNotEmpty()) {
                                        homePageList.add(
                                            HomePageList(
                                                navItem.title,
                                                dramaList
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip jika error
                    }
                }
            }
            
            // Tambahkan halaman trending
            try {
                val hotResponse = parseJson<HotResponse>(app.get("$apiBase/hot?lang=in").text)
                val hotItems = mutableListOf<SearchResponse>()
                
                hotResponse?.data?.forEach { hotItem ->
                    try {
                        val bookId = hotItem.action
                        val detailUrl = "$apiBase/book/$bookId?lang=in"
                        val detailResponse = parseJson<BookDetailResponse>(app.get(detailUrl).text)
                        
                        detailResponse?.data?.book?.let { book ->
                            hotItems.add(
                                newSearchResponse(book.bookName) {
                                    this.posterUrl = book.cover
                                    this.plot = book.introduction
                                    this.url = "/drama/${book.bookId}"
                                }
                            )
                        }
                    } catch (e: Exception) {
                        // Skip jika error
                    }
                }
                
                if (hotItems.isNotEmpty()) {
                    homePageList.add(
                        HomePageList(
                            "🔥 Trending",
                            hotItems
                        )
                    )
                }
            } catch (e: Exception) {
                // Log error jika perlu
            }
            
        } catch (e: Exception) {
            // Log error jika perlu
        }
        
        return HomePageResponse(homePageList)
    }

    // ==================== SEARCH ====================
    
    override suspend fun search(query: String): List<SearchResponse>? {
        val results = mutableListOf<SearchResponse>()
        
        try {
            val url = "$apiBase/search?lang=in&q=${query.urlEncoded()}&page=1&size=20"
            val response = parseJson<SearchResponse>(app.get(url).text)
            
            response?.data?.searchResult?.records?.forEach { item ->
                results.add(
                    newSearchResponse(item.bookName) {
                        this.posterUrl = item.cover
                        this.plot = item.introduction
                        this.url = "/drama/${item.bookId}"
                    }
                )
            }
        } catch (e: Exception) {
            // Log error jika perlu
        }
        
        return results
    }

    // ==================== LOAD DETAIL ====================
    
    override suspend fun load(url: String): LoadResponse? {
        val bookId = url.replace("/drama/", "")
        
        try {
            val detailUrl = "$apiBase/book/$bookId?lang=in"
            val response = parseJson<BookDetailResponse>(app.get(detailUrl).text)
            
            response?.data?.let { data ->
                val book = data.book
                val chapters = data.list
                
                val episodes = chapters.map { chapter ->
                    newEpisode("${book.bookId}|${chapter.id}") {
                        this.name = chapter.chapterName
                        this.description = "Durasi: ${chapter.playTime ?: 0} detik\nViews: ${chapter.playCountDisplay ?: "0"}"
                    }
                }
                
                val status = when (book.writeStatus) {
                    "COMPLETE" -> ShowStatus.Completed
                    else -> ShowStatus.Ongoing
                }
                
                return newTvSeriesLoadResponse(book.bookName, url, TvType.TvSeries, episodes) {
                    this.posterUrl = book.cover
                    this.plot = book.introduction
                    this.tags = book.labels ?: emptyList()
                    this.year = null
                    this.status = status
                }
            }
        } catch (e: Exception) {
            // Log error jika perlu
        }
        
        return null
    }

    // ==================== LOAD LINKS ====================
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        if (parts.size < 2) return false
        
        val bookId = parts[0]
        val chapterId = parts[1]
        
        try {
            // Dapatkan informasi video dari endpoint play
            val playUrl = "$apiBase/play/$chapterId?bookId=$bookId&lang=in"
            val playResponse = parseJson<PlayResponse>(app.get(playUrl).text)
            
            playResponse?.data?.multiVideos?.forEach { video ->
                // Kualitas video
                val quality = when (video.type) {
                    "1080p" -> QUALITY_1080p
                    "720p" -> QUALITY_720p
                    "540p" -> QUALITY_540p
                    else -> QUALITY_UNKNOWN
                }
                
                // Panggil callback dengan link video
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Drama Short [${video.type}]",
                        url = video.filePath,
                        referer = mainUrl
                    ) {
                        this.quality = quality
                    }
                )
            }
            
            return true
        } catch (e: Exception) {
            // Log error jika perlu
        }
        
        return false
    }

    // ==================== HELPER FUNCTIONS ====================

    private inline fun <reified T> parseJson(json: String): T? {
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun String.urlEncoded(): String {
        return java.net.URLEncoder.encode(this, "utf-8")
    }
}

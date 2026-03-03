package repo.plusorca.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.google.gson.annotations.SerializedName

class GoodShort : MainAPI() {
    override var name = "GoodShort"
    override var mainUrl = "https://goodshort.dramabos.my.id"
    override var lang = "id"
    override var hasMainPage = true
    override var hasDownloadSupport = true
    override var hasQuickSearch = false
    override var supportedTypes = setOf(TvType.TvSeries)

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
            val navResponse = app.get("$apiBase/nav?lang=in").parsedSafe<NavResponse>()
            
            navResponse?.data?.list?.forEach { navItem ->
                if (navItem.channelId > 0 && navItem.channelId != 99) {
                    try {
                        val homeUrl = "$apiBase/home?lang=in&channel=${navItem.channelId}&page=1&size=20"
                        val response = app.get(homeUrl).parsedSafe<HomeResponse>()
                        
                        response?.data?.records?.forEach { record ->
                            record.items?.let { items ->
                                if (items.isNotEmpty()) {
                                    val dramaList = items.mapNotNull { item ->
                                        item.toSearchResponse()
                                    }
                                    
                                    if (dramaList.isNotEmpty()) {
                                        homePageList.add(
                                            HomePageList(
                                                navItem.title,
                                                dramaList,
                                                false
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
                val hotResponse = app.get("$apiBase/hot?lang=in").parsedSafe<HotResponse>()
                val hotItems = mutableListOf<SearchResponse>()
                
                hotResponse?.data?.forEach { hotItem ->
                    try {
                        val bookId = hotItem.action
                        val detailUrl = "$apiBase/book/$bookId?lang=in"
                        val detailResponse = app.get(detailUrl).parsedSafe<BookDetailResponse>()
                        
                        detailResponse?.data?.book?.let { book ->
                            hotItems.add(
                                newTvSeriesSearchResponse(
                                    name = book.bookName,
                                    url = "/drama/${book.bookId}",
                                    apiUrl = "/drama/${book.bookId}"
                                ) {
                                    this.posterUrl = book.cover
                                    this.plot = book.introduction
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
                            hotItems,
                            false
                        )
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
            
        } catch (e: Exception) {
            logError(e)
        }
        
        return HomePageResponse(homePageList)
    }

    // Fungsi konversi DramaItem ke SearchResponse
    private fun DramaItem.toSearchResponse(): SearchResponse? {
        return try {
            newTvSeriesSearchResponse(
                name = title,
                url = "/drama/$bookId",
                apiUrl = "/drama/$bookId"
            ) {
                this.posterUrl = cover.ifEmpty { null }
                this.plot = introduction
                if (!viewCount.isNullOrEmpty()) {
                    this.rating = parseViewCount(viewCount)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==================== SEARCH ====================
    
    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        
        try {
            val url = "$apiBase/search?lang=in&q=${query.encodeUTF8()}&page=1&size=20"
            val response = app.get(url).parsedSafe<SearchResponse>()
            
            response?.data?.searchResult?.records?.forEach { item ->
                results.add(
                    newTvSeriesSearchResponse(
                        name = item.bookName,
                        url = "/drama/${item.bookId}",
                        apiUrl = "/drama/${item.bookId}"
                    ) {
                        this.posterUrl = item.cover
                        this.plot = item.introduction
                        item.viewCountDisplay?.let { this.rating = parseViewCount(it) }
                    }
                )
            }
        } catch (e: Exception) {
            logError(e)
        }
        
        return results
    }

    // ==================== LOAD DETAIL ====================
    
    override suspend fun load(url: String): LoadResponse {
        val bookId = url.replace("/drama/", "")
        
        try {
            val detailUrl = "$apiBase/book/$bookId?lang=in"
            val response = app.get(detailUrl).parsedSafe<BookDetailResponse>()
            
            response?.data?.let { data ->
                val book = data.book
                val chapters = data.list
                
                val episodes = chapters.mapIndexed { index, chapter ->
                    Episode(
                        data = "${book.bookId}|${chapter.id}",
                        name = chapter.chapterName,
                        episode = index + 1,
                        description = "Durasi: ${chapter.playTime ?: 0} detik\nViews: ${chapter.playCountDisplay ?: "0"}"
                    )
                }
                
                return newTvSeriesLoadResponse(
                    name = book.bookName,
                    url = url,
                    apiUrl = url
                ) {
                    this.posterUrl = book.cover
                    this.plot = book.introduction
                    this.tags = book.labels ?: emptyList()
                    this.episodes = episodes
                    
                    book.viewCountDisplay?.let { this.rating = parseViewCount(it) }
                    
                    // Tambahkan informasi tambahan
                    this.year = null
                    if (book.writeStatus == "COMPLETE") {
                        this.status = ShowStatus.Completed
                    } else {
                        this.status = ShowStatus.Ongoing
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        
        throw ErrorLoadingException("Drama tidak ditemukan")
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
            val playResponse = app.get(playUrl).parsedSafe<PlayResponse>()
            
            playResponse?.data?.multiVideos?.forEach { video ->
                // Kualitas video
                val quality = when (video.type) {
                    "1080p" -> EnumQuality.FOURK.value
                    "720p" -> EnumQuality.HD.value
                    "540p" -> EnumQuality.SD.value
                    else -> EnumQuality.Unknown.value
                }
                
                // Panggil callback dengan link video
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Drama Short [${video.type}]",
                        url = video.filePath,
                        referer = mainUrl,
                        quality = quality
                    )
                )
            }
            
            return true
        } catch (e: Exception) {
            logError(e)
        }
        
        return false
    }

    // ==================== HELPER FUNCTIONS ====================
    
    private fun parseViewCount(viewCount: String): Float {
        return when {
            viewCount.contains("M") -> {
                viewCount.replace("M", "").toFloatOrNull()?.times(1000000) ?: 0f
            }
            viewCount.contains("K") -> {
                viewCount.replace("K", "").toFloatOrNull()?.times(1000) ?: 0f
            }
            else -> {
                viewCount.toFloatOrNull() ?: 0f
            }
        }
    }

    private inline fun <reified T> String.parsedSafe(): T? {
        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(this, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

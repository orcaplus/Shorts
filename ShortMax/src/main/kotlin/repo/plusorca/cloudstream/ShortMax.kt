package repo.plusorca.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ShortMax : MainAPI() {
    override var mainUrl = "https://api.sansekai.my.id"
    override var name = "ShortMax 👍"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "/api/shortmax/foryou" to "Untukmu",
        "/api/shortmax/latest" to "Terbaru",
        "/api/shortmax/rekomendasi" to "Rekomendasi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        
        val items = fetchItems(request.data)
            .distinctBy { it.id }
            .mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(q, "UTF-8")
        val fromSearch = fetchItems("/api/shortmax/search?q=$encoded")
        
        val items = if (fromSearch.isNotEmpty()) {
            fromSearch
        } else {
            listOf(
                "/api/shortmax/foryou",
                "/api/shortmax/latest",
                "/api/shortmax/rekomendasi",
            ).flatMap { fetchItems(it) }
                .distinctBy { it.id }
                .filter {
                    it.title.contains(q, true) || it.description.orEmpty().contains(q, true)
                }
        }

        return items.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val bookId = extractBookId(url)
        val detail = fetchDetail(bookId)
            ?: throw ErrorLoadingException("Detail ShortMax tidak ditemukan")

        val episodes = detail.chapters
            .sortedBy { it.episodeNumber }
            .map { chapter ->
                newEpisode(
                    LoadData(
                        bookId = detail.bookId,
                        chapterId = chapter.chapterId,
                        episodeNumber = chapter.episodeNumber
                    ).toJson()
                ) {
                    name = chapter.title.ifBlank { "EP ${chapter.episodeNumber}" }
                    episode = chapter.episodeNumber
                    posterUrl = detail.cover
                }
            }

        return newTvSeriesLoadResponse(
            name = detail.title,
            url = "$mainUrl/book/${detail.bookId}",
            type = TvType.AsianDrama,
            episodes = episodes
        ) {
            posterUrl = detail.cover?.fixUrl()
            plot = detail.description
            tags = detail.tags
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
        val payload = parseJson<LoadData>(data)
        val bookId = payload.bookId ?: return false
        val episodeNumber = payload.episodeNumber ?: return false

        val episodeJson = runCatching {
            app.get("$mainUrl/api/shortmax/episode?bookId=$bookId&episodeNumber=$episodeNumber").text
        }.getOrNull() ?: return false

        val root = runCatching { JSONObject(episodeJson) }.getOrNull() ?: return false
        
        // Cek berbagai format response
        val videos = when {
            root.has("videoList") -> root.optJSONArray("videoList")
            root.has("data") -> {
                val data = root.optJSONObject("data")
                data?.optJSONArray("videoList") ?: data?.optJSONArray("videos")
            }
            root.has("videos") -> root.optJSONArray("videos")
            else -> null
        }
        
        if (videos == null || videos.length() == 0) return false

        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*",
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/"
        )

        val links = mutableListOf<Pair<String, String>>()
        for (i in 0 until videos.length()) {
            val video = videos.optJSONObject(i) ?: continue
            
            // Coba berbagai field untuk URL video
            val streamUrl = video.optStringSafe("url")
                ?: video.optStringSafe("videoPath")
                ?: video.optStringSafe("video_url")
                ?: continue
                
            val quality = video.optInt("quality").takeIf { it > 0 }?.toString() ?: "Auto"
            val label = "${quality}p"
            
            links.add(label to streamUrl)
        }

        links.distinctBy { it.second }.forEach { (label, streamUrl) ->
            if (streamUrl.contains(".m3u8", true)) {
                M3u8Helper.generateM3u8(
                    source = "$name $label",
                    streamUrl = streamUrl,
                    referer = mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name $label",
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = label.replace("p", "").toIntOrNull() ?: Qualities.Unknown.value
                        this.headers = headers
                    }
                )
            }
        }

        return links.isNotEmpty()
    }

    private suspend fun fetchItems(path: String): List<BookItem> {
        val body = runCatching { app.get("$mainUrl$path").text }.getOrNull() ?: return emptyList()
        return runCatching { parseItems(body) }.getOrDefault(emptyList())
    }

    private fun parseItems(body: String): List<BookItem> {
        val root = JSONObject(body)
        val results = mutableListOf<BookItem>()

        // Format response untuk search
        val searchResults = root.optJSONArray("results")
        if (searchResults != null) {
            for (i in 0 until searchResults.length()) {
                val obj = searchResults.optJSONObject(i) ?: continue
                parseBook(obj)?.let(results::add)
            }
            return results
        }

        // Format response untuk list
        val data = root.optJSONObject("data") ?: root
        val lists = data.optJSONArray("lists") ?: data.optJSONArray("data") ?: return emptyList()
        
        for (i in 0 until lists.length()) {
            val entry = lists.optJSONObject(i) ?: continue

            // Format foryou (langsung item buku)
            if (entry.has("bookId") || entry.has("book_id")) {
                parseBook(entry)?.let(results::add)
            }

            // Format homepage (nested books)
            appendBooks(results, entry.optJSONArray("books"))
            appendBooks(results, entry.optJSONArray("book_list"))
            appendBooks(results, entry.optJSONArray("list"))
            appendBooks(results, entry.optJSONArray("items"))
        }
        
        return results
    }

    private fun appendBooks(out: MutableList<BookItem>, books: JSONArray?) {
        if (books == null) return
        for (i in 0 until books.length()) {
            val obj = books.optJSONObject(i) ?: continue
            parseBook(obj)?.let(out::add)
        }
    }

    private fun parseBook(obj: JSONObject): BookItem? {
        val id = obj.optStringSafe("bookId")
            ?: obj.optStringSafe("book_id")
            ?: obj.optStringSafe("id")
            ?: return null
            
        val title = obj.optStringSafe("bookName")
            ?: obj.optStringSafe("book_name")
            ?: obj.optStringSafe("title")
            ?: obj.optStringSafe("name")
            ?: return null
            
        if (title.isBlank()) return null

        val tags = mutableListOf<String>()
        tags.addAll(obj.optStringArray("tags"))
        tags.addAll(obj.optStringArray("tag_list"))
        tags.addAll(obj.optTagList("tags"))

        val score = obj.optDouble("score").takeIf { it > 0 }
            ?: obj.optDouble("rating").takeIf { it > 0 }
            
        val releaseYear = obj.optInt("releaseYear").takeIf { it > 0 }
            ?: obj.optInt("year").takeIf { it > 0 }

        return BookItem(
            id = id,
            title = title,
            cover = obj.optStringSafe("coverWap") 
                ?: obj.optStringSafe("cover")
                ?: obj.optStringSafe("poster"),
            description = obj.optStringSafe("introduction") 
                ?: obj.optStringSafe("description")
                ?: obj.optStringSafe("synopsis"),
            chapterCount = obj.optInt("chapterCount").takeIf { it > 0 }
                ?: obj.optInt("chapter_count").takeIf { it > 0 }
                ?: obj.optInt("episodeCount").takeIf { it > 0 },
            tags = tags.distinct(),
            score = score,
            releaseYear = releaseYear
        )
    }

    private suspend fun fetchDetail(bookId: String): DetailData? {
        val body = runCatching {
            app.get("$mainUrl/api/shortmax/detail/$bookId").text
        }.getOrNull() ?: return null

        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        
        // Cek berbagai format response
        val data = when {
            root.has("data") -> root.optJSONObject("data")
            root.has("result") -> root.optJSONObject("result")
            else -> root
        } ?: return null

        val title = data.optStringSafe("bookName")
            ?: data.optStringSafe("title")
            ?: return null
            
        val chapters = mutableListOf<ChapterData>()
        
        // Fetch chapters dari endpoint terpisah
        val chaptersBody = runCatching {
            app.get("$mainUrl/api/shortmax/allepisode/$bookId").text
        }.getOrNull()
        
        if (chaptersBody != null) {
            parseChapters(chaptersBody, chapters)
        }

        return DetailData(
            bookId = data.optStringSafe("bookId") ?: bookId,
            title = title,
            cover = data.optStringSafe("coverWap") ?: data.optStringSafe("cover"),
            description = data.optStringSafe("introduction") ?: data.optStringSafe("description"),
            chapters = chapters,
            tags = data.optStringArray("tags"),
            score = data.optDouble("score").takeIf { it > 0 },
            releaseYear = data.optInt("releaseYear").takeIf { it > 0 }
        )
    }

    private fun parseChapters(body: String, out: MutableList<ChapterData>) {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return
        
        val chapters = when {
            root.has("data") -> root.optJSONArray("data")
            root.has("result") -> root.optJSONArray("result")
            root.has("list") -> root.optJSONArray("list")
            root.has("chapterList") -> root.optJSONArray("chapterList")
            else -> null
        } ?: return

        for (i in 0 until chapters.length()) {
            val obj = chapters.optJSONObject(i) ?: continue
            val episodeNumber = obj.optInt("chapterIndex").takeIf { it > 0 }
                ?: obj.optInt("index").takeIf { it > 0 }
                ?: (i + 1)
                
            out.add(
                ChapterData(
                    chapterId = obj.optStringSafe("chapterId"),
                    title = obj.optStringSafe("chapterName").orEmpty(),
                    episodeNumber = episodeNumber
                )
            )
        }
    }

    private fun extractBookId(url: String): String {
        val fromQuery = Regex("[?&](?:bookId|id)=([^&]+)").find(url)?.groupValues?.getOrNull(1)
        if (!fromQuery.isNullOrBlank()) return fromQuery
        
        return when {
            url.contains("/book/") -> url.substringAfterLast("/book/").substringBefore("?")
            url.contains("/") -> url.substringAfterLast("/").substringBefore("?")
            else -> url
        }
    }

    private fun JSONObject.optStringSafe(key: String): String? {
        if (!has(key)) return null
        val value = optString(key).trim()
        return value.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val value = arr.optString(i).trim()
            if (value.isNotBlank() && !value.equals("null", true)) out.add(value)
        }
        return out
    }

    private fun JSONObject.optTagList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            when (item) {
                is JSONObject -> {
                    val value = item.optStringSafe("tag_name") ?: item.optStringSafe("name")
                    if (value != null) out.add(value)
                }
                is String -> {
                    if (item.isNotBlank() && !item.equals("null", true)) out.add(item)
                }
            }
        }
        return out
    }

    private fun BookItem.toSearchResponse(): SearchResponse? {
        if (title.isBlank()) return null
        return newTvSeriesSearchResponse(
            title,
            id,
            TvType.AsianDrama
        ) {
            posterUrl = cover?.fixUrl()
        }
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

    data class LoadData(
        val bookId: String? = null,
        val chapterId: String? = null,
        val episodeNumber: Int? = null,
    )

    data class BookItem(
        val id: String,
        val title: String,
        val cover: String?,
        val description: String?,
        val chapterCount: Int?,
        val tags: List<String>,
        val score: Double? = null,
        val releaseYear: Int? = null,
    )

    data class DetailData(
        val bookId: String,
        val title: String,
        val cover: String?,
        val description: String?,
        val chapters: List<ChapterData>,
        val tags: List<String> = emptyList(),
        val score: Double? = null,
        val releaseYear: Int? = null,
    )

    data class ChapterData(
        val chapterId: String?,
        val title: String,
        val episodeNumber: Int,
    )
}

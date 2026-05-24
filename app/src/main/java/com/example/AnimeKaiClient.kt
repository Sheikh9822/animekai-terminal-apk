package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

data class AnimeCard(
    val url: String,
    val title: String,
    val thumbnailUrl: String
)

data class AnimeDetails(
    val title: String,
    val status: String,
    val studios: String,
    val genres: String,
    val synopsis: String,
    val thumbnailUrl: String,
    val url: String,
    val rating: String,
    val otherMeta: List<Pair<String, String>> = emptyList()
)

data class EpisodeCard(
    val name: String,
    val url: String, // Episode token
    val number: Float,
    val scanlator: String
)

data class VideoSource(
    val quality: String,
    val videoUrl: String,
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class SubtitleTrack(
    val label: String,
    val url: String
)

class AnimeKaiClient {
    private val tag = "AnimeKaiClient"
    
    var use_english: Boolean = true
    
    // Configurable base URL
    var baseUrl = "https://animekai.la"

        set(value) {
            field = value.trimEnd('/')
        }
        
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    private val encDecBase = "https://enc-dec.app"

    private fun getRequest(url: String, referer: String = "$baseUrl/"): Request {
        return Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .addHeader("Referer", referer)
            .build()
    }

    private fun postJsonRequest(url: String, jsonBody: String, referer: String = "$baseUrl/"): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)
        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader("User-Agent", userAgent)
            .addHeader("Referer", referer)
            .build()
    }

    private suspend fun _enc_dec(text: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "$encDecBase/api/enc-kai?text=${text}"
            val request = getRequest(url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Enc HTTP failed: ${response.code}")
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    return@withContext json.optString("result", "")
                }
                return@withContext bodyStr.trim()
            }
        } catch (e: Exception) {
            Log.e(tag, "Encryption error for $text", e)
            ""
        }
    }

    // Browse popular / trending
    suspend fun getPopular(page: Int = 1): List<AnimeCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AnimeCard>()
        try {
            val url = "$baseUrl/trending?page=$page"
            val request = getRequest(url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val elements = doc.select("div.aitem-wrapper div.aitem")
                for (el in elements) {
                    val aPoster = el.selectFirst("a.poster")
                    var animeUrl = aPoster?.attr("href")?.trim() ?: ""
                    if (animeUrl.isEmpty()) {
                        val anyA = el.select("a[href]").firstOrNull { it.attr("href").contains("/watch/") }
                        animeUrl = anyA?.attr("href") ?: ""
                    }
                    val titleEl = el.selectFirst("a.title")
                    val title = titleEl?.attr("title")?.takeIf { it.isNotEmpty() }
                        ?: titleEl?.text() ?: ""
                    val img = el.selectFirst("a.poster img")
                    val thumb = img?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: img?.attr("src") ?: ""
                    if (animeUrl.isNotEmpty() && title.isNotEmpty()) {
                        list.add(AnimeCard(animeUrl, title, thumb))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getPopular failed", e)
        }
        list
    }

    // Browse latest updates
    suspend fun getLatest(page: Int = 1): List<AnimeCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AnimeCard>()
        try {
            val url = "$baseUrl/updates?page=$page"
            val request = getRequest(url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val elements = doc.select("div.aitem-wrapper div.aitem")
                for (el in elements) {
                    val aPoster = el.selectFirst("a.poster")
                    var animeUrl = aPoster?.attr("href")?.trim() ?: ""
                    if (animeUrl.isEmpty()) {
                        val anyA = el.select("a[href]").firstOrNull { it.attr("href").contains("/watch/") }
                        animeUrl = anyA?.attr("href") ?: ""
                    }
                    val titleEl = el.selectFirst("a.title")
                    val title = titleEl?.attr("title")?.takeIf { it.isNotEmpty() }
                        ?: titleEl?.text() ?: ""
                    val img = el.selectFirst("a.poster img")
                    val thumb = img?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: img?.attr("src") ?: ""
                    if (animeUrl.isNotEmpty() && title.isNotEmpty()) {
                        list.add(AnimeCard(animeUrl, title, thumb))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getLatest failed", e)
        }
        list
    }

    // Search query
    suspend fun search(query: String, page: Int = 1): List<AnimeCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AnimeCard>()
        try {
            val url = "$baseUrl/browser?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page"
            val request = getRequest(url)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val elements = doc.select("div.aitem-wrapper div.aitem")
                for (el in elements) {
                    val aPoster = el.selectFirst("a.poster")
                    var animeUrl = aPoster?.attr("href")?.trim() ?: ""
                    if (animeUrl.isEmpty()) {
                        val anyA = el.select("a[href]").firstOrNull { it.attr("href").contains("/watch/") }
                        animeUrl = anyA?.attr("href") ?: ""
                    }
                    val titleEl = el.selectFirst("a.title")
                    val title = titleEl?.attr("title")?.takeIf { it.isNotEmpty() }
                        ?: titleEl?.text() ?: ""
                    val img = el.selectFirst("a.poster img")
                    val thumb = img?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: img?.attr("src") ?: ""
                    if (animeUrl.isNotEmpty() && title.isNotEmpty()) {
                        list.add(AnimeCard(animeUrl, title, thumb))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "search failed", e)
        }
        list
    }

    // Get details
    suspend fun getDetails(animeUrl: String): AnimeDetails = withContext(Dispatchers.IO) {
        val fullUrl = if (animeUrl.startsWith("http")) animeUrl else "$baseUrl${if (animeUrl.startsWith("/")) "" else "/"}$animeUrl"
        try {
            val request = getRequest(fullUrl)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext createFallbackDetails(animeUrl)
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                
                val posterImg = doc.selectFirst(".poster img")
                val thumb = posterImg?.attr("src") ?: ""
                
                val ratingScore = doc.selectFirst("#anime-rating")?.attr("data-score") ?: ""
                val scoreDisplay = if (ratingScore.isNotEmpty() && ratingScore != "0") "★ $ratingScore" else ""
                
                val main = doc.selectFirst("div#main-entity")
                val titleEl = main?.selectFirst("h1.title")
                val title = titleEl?.attr("title")?.takeIf { it.isNotEmpty() } ?: titleEl?.text() ?: ""
                
                val synopsis = main?.selectFirst(".desc")?.text() ?: "No synopsis."
                
                val detail = main?.selectFirst("div.detail")
                var status = "Unknown"
                var studios = "Unknown"
                var genres = "Unknown"
                val otherMeta = mutableListOf<Pair<String, String>>()
                
                if (detail != null) {
                    // Extract Studios
                    val studioDiv = detail.children().firstOrNull { it.text().startsWith("Studios:") }
                    studios = studioDiv?.select("a")?.joinToString(", ") { it.text() } ?: "Unknown"
                    
                    // Extract Genres
                    val genreDiv = detail.children().firstOrNull { it.text().startsWith("Genres:") }
                    genres = genreDiv?.select("a")?.joinToString(", ") { it.text() } ?: "Unknown"
                    
                    // Extract Status
                    val statusDiv = detail.children().firstOrNull { it.text().startsWith("Status:") }
                    status = statusDiv?.text()?.replace("Status:", "")?.trim() ?: "Unknown"
                    
                    // Meta items
                    listOf("Country:", "Premiered:", "Date aired:", "Broadcast:", "Duration:", "Rating:").forEach { key ->
                        val itemDiv = detail.children().firstOrNull { it.text().startsWith(key) }
                        if (itemDiv != null) {
                            val value = itemDiv.text().replace(key, "").trim()
                            if (value.isNotEmpty()) {
                                otherMeta.add(key.trimEnd(':') to value)
                            }
                        }
                    }
                }
                
                return@withContext AnimeDetails(
                    title = title,
                    status = status,
                    studios = studios,
                    genres = genres,
                    synopsis = synopsis,
                    thumbnailUrl = thumb,
                    url = animeUrl,
                    rating = scoreDisplay,
                    otherMeta = otherMeta
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "getDetails failed for $animeUrl", e)
        }
        createFallbackDetails(animeUrl)
    }

    private fun createFallbackDetails(url: String): AnimeDetails {
        val parts = url.split("-")
        val niceTitle = parts.dropLast(1).joinToString(" ").replaceFirstChar { it.uppercase() }
        return AnimeDetails(
            title = niceTitle.ifEmpty { "Anime Detail" },
            status = "Unknown",
            studios = "Unknown",
            genres = "Unknown",
            synopsis = "Failed to load synchronous details, but you can jack in directly to the stream feed.",
            thumbnailUrl = "",
            url = url,
            rating = ""
        )
    }

    // Get episodes
    suspend fun getEpisodes(animeUrl: String): List<EpisodeCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EpisodeCard>()
        val fullUrl = if (animeUrl.startsWith("http")) animeUrl else "$baseUrl${if (animeUrl.startsWith("/")) "" else "/"}$animeUrl"
        try {
            val request = getRequest(fullUrl)
            var html = ""
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                html = response.body?.string() ?: ""
            }
            
            // Find anime_id from inline scripts
            val regex = "\"anime_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = regex.find(html)
            val animeId = match?.groupValues?.get(1) ?: return@withContext emptyList()
            
            // Call episodes list endpoint
            val enc = _enc_dec(animeId)
            val epListUrl = "$baseUrl/ajax/episodes/list?ani_id=$animeId&_=$enc"
            val epRequest = getRequest(epListUrl)
            client.newCall(epRequest).execute().use { epResponse ->
                if (!epResponse.isSuccessful) return@withContext emptyList()
                val epJson = JSONObject(epResponse.body?.string() ?: "")
                val resultHtml = epJson.optString("result", "")
                if (resultHtml.isNotEmpty()) {
                    val doc = Jsoup.parse(resultHtml)
                    val elements = doc.select("div.eplist a")
                    for (el in elements) {
                        val token = el.attr("token").trim()
                        if (token.isEmpty()) continue
                        
                        val numStr = el.attr("num")
                        val num = numStr.toFloatOrNull() ?: 0f
                        
                        val langs = el.attr("langs").toIntOrNull() ?: 0
                        val scanlator = when (langs) {
                            1 -> "Sub"
                            3 -> "Dub & Sub"
                            else -> "Sub"
                        }
                        
                        var epName = "Episode $numStr"
                        val span = el.selectFirst("span")
                        if (span != null) {
                            val spanText = span.text().trim()
                            if (spanText.isNotEmpty() && spanText != epName) {
                                epName = "EP $numStr: $spanText"
                            }
                        }
                        list.add(EpisodeCard(epName, token, num, scanlator))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getEpisodes failed", e)
        }
        list.reversed() // Ascending order
    }

    // Get video sources/streams
    suspend fun getVideos(episodeToken: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val videosList = mutableListOf<VideoSource>()
        try {
            val enc = _enc_dec(episodeToken)
            val linksUrl = "$baseUrl/ajax/links/list?token=$episodeToken&_=$enc"
            val request = getRequest(linksUrl)
            var linksHtml = ""
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                linksHtml = JSONObject(response.body?.string() ?: "").optString("result", "")
            }
            if (linksHtml.isEmpty()) return@withContext emptyList()
            
            val doc = Jsoup.parse(linksHtml)
            val typeElements = doc.select("div.server-items[data-id]")
            
            for (typeEl in typeElements) {
                val typeId = typeEl.attr("data-id") // sub, softsub, dub
                val labelSuffix = when(typeId) {
                    "sub" -> "Hard Sub"
                    "softsub" -> "Soft Sub"
                    "dub" -> "Dub"
                    else -> typeId.uppercase()
                }
                
                val serverElements = typeEl.select("span.server[data-lid]")
                for (serverEl in serverElements) {
                    val serverLid = serverEl.attr("data-lid")
                    val serverName = serverEl.text().trim()
                    
                    try {
                        // View link call
                        val encView = _enc_dec(serverLid)
                        val viewUrl = "$baseUrl/ajax/links/view?id=$serverLid&_=$encView"
                        var encodedLink = ""
                        client.newCall(getRequest(viewUrl)).execute().use { viewResp ->
                            if (viewResp.isSuccessful) {
                                encodedLink = JSONObject(viewResp.body?.string() ?: "").optString("result", "")
                            }
                        }
                        if (encodedLink.isEmpty()) continue
                        
                        // Decrypt link via enc-dec.app
                        val payload = JSONObject().apply { put("text", encodedLink) }.toString()
                        val decRequest = postJsonRequest("$encDecBase/api/dec-kai", payload)
                        var iframeUrl = ""
                        client.newCall(decRequest).execute().use { decResp ->
                            if (decResp.isSuccessful) {
                                val decBody = decResp.body?.string() ?: ""
                                val decJson = JSONObject(decBody)
                                val resultObj = decJson.optJSONObject("result")
                                iframeUrl = resultObj?.optString("url", "") ?: ""
                            }
                        }
                        if (iframeUrl.isEmpty()) continue
                        
                        // MegaUp embed parsing
                        val iframeHost = URL(iframeUrl).host
                        val pathSegments = URL(iframeUrl).path.split("/")
                        val token = pathSegments.lastOrNull { it.isNotEmpty() } ?: continue
                        
                        // Call media token
                        val mediaUrl = "https://$iframeHost/media/$token"
                        var megaToken = ""
                        client.newCall(getRequest(mediaUrl)).execute().use { mediaResp ->
                            if (mediaResp.isSuccessful) {
                                megaToken = JSONObject(mediaResp.body?.string() ?: "").optString("result", "")
                            }
                        }
                        if (megaToken.isEmpty()) continue
                        
                        // Decrypt mega token
                        val megaPayload = JSONObject().apply {
                            put("text", megaToken)
                            put("agent", userAgent)
                        }.toString()
                        
                        val decMegaRequest = postJsonRequest("$encDecBase/api/dec-mega", megaPayload)
                        client.newCall(decMegaRequest).execute().use { decMegaResp ->
                            if (decMegaResp.isSuccessful) {
                                val bodyStr = decMegaResp.body?.string() ?: ""
                                val resObj = JSONObject(bodyStr).optJSONObject("result") ?: return@use
                                
                                // Subtitle tracks
                                val subtitles = mutableListOf<SubtitleTrack>()
                                val tracksArray = resObj.optJSONArray("tracks")
                                if (tracksArray != null) {
                                    for (i in 0 until tracksArray.length()) {
                                        val track = tracksArray.getJSONObject(i)
                                        val kind = track.optString("kind")
                                        val file = track.optString("file")
                                        val trackLabel = track.optString("label", "Unknown")
                                        if (kind == "captions" && file.endsWith(".vtt")) {
                                            subtitles.add(SubtitleTrack(trackLabel, file))
                                        }
                                    }
                                }
                                
                                // Direct media sources
                                val sourcesArray = resObj.optJSONArray("sources")
                                if (sourcesArray != null) {
                                    for (i in 0 until sourcesArray.length()) {
                                        val src_file = sourcesArray.getJSONObject(i).optString("file", "")
                                        if (src_file.isNotEmpty()) {
                                            videosList.add(
                                                VideoSource(
                                                    quality = "$serverName | [$labelSuffix]",
                                                    videoUrl = src_file,
                                                    subtitles = subtitles
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(tag, "Failed to resolve link for $serverName / $labelSuffix", ex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getVideos failed", e)
        }
        videosList
    }

    suspend fun related_anime(animeUrl: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AnimeCard>()
        val fullUrl = if (animeUrl.startsWith("http")) animeUrl else "$baseUrl${if (animeUrl.startsWith("/")) "" else "/"}$animeUrl"
        try {
            val request = getRequest(fullUrl)
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    
                    // Related/seasons selector
                    val elements = doc.select("div.aitem-col a.aitem")
                    for (el in elements) {
                        val url = el.attr("href").trim()
                        val titleEl = el.selectFirst("div.title")
                        val title = titleEl?.text() ?: ""
                        val style = el.attr("style")
                        val regex = "url\\s*\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)".toRegex()
                        val match = regex.find(style)
                        val thumb = match?.groupValues?.get(1) ?: ""
                        if (url.isNotEmpty() && title.isNotEmpty()) {
                            list.add(AnimeCard(url, title, thumb))
                        }
                    }
                    
                    val seasons = doc.select("#seasons div.season div.aitem div.inner")
                    for (s in seasons) {
                        val aTag = s.selectFirst("a")
                        val sUrl = aTag?.attr("href") ?: ""
                        val img = s.selectFirst("img")
                        val sThumb = img?.attr("src") ?: ""
                        val titleEl = s.selectFirst("div.detail span")
                        val sTitle = titleEl?.text() ?: ""
                        if (sUrl.isNotEmpty() && sTitle.isNotEmpty()) {
                            list.add(AnimeCard(sUrl, sTitle, sThumb))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "related_anime failed for $animeUrl", e)
        }
        list
    }
}


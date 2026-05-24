package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class TerminalScreen {
    BOOT,
    MAIN_MENU,
    ANIME_LIST,
    SEARCH_INPUT,
    ANIME_DETAIL,
    EPISODE_LIST,
    VIDEO_LIST,
    STREAM_ACTIONS,
    CONFIG
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TerminalApp()
        }
    }
}

// Retro Matrix Color Palette
object RetroColors {
    val GreenPrimary = Color(0xFF33FF33)   // Classic matrix phosphor green
    val GreenMuted = Color(0xFF006600)     // Dark green code
    val CyanAccent = Color(0xFF00E5FF)     // Tech cyan
    val PinkAccent = Color(0xFFE040FB)     // Glitch magenta
    val GoldWarn = Color(0xFFFFAB40)       // Alert amber
    val RedError = Color(0xFFFF1744)       // Cyber red
    val TerminalBlack = Color(0xFF070907)  // Deep cathode tube black
    val CRTShadow = Color(0x3300FF00)      // Glow shadow green
}

@Composable
fun TerminalApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Scraper Client
    val client = remember { AnimeKaiClient() }
    
    // Persisted preferred Package configurations
    val sharedPrefs = remember { context.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE) }
    var downloaderPackage by remember {
        mutableStateOf(sharedPrefs.getString("downloader_package", "idm.internet.download.manager.plus") ?: "idm.internet.download.manager.plus")
    }
    var playerPackage by remember {
        mutableStateOf(sharedPrefs.getString("player_package", "") ?: "")
    }

    val updateDownloaderPackage: (String) -> Unit = { pkg ->
        downloaderPackage = pkg
        sharedPrefs.edit().putString("downloader_package", pkg).apply()
    }

    val updatePlayerPackage: (String) -> Unit = { pkg ->
        playerPackage = pkg
        sharedPrefs.edit().putString("player_package", pkg).apply()
    }
    
    // Terminal States
    var currentScreen by remember { mutableStateOf(TerminalScreen.BOOT) }
    var bootProgress by remember { mutableStateOf(0f) }
    val bootLogs = remember { mutableStateListOf<String>() }
    
    var animeListTitle by remember { mutableStateOf("TRENDING FEED") }
    var animeListType by remember { mutableStateOf("trending") } // trending, latest, search, related
    var animePage by remember { mutableStateOf(1) }
    val animeItems = remember { mutableStateListOf<AnimeCard>() }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchInputBuffer by remember { mutableStateOf("") }
    
    var selectedAnime by remember { mutableStateOf<AnimeCard?>(null) }
    var selectedAnimeDetails by remember { mutableStateOf<AnimeDetails?>(null) }
    
    val episodeList = remember { mutableStateListOf<EpisodeCard>() }
    var episodePage by remember { mutableStateOf(1) }
    val episodesPerPage = 20
    
    var selectedEpisode by remember { mutableStateOf<EpisodeCard?>(null) }
    val videoSources = remember { mutableStateListOf<VideoSource>() }
    var selectedVideoSource by remember { mutableStateOf<VideoSource?>(null) }
    
    // Command Line Interface inputs
    var inputBuffer by remember { mutableStateOf("") }
    var isBlinkCursor by remember { mutableStateOf(true) }
    var isLoadingIntel by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("SYS: OK // PORT CONFIGURED") }
    
    // Domains list
    val domains = listOf(
        "https://animekai.la",
        "https://animekai.fo", 
        "https://animekai.fi", 
        "https://animekai.gs", 
        "https://animekai.la", 
        "https://anikai.to"
    )

    // Cursor controller blinker
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            isBlinkCursor = !isBlinkCursor
        }
    }

    // Simulated Boot loading logs
    LaunchedEffect(currentScreen) {
        if (currentScreen == TerminalScreen.BOOT && bootLogs.isEmpty()) {
            delay(300)
            bootLogs.add("◈ CORE: INITIALIZING NEURAL GLITCH LAYERS...")
            bootProgress = 0.12f
            delay(400)
            bootLogs.add("◈ SCRAPER: DISCOVERING ACTIVE NODES...")
            bootLogs.add("  - NODE FOUND: ${client.baseUrl}")
            bootProgress = 0.35f
            delay(500)
            bootLogs.add("◈ SECURITY: ESTABLISHING SECURE TUNNEL PROTOCOLS...")
            bootProgress = 0.58f
            delay(600)
            bootLogs.add("◈ NETWORK: SYNCING WITH https://enc-dec.app API...")
            bootProgress = 0.82f
            delay(400)
            bootLogs.add("◈ CLI: CALIBRATING SEARCH MATRIX CHANNELS...")
            bootProgress = 1.0f
            delay(300)
            bootLogs.add("◆ SYSTEM READY. MAIN INTERFACE DECRYPTED.")
        }
    }

    // Main network loading helpers
    val loadPopular: (Int) -> Unit = { page ->
        coroutineScope.launch {
            isLoadingIntel = true
            statusText = "SYS: FETCHING TRENDING FEED PAGE $page..."
            try {
                val res = client.getPopular(page)
                animeItems.clear()
                animeItems.addAll(res)
                animePage = page
                animeListTitle = "TRENDING FEED"
                animeListType = "trending"
                currentScreen = TerminalScreen.ANIME_LIST
                statusText = "SYS: CACHE SYNCED // TRENDING PAGE $page"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: FEED FAULT - ${e.localizedMessage}"
                Toast.makeText(context, "Transmission Error: Could not connect to domain.", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadLatest: (Int) -> Unit = { page ->
        coroutineScope.launch {
            isLoadingIntel = true
            statusText = "SYS: FETCHING LATEST CHANNELS PAGE $page..."
            try {
                val res = client.getLatest(page)
                animeItems.clear()
                animeItems.addAll(res)
                animePage = page
                animeListTitle = "LATEST UPDATES"
                animeListType = "latest"
                currentScreen = TerminalScreen.ANIME_LIST
                statusText = "SYS: CACHE SYNCED // UPDATES PAGE $page"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: FEED FAULT - ${e.localizedMessage}"
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadSearch: (String, Int) -> Unit = { query, page ->
        coroutineScope.launch {
            isLoadingIntel = true
            statusText = "SYS: RETRIEVING QUERY '$query'..."
            try {
                val res = client.search(query, page)
                animeItems.clear()
                animeItems.addAll(res)
                animePage = page
                searchQuery = query
                animeListTitle = "SEARCH: '$query'"
                animeListType = "search"
                currentScreen = TerminalScreen.ANIME_LIST
                statusText = "SYS: SEARCH SYNCED // '${query.take(15)}' PAGE $page"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: SEARCH FAILED - ${e.localizedMessage}"
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadAnimeDetails: (AnimeCard) -> Unit = { anime ->
        coroutineScope.launch {
            isLoadingIntel = true
            selectedAnime = anime
            selectedAnimeDetails = null
            statusText = "SYS: EXTRACTING INTEL OVERLAY FOR: ${anime.title.take(20)}..."
            try {
                val details = client.getDetails(anime.url)
                selectedAnimeDetails = details
                currentScreen = TerminalScreen.ANIME_DETAIL
                statusText = "SYS: METADATA PARSED // READY TO ACCESS STREAMS"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: DETAILS DECRYPTION FAULT - ${e.localizedMessage}"
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadEpisodes: (String) -> Unit = { url ->
        coroutineScope.launch {
            isLoadingIntel = true
            statusText = "SYS: RETRIEVING EPISODE MANIFEST MATRIX..."
            try {
                val eps = client.getEpisodes(url)
                episodeList.clear()
                episodeList.addAll(eps)
                episodePage = 1
                currentScreen = TerminalScreen.EPISODE_LIST
                statusText = "SYS: MANIFEST DECODED // ${eps.size} TRANSMISSION EPISODES SECURED"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: MANIFEST RETRIEVAL FAILURE - ${e.localizedMessage}"
                Toast.makeText(context, "Error: Manifest decryption failed.", Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadVideoSources: (EpisodeCard) -> Unit = { episode ->
        coroutineScope.launch {
            isLoadingIntel = true
            selectedEpisode = episode
            statusText = "SYS: JACKING IN SERVER LINK FOR EP ${episode.number}..."
            try {
                val sources = client.getVideos(episode.url)
                videoSources.clear()
                videoSources.addAll(sources)
                if (videoSources.size > 0) {
                    currentScreen = TerminalScreen.VIDEO_LIST
                    statusText = "SYS: EXTRACTED ${sources.size} LIVE STREAM SIGNALS"
                } else {
                    statusText = "SYS_FAIL: NO ACTIVE STREAM SERVERS EXTRACTED FOR THIS EPISODE"
                    Toast.makeText(context, "No streaming sources found on active nodes.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                statusText = "SYS_ERROR: STREAM EXTRACTION FAULT - ${e.localizedMessage}"
            } finally {
                isLoadingIntel = false
            }
        }
    }

    val loadRelatedAnime: (String) -> Unit = { url ->
        coroutineScope.launch {
            isLoadingIntel = true
            statusText = "SYS: SCANNING MULTIVERSE RELATED RELEASES..."
            try {
                val related = client.related_anime(url)
                animeItems.clear()
                animeItems.addAll(related)
                animePage = 1
                animeListTitle = "RELATED / SEASONS"
                animeListType = "related"
                currentScreen = TerminalScreen.ANIME_LIST
                statusText = "SYS: SYNCED RELATED FILES // ${related.size} SEASONS ACQUIRED"
            } catch (e: Exception) {
                statusText = "SYS_ERROR: RELATED ANALYSIS BLOCKED - ${e.localizedMessage}"
            } finally {
                isLoadingIntel = false
            }
        }
    }

    // CLI Input Command execution handler
    val executeCommand: (String) -> Unit = { command ->
        val cmd = command.trim().lowercase()
        inputBuffer = ""
        if (cmd.isNotEmpty()) {
            when (currentScreen) {
            TerminalScreen.MAIN_MENU -> {
                when (cmd) {
                    "1" -> loadPopular(1)
                    "2" -> loadLatest(1)
                    "3" -> {
                        searchInputBuffer = ""
                        currentScreen = TerminalScreen.SEARCH_INPUT
                    }
                    "4" -> currentScreen = TerminalScreen.CONFIG
                    "0" -> {
                        bootLogs.clear()
                        bootProgress = 0f
                        currentScreen = TerminalScreen.BOOT
                    }
                    "exit", "quit" -> {
                        statusText = "SYS: SHUTTING DOWN TERMINAL..."
                        Toast.makeText(context, "Jacked Out. Severing links.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        statusText = "SYS_FAIL: COMMAND '$cmd' NOT REGISTERED IN CHANNELS"
                    }
                }
            }
            TerminalScreen.ANIME_LIST -> {
                when (cmd) {
                    "n" -> loadPopular(animePage + 1)
                    "p" -> if (animePage > 1) {
                        if (animeListType == "trending") loadPopular(animePage - 1)
                        else if (animeListType == "latest") loadLatest(animePage - 1)
                        else if (animeListType == "search") loadSearch(searchQuery, animePage - 1)
                    } else {
                        statusText = "SYS_FAIL: ALREADY ON ROOT MEMORY BLOCK (PAGE 1)"
                    }
                    "0" -> currentScreen = TerminalScreen.MAIN_MENU
                    else -> {
                        val idx = cmd.toIntOrNull()
                        if (idx != null && idx in 1..animeItems.size) {
                            loadAnimeDetails(animeItems[idx - 1])
                        } else {
                            statusText = "SYS_FAIL: INVALID ACCESS SECTOR INDEX"
                        }
                    }
                }
            }
            TerminalScreen.SEARCH_INPUT -> {
                if (cmd == "0") {
                    currentScreen = TerminalScreen.MAIN_MENU
                } else {
                    loadSearch(command, 1)
                }
            }
            TerminalScreen.ANIME_DETAIL -> {
                when (cmd) {
                    "e" -> {
                        selectedAnime?.let { loadEpisodes(it.url) }
                    }
                    "r" -> {
                        selectedAnime?.let { loadRelatedAnime(it.url) }
                    }
                    "0" -> {
                        currentScreen = TerminalScreen.ANIME_LIST
                    }
                    else -> {
                        statusText = "SYS_FAIL: DIRECTIVE UNRESOLVED"
                    }
                }
            }
            TerminalScreen.EPISODE_LIST -> {
                val startIdx = (episodePage - 1) * episodesPerPage
                val endIdx = (startIdx + episodesPerPage).coerceAtMost(episodeList.size)
                val activeList = episodeList.subList(startIdx, endIdx)
                
                when (cmd) {
                    "n" -> {
                        if (endIdx < episodeList.size) {
                            episodePage++
                        }
                    }
                    "p" -> {
                        if (episodePage > 1) {
                            episodePage--
                        }
                    }
                    "0" -> {
                        currentScreen = TerminalScreen.ANIME_DETAIL
                    }
                    else -> {
                        val idx = cmd.toIntOrNull()
                        if (idx != null && idx in 1..activeList.size) {
                            loadVideoSources(activeList[idx - 1])
                        } else {
                            statusText = "SYS_FAIL: INDEX EXCEEDS TRANSLATOR STACK SIZE"
                        }
                    }
                }
            }
            TerminalScreen.VIDEO_LIST -> {
                when (cmd) {
                    "0" -> {
                        currentScreen = TerminalScreen.EPISODE_LIST
                    }
                    else -> {
                        val idx = cmd.toIntOrNull()
                        if (idx != null && idx in 1..videoSources.size) {
                            selectedVideoSource = videoSources[idx - 1]
                            currentScreen = TerminalScreen.STREAM_ACTIONS
                            statusText = "SYS: CORE LOCKED IN ON CH-${idx} READY FOR LINK INJECT"
                        } else {
                            statusText = "SYS_FAIL: CH-OUT OF RANGE"
                        }
                    }
                }
            }
            TerminalScreen.STREAM_ACTIONS -> {
                when (cmd) {
                    "1", "d", "download" -> {
                        selectedVideoSource?.let { downloadExternally(context, it.videoUrl, downloaderPackage) }
                    }
                    "2", "s", "stream", "watch" -> {
                        selectedVideoSource?.let { watchInExternalPlayer(context, it.videoUrl, playerPackage) }
                    }
                    "3", "c", "copy" -> {
                        selectedVideoSource?.let { copyToClipboard(context, it.videoUrl) }
                    }
                    "0" -> {
                        currentScreen = TerminalScreen.VIDEO_LIST
                    }
                    else -> {
                        statusText = "SYS_FAIL: COMMAND OUT OF CHANNELS"
                    }
                }
            }
            TerminalScreen.CONFIG -> {
                when (cmd) {
                    "1", "2", "3", "4", "5", "6" -> {
                        val idx = cmd.toInt() - 1
                        if (idx in domains.indices) {
                            client.baseUrl = domains[idx]
                            statusText = "SYS: CONFIGURED NODE -> ${client.baseUrl}"
                            currentScreen = TerminalScreen.MAIN_MENU
                        }
                    }
                    "t" -> {
                        client.use_english = !client.use_english
                        statusText = "SYS: TOGGLED LANGUAGE. ENGLISH: ${client.use_english}"
                    }
                    "0" -> currentScreen = TerminalScreen.MAIN_MENU
                }
            }
            else -> {}
        }
    }
}

    // Direct Tap on choices immediately executes the corresponding command
    val sendInteractiveKey: (String) -> Unit = { key ->
        inputBuffer = ""
        executeCommand(key)
    }

    // Material 3 Surface Frame styled with Scanlines
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .drawWithContent {
                    drawContent()
                    // CRT Scanlines design implementation for extreme retro fidelity
                    val lineSpacing = 5.dp.toPx()
                    val lineColor = Color(0xFF33FF33).copy(alpha = 0.04f)
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                        y += lineSpacing
                    }
                    // Cyber glow corner aesthetic accents
                    val glowColor = Color(0xFF33FF33).copy(alpha = 0.05f)
                    drawRect(color = glowColor, size = size)
                },
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // top header
                HeaderSection(
                    baseUrl = client.baseUrl,
                    statusText = statusText,
                    currentScreen = currentScreen,
                    isLoading = isLoadingIntel
                )

                // screen router wrapper with nice matrix glow card border
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, RetroColors.GreenPrimary, RoundedCornerShape(4.dp))
                        .background(RetroColors.TerminalBlack)
                        .padding(8.dp)
                ) {
                    if (isLoadingIntel) {
                        IntelSpinnerOverlay()
                    } else {
                        when (currentScreen) {
                            TerminalScreen.BOOT -> BootScreen(
                                logs = bootLogs,
                                progress = bootProgress,
                                onEnterPressed = { currentScreen = TerminalScreen.MAIN_MENU }
                            )
                            TerminalScreen.MAIN_MENU -> MainMenuScreen(
                                onOptionSelected = sendInteractiveKey
                            )
                            TerminalScreen.ANIME_LIST -> AnimeListScreen(
                                title = animeListTitle,
                                page = animePage,
                                items = animeItems,
                                onSelectAnime = { anime -> loadAnimeDetails(anime) },
                                onCommand = sendInteractiveKey
                            )
                            TerminalScreen.SEARCH_INPUT -> SearchInputScreen(
                                initialValue = searchInputBuffer,
                                onValueChange = { searchInputBuffer = it },
                                onCancel = { currentScreen = TerminalScreen.MAIN_MENU },
                                onSearch = { loadSearch(it, 1) }
                            )
                            TerminalScreen.ANIME_DETAIL -> AnimeDetailScreen(
                                details = selectedAnimeDetails ?: createFallbackDetails(selectedAnime?.url ?: ""),
                                onCommand = sendInteractiveKey
                            )
                            TerminalScreen.EPISODE_LIST -> EpisodeListScreen(
                                animeTitle = selectedAnime?.title ?: "Manifest",
                                episodeList = episodeList,
                                page = episodePage,
                                itemsPerPage = episodesPerPage,
                                onSelectEpisode = { loadVideoSources(it) },
                                onCommand = sendInteractiveKey
                            )
                            TerminalScreen.VIDEO_LIST -> VideoLinksScreen(
                                episodeName = selectedEpisode?.name ?: "EP",
                                sources = videoSources,
                                onSelectSource = { source ->
                                    selectedVideoSource = source
                                    currentScreen = TerminalScreen.STREAM_ACTIONS
                                    statusText = "SYS: INJECTION MATRIX SEALED FOR STREAMS"
                                },
                                onCommand = sendInteractiveKey
                            )
                            TerminalScreen.STREAM_ACTIONS -> StreamActionsScreen(
                                episodeName = selectedEpisode?.name ?: "EP",
                                animeTitle = selectedAnime?.title ?: "Stream Inject",
                                source = selectedVideoSource ?: VideoSource("NULL", ""),
                                onCommand = sendInteractiveKey
                            )
                            TerminalScreen.CONFIG -> ConfigurationScreen(
                                domains = domains,
                                activeDomain = client.baseUrl,
                                isEnglish = client.use_english,
                                downloaderPackage = downloaderPackage,
                                playerPackage = playerPackage,
                                onUpdateDownloaderPackage = updateDownloaderPackage,
                                onUpdatePlayerPackage = updatePlayerPackage,
                                onSelectNode = sendInteractiveKey,
                                onToggleLang = {
                                    sendInteractiveKey("t")
                                }
                            )
                        }
                    }
                }

                // CLI Keyboard input console + direct hotkeys row
                FooterConsole(
                    inputBuffer = inputBuffer,
                    onInputChange = { inputBuffer = it },
                    isBlinkCursor = isBlinkCursor,
                    onSend = {
                        executeCommand(inputBuffer)
                    },
                    currentScreen = currentScreen,
                    onHotkeyTapped = sendInteractiveKey
                )
            }
        }
    }
}

// ── RETRO TERMINAL DESIGNS ────────────────────────────────────────────────

@Composable
fun HeaderSection(baseUrl: String, statusText: String, currentScreen: TerminalScreen, isLoading: Boolean) {
    val dateString = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◈ ANIMEKAI-NET CLI [NODE: ${baseUrl.replace("https://", "")}]",
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SYS: ${if (isLoading) "BUSY..." else "READY"}",
                color = if (isLoading) RetroColors.GoldWarn else RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Status bar line styled with vintage dashes
        Text(
            text = "▣ $statusText",
            color = if (statusText.contains("SYS_ERROR") || statusText.contains("SYS_FAIL")) RetroColors.RedError else RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)) {
            drawLine(
                color = RetroColors.GreenPrimary.copy(alpha = 0.5f),
                start = Offset(0f, 2f),
                end = Offset(size.width, 2f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun BootScreen(logs: List<String>, progress: Float, onEnterPressed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // High fidelity ASCII art logo display
            Text(
                text = """
  ▄▄▄       ███▄    █  ██▓ ███▄ ▄███▓▓█████  ██ ▄█▀ ▄▄▄       ██▓
 ▒████▄     ██ ▀█   █ ▓██▒▓██▒▀█▀ ██▒▓█   ▀  ██▄█▒ ▒████▄    ▓██▒
 ▒██  ▀█▄  ▓██  ▀█ ██▒▒██▒▓██    ▓██░▒███   ▓███▄░ ▒██  ▀█▄  ▒██▒
 ░██▄▄▄▄██ ▓██▒  ▐▌██▒░██░▒██    ▒██ ▒▓█  ▄ ▓██ █▄ ░██▄▄▄▄██ ░██░
  ▓█   ▓██▒▒██░   ▓██░░██░▒██▒   ░██▒░▒████▒▒██▒ █▄ ▓█   ▓██▒░██░
  ▒▒   ▓▒█░░ ▒░   ▒ ▒ ░▓  ░ ▒░   ░  ░░░ ▒░ ░▒ ▒▒ ▓▒ ▒▒   ▓▒█░░▓  
   ▒   ▒▒ ░░ ░░   ░ ▒░ ▒ ░░  ░      ░ ░ ░  ░░ ░▒ ▒░  ▒   ▒▒ ░ ▒ ░
""".trimIndent(),
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                lineHeight = 9.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "───────────────────────────────────────────────",
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "[ NEURAL STREAM ACCESS // VER 2.0.77 // DECRYPTED ]",
                color = RetroColors.CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "───────────────────────────────────────────────",
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Console loading entries
            logs.forEach { log ->
                Text(
                    text = log,
                    color = if (log.startsWith("◆")) RetroColors.CyanAccent else RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Simulated loading cylinder
            val sizeCylinder = 20
            val loadedBlocks = (progress * sizeCylinder).toInt()
            val cylinderString = "▰".repeat(loadedBlocks) + "▱".repeat(sizeCylinder - loadedBlocks)
            
            Text(
                text = "MATRIX INJECTION: [ $cylinderString ] ${(progress * 100).toInt()}%",
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (progress >= 1.0f) {
                Button(
                    onClick = onEnterPressed,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, RetroColors.GreenPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "▸ PRESS ENTER TO JACK IN NOW ◂",
                        color = RetroColors.PinkAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MainMenuScreen(onOptionSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "◈  M A I N   T E R M I N A L  ◈",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        
        val options = listOf(
            "1" to "TRENDING FEED     ◈  HOT SIGNALS",
            "2" to "LATEST UPDATES    ◈  NEW RELEASES",
            "3" to "SEARCH DATABASE   ◈  QUERY MATRIX",
            "4" to "SYSTEM CONFIG     ◈  TUNE CHANNELS",
            "0" to "REBOOT TERMINAL   ◈  HARD RE-SYNC"
        )
        
        options.forEach { (key, label) ->
            Button(
                onClick = { onOptionSelected(key) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.GreenPrimary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = " [ $key ]  ",
                        color = RetroColors.PinkAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = label,
                        color = RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AnimeListScreen(
    title: String,
    page: Int,
    items: List<AnimeCard>,
    onSelectAnime: (AnimeCard) -> Unit,
    onCommand: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◈ $title ◈",
                color = RetroColors.CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PAGE $page",
                color = RetroColors.GoldWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◆ SIGNAL IS EMPTY FOR THIS MATRIX ◆\nNo records extracted. Try another node config.",
                    color = RetroColors.RedError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(items) { index, anime ->
                    val cliIndex = index + 1
                    Button(
                        onClick = { onSelectAnime(anime) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[%02d] ".format(cliIndex),
                                color = RetroColors.PinkAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = anime.title,
                                color = RetroColors.GreenPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "▸ JACK IN",
                                color = RetroColors.CyanAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Pagination buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onCommand("p") },
                enabled = page > 1,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (page > 1) RetroColors.GreenPrimary else RetroColors.GreenMuted),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ P ] PREV PAGE",
                    color = if (page > 1) RetroColors.GreenPrimary else RetroColors.GreenMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "ITEMS: ${items.size}",
                color = RetroColors.GreenPrimary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Button(
                onClick = { onCommand("n") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.GreenPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ N ] NEXT PAGE",
                    color = RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SearchInputScreen(
    initialValue: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "◈ INTEL MATRIX QUERY ◈",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Enter keyword below to probe channels:",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = initialValue,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroColors.GreenPrimary,
                unfocusedBorderColor = RetroColors.GreenMuted,
                focusedContainerColor = Color(0x3300FF00),
                unfocusedContainerColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            placeholder = {
                Text(
                    text = "e.g. One Piece, Naruto...",
                    color = RetroColors.GreenMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(4.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.RedError),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ 0 ] CANCEL",
                    color = RetroColors.RedError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = { if (initialValue.trim().isNotEmpty()) onSearch(initialValue) },
                enabled = initialValue.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (initialValue.trim().isNotEmpty()) RetroColors.GreenPrimary else RetroColors.GreenMuted),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ ↵ ] SEARCH",
                    color = if (initialValue.trim().isNotEmpty()) RetroColors.GreenPrimary else RetroColors.GreenMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AnimeDetailScreen(details: AnimeDetails, onCommand: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "◈ INTEL FILE // ${details.title.uppercase()} ◈",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Metadata grid styled in a table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            MetaRow("STATUS", details.status)
            MetaRow("STUDIOS", details.studios)
            MetaRow("GENRES", details.genres)
            if (details.rating.isNotEmpty()) {
                MetaRow("RATING", details.rating)
            }
            details.otherMeta.forEach { (k, v) ->
                MetaRow(k.uppercase(), v)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Synopsis panel
        Text(
            text = "◈ SYNOPSIS DATA ◈",
            color = RetroColors.PinkAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = details.synopsis,
            color = RetroColors.GreenPrimary.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.3f))
                .padding(6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Choice commands
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onCommand("e") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.GreenPrimary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = " [ E ] EPISODE MANIFEST DECRYPTOR",
                    color = RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { onCommand("r") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.CyanAccent),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = " [ R ] SEASONS & RELATED TITLES",
                    color = RetroColors.CyanAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { onCommand("0") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, RetroColors.RedError),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = " [ 0 ] BACK TO CHANNELS LIST",
                    color = RetroColors.RedError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "  %-10s : ".format(label),
            color = RetroColors.PinkAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpisodeListScreen(
    animeTitle: String,
    episodeList: List<EpisodeCard>,
    page: Int,
    itemsPerPage: Int,
    onSelectEpisode: (EpisodeCard) -> Unit,
    onCommand: (String) -> Unit
) {
    val totalPages = (episodeList.size + itemsPerPage - 1) / itemsPerPage
    val startIdx = (page - 1) * itemsPerPage
    val endIdx = (startIdx + itemsPerPage).coerceAtMost(episodeList.size)
    val displayedEpisodes = if (episodeList.isEmpty()) emptyList() else episodeList.subList(startIdx, endIdx)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◈ ${animeTitle.take(20).uppercase()} ESP ◈",
                color = RetroColors.CyanAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PAGE $page / $totalPages",
                color = RetroColors.GoldWarn,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (displayedEpisodes.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No episodes detected on this node.",
                    color = RetroColors.RedError,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(displayedEpisodes) { index, ep ->
                    val cliIndex = index + 1
                    Button(
                        onClick = { onSelectEpisode(ep) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[%02d] ".format(cliIndex),
                                    color = RetroColors.PinkAccent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ep.name,
                                    color = RetroColors.GreenPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "[${ep.scanlator}]",
                                color = if (ep.scanlator.contains("Dub")) RetroColors.GoldWarn else RetroColors.CyanAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Episode List Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onCommand("p") },
                enabled = page > 1,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (page > 1) RetroColors.GreenPrimary else RetroColors.GreenMuted),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ P ] PREV EPs",
                    color = if (page > 1) RetroColors.GreenPrimary else RetroColors.GreenMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "EPISODES: ${episodeList.size}",
                color = RetroColors.GreenPrimary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Button(
                onClick = { onCommand("n") },
                enabled = page < totalPages,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (page < totalPages) RetroColors.GreenPrimary else RetroColors.GreenMuted),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "[ N ] NEXT EPs",
                    color = if (page < totalPages) RetroColors.GreenPrimary else RetroColors.GreenMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun VideoLinksScreen(
    episodeName: String,
    sources: List<VideoSource>,
    onSelectSource: (VideoSource) -> Unit,
    onCommand: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "◈ STREAM EXTRACTOR // $episodeName ◈",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(sources) { idx, v ->
                val cliIndex = idx + 1
                val subCount = v.subtitles.size
                val subBadge = if (subCount > 0) "($subCount sub track${if (subCount > 1) "s" else ""})" else "No Sub"
                
                Button(
                    onClick = { onSelectSource(v) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[%02d] ".format(cliIndex),
                                color = RetroColors.PinkAccent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = v.quality,
                                color = RetroColors.GreenPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = subBadge,
                            color = RetroColors.CyanAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { onCommand("0") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.RedError),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "[ 0 ] BACK TO EPISODE MANIFEST",
                color = RetroColors.RedError,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun StreamActionsScreen(
    episodeName: String,
    animeTitle: String,
    source: VideoSource,
    onCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "◈ DEC DECRYPTOR LOCKED IN ◈",
            color = RetroColors.PinkAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            MetaRow("ANIME", animeTitle)
            MetaRow("EPISODE", episodeName)
            MetaRow("CHANNEL", source.quality)
            MetaRow("URL LINK", source.videoUrl.take(45) + "...")
            MetaRow("TRACKS", "${source.subtitles.size} Subtitle Track${if (source.subtitles.size != 1) "s" else ""}")
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "◆ RESOLVED DIRECTIVES ◆",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Button(
            onClick = { onCommand("2") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.GreenPrimary),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(11.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = "[ 2 ] STREAM IN EXTERNAL VIDEO PLAYER",
                    color = RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = { onCommand("1") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.PinkAccent),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(11.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = "[ 1 ] DOWNLOAD EXTERNALLY (ADM / BROWSER)",
                    color = RetroColors.PinkAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = { onCommand("3") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.CyanAccent),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(11.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Text(
                    text = "[ 3 ] COPY DIRECT STREAM LINK",
                    color = RetroColors.CyanAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { onCommand("0") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.RedError),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "[ 0 ] BACK TO SERVERS LINKS",
                color = RetroColors.RedError,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ConfigurationScreen(
    domains: List<String>,
    activeDomain: String,
    isEnglish: Boolean,
    downloaderPackage: String,
    playerPackage: String,
    onUpdateDownloaderPackage: (String) -> Unit,
    onUpdatePlayerPackage: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    onToggleLang: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "◈ TUNING SYSTEMS CONFIG ◈",
            color = RetroColors.CyanAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = "Select active AnimeKai Node Proxy:",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        domains.forEachIndexed { i, domain ->
            val num = i + 1
            val isActive = domain == activeDomain
            Button(
                onClick = { onSelectNode(num.toString()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, if (isActive) RetroColors.PinkAccent else RetroColors.GreenPrimary),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "[ $num ] $domain",
                        color = if (isActive) RetroColors.PinkAccent else RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    if (isActive) {
                        Text(
                            text = "◈ ACTIVE CONNECTOR",
                            color = RetroColors.PinkAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Manage display parsing:",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Button(
            onClick = onToggleLang,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.CyanAccent),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[ T ] TITLE LANGUAGE PARSING: " + if (isEnglish) "ENGLISH" else "ROMAJI",
                    color = RetroColors.CyanAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "◈ EXTERNAL PLAYER & DOWNLOADER ◈",
            color = RetroColors.PinkAccent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = "Select active Downloader package:",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "System Default" to "",
                "1DM+" to "idm.internet.download.manager.plus",
                "1DM" to "idm.internet.download.manager"
            )
            presets.forEach { (name, value) ->
                val selected = downloaderPackage == value
                Button(
                    onClick = { onUpdateDownloaderPackage(value) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0x3300FF00) else Color.Transparent),
                    border = BorderStroke(1.dp, if (selected) RetroColors.PinkAccent else RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = name,
                        color = if (selected) RetroColors.PinkAccent else RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = downloaderPackage,
            onValueChange = { onUpdateDownloaderPackage(it.trim()) },
            textStyle = TextStyle(
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            label = {
                Text(
                    text = "Custom Downloader Package ID",
                    color = RetroColors.GreenPrimary.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroColors.GreenPrimary,
                unfocusedBorderColor = RetroColors.GreenPrimary.copy(alpha = 0.4f),
                focusedContainerColor = Color(0x1100FF00),
                unfocusedContainerColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = "e.g. idm.internet.download.manager.plus",
                    color = RetroColors.GreenPrimary.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Select active Video Player package:",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(
                "System Default" to "",
                "VLC" to "org.videolan.vlc",
                "MX Player" to "com.mxtech.videoplayer.ad",
                "Just Player" to "com.brouken.player"
            )
            presets.forEach { (name, value) ->
                val selected = playerPackage == value
                Button(
                    onClick = { onUpdatePlayerPackage(value) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0x3300FF00) else Color.Transparent),
                    border = BorderStroke(1.dp, if (selected) RetroColors.PinkAccent else RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = name,
                        color = if (selected) RetroColors.PinkAccent else RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = playerPackage,
            onValueChange = { onUpdatePlayerPackage(it.trim()) },
            textStyle = TextStyle(
                color = RetroColors.GreenPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            label = {
                Text(
                    text = "Custom Player Package ID",
                    color = RetroColors.GreenPrimary.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RetroColors.GreenPrimary,
                unfocusedBorderColor = RetroColors.GreenPrimary.copy(alpha = 0.4f),
                focusedContainerColor = Color(0x1100FF00),
                unfocusedContainerColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = "e.g. org.videolan.vlc",
                    color = RetroColors.GreenPrimary.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { onSelectNode("0") },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, RetroColors.RedError),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "[ 0 ] BACK TO TERMINAL MENU",
                color = RetroColors.RedError,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun FooterConsole(
    inputBuffer: String,
    onInputChange: (String) -> Unit,
    isBlinkCursor: Boolean,
    onSend: () -> Unit,
    currentScreen: TerminalScreen,
    onHotkeyTapped: (String) -> Unit
) {
    Column {
        // Fast access tactile row of useful hotkeys
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val hotkeys = when (currentScreen) {
                TerminalScreen.MAIN_MENU -> listOf("1" to "TRENDING", "2" to "LATEST", "3" to "SEARCH", "4" to "CONFIG")
                TerminalScreen.ANIME_LIST -> listOf("0" to "MENU", "p" to "PREV", "n" to "NEXT")
                TerminalScreen.SEARCH_INPUT -> listOf("0" to "CANCEL")
                TerminalScreen.ANIME_DETAIL -> listOf("0" to "CHANNELS", "e" to "EPISODES", "r" to "RELATED")
                TerminalScreen.EPISODE_LIST -> listOf("0" to "INFO", "p" to "PREV", "n" to "NEXT")
                TerminalScreen.VIDEO_LIST -> listOf("0" to "EPISODES")
                TerminalScreen.STREAM_ACTIONS -> listOf("0" to "SERVERS", "2" to "STREAM", "1" to "DOWNLOAD", "3" to "COPY")
                TerminalScreen.CONFIG -> listOf("0" to "MENU", "t" to "LANG")
                else -> emptyList()
            }
            
            hotkeys.forEach { (key, title) ->
                Button(
                    onClick = { onHotkeyTapped(key) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x2200FF00)),
                    border = BorderStroke(1.dp, RetroColors.GreenPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "[$key] $title",
                        color = RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Command Prompt typing interaction line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, RetroColors.GreenPrimary, RoundedCornerShape(4.dp))
                .background(RetroColors.TerminalBlack)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMMAND > ",
                    color = RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Simulated interactive terminal command buffer text row
                BasicTextField(
                    value = inputBuffer,
                    onValueChange = onInputChange,
                    textStyle = TextStyle(
                        color = RetroColors.GreenPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { onSend() }
                    )
                )
                
                // Blinking cathode block cursor
                Text(
                    text = if (isBlinkCursor) "█" else " ",
                    color = RetroColors.GreenPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
            
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "[↵ SEND]",
                    color = RetroColors.PinkAccent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun IntelSpinnerOverlay() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = RetroColors.GreenPrimary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "DECRYPTING NODE DATAFRAME PROTOCOLS..." + "\n" + "Sys: Please stand by for feed matrix.",
            color = RetroColors.GreenPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── UTILITIES AND LAUNCHERS ────────────────────────────────────────────────

fun watchInExternalPlayer(context: Context, videoUrl: String, playerPackage: String) {
    try {
        val uri = Uri.parse(videoUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (playerPackage.isNotEmpty()) {
            intent.setPackage(playerPackage)
            context.startActivity(intent)
        } else {
            val chooser = Intent.createChooser(intent, "Watch stream with external player")
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        if (playerPackage.isNotEmpty()) {
            Toast.makeText(context, "Pkg '$playerPackage' not found! Falling back to standard player list.", Toast.LENGTH_SHORT).show()
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(videoUrl), "video/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val chooser = Intent.createChooser(intent, "Watch stream with external player")
                context.startActivity(chooser)
            } catch (ex: Exception) {
                copyToClipboard(context, videoUrl)
            }
        } else {
            Toast.makeText(context, "No external player. Clipboard locked on link instead.", Toast.LENGTH_SHORT).show()
            copyToClipboard(context, videoUrl)
        }
    }
}

fun downloadExternally(context: Context, videoUrl: String, downloaderPackage: String) {
    try {
        val uri = Uri.parse(videoUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (downloaderPackage.isNotEmpty()) {
            intent.setPackage(downloaderPackage)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        if (downloaderPackage.isNotEmpty()) {
            Toast.makeText(context, "Pkg '$downloaderPackage' not found! Falling back to browser.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Opening browser download node fallback...", Toast.LENGTH_SHORT).show()
        }
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (ex: Exception) {
            copyToClipboard(context, videoUrl)
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Direct Stream URL", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Link Copied: Intel payload secured in clipboard.", Toast.LENGTH_SHORT).show()
}

private fun createFallbackDetails(url: String?): AnimeDetails {
    val nonNullUrl = url ?: "channel"
    val parts = nonNullUrl.split("/")
    val last = parts.lastOrNull { it.isNotEmpty() } ?: "Anime"
    val nicetitle = last.replace("-", " ").replaceFirstChar { it.uppercase() }
    return AnimeDetails(
        title = nicetitle,
        status = "Unknown",
        studios = "Unknown",
        genres = "Unknown",
        synopsis = "Metadata parsed from source node. Click manifest decryptor below to extract full episode stream tracks.",
        thumbnailUrl = "",
        url = nonNullUrl,
        rating = ""
    )
}

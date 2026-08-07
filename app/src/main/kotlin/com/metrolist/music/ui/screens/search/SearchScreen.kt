/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.LocalIsPlayerExpanded
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.SearchSource
import com.metrolist.music.constants.SearchSourceKey
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraFloatingChromeButton
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraTopBar
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.utils.RecentSearchesStore
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.viewmodels.SearchHubViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val SearchBrowseFallbackColors =
    listOf(
        Color(0xFFE13300),
        Color(0xFF7358FF),
        Color(0xFF1DB954),
        Color(0xFF148A08),
        Color(0xFFE8115B),
        Color(0xFFBC5900),
        Color(0xFF8D67AB),
        Color(0xFF503750),
        Color(0xFF477D95),
        Color(0xFFAF2896),
        Color(0xFF8C67AC),
        Color(0xFFE91429),
    )

private val SearchHubFieldColor = Color(0xFF2A2A2A)

private val SearchBrowseTileHeight = 100.dp

private fun stripeColorToCompose(stripeColor: Long): Color? {
    val argb = stripeColor and 0xFFFFFFFFL
    if (argb == 0L) return null
    val color = Color(argb.toInt())
    return if (color.alpha <= 0f) null else color
}

private fun browseCategoryColor(
    item: MoodAndGenres.Item,
    index: Int,
): Color = stripeColorToCompose(item.stripeColor) ?: SearchBrowseFallbackColors[index % SearchBrowseFallbackColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    pureBlack: Boolean,
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val hubListState = rememberLazyListState()
    val canvasColor = if (pureBlack) Color.Black else Color(0xFF121212)

    val scrollToTopCount by savedStateHandle
        .getStateFlow("scrollToTopCount", 0)
        .collectAsStateWithLifecycle(initialValue = 0)
    val focusSearchFieldCount by savedStateHandle
        .getStateFlow("focusSearchField", 0)
        .collectAsStateWithLifecycle(initialValue = 0)

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusSearchCount by rememberSaveable { mutableIntStateOf(0) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    // Keep shell profile chrome on the browse hub; hide it while typing.
    LaunchedEffect(isSearchActive) {
        savedStateHandle["searchActive"] = isSearchActive
    }
    DisposableEffect(Unit) {
        onDispose {
            savedStateHandle["searchActive"] = false
        }
    }

    fun exitSearchMode() {
        isSearchActive = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BackHandler(enabled = isSearchActive) {
        exitSearchMode()
    }

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            if (isSearchActive) {
                exitSearchMode()
            } else {
                hubListState.animateScrollToItem(0)
            }
        }
    }

    // Bottom-nav Search reselect (double-tap when already on Search) → focus the bar.
    LaunchedEffect(focusSearchFieldCount) {
        if (focusSearchFieldCount > lastFocusSearchCount) {
            lastFocusSearchCount = focusSearchFieldCount
            isSearchActive = true
            kotlinx.coroutines.delay(50)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isSearchActive, isPlayerExpanded) {
        if (isSearchActive && !isPlayerExpanded) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }
        }
    }

    fun handleSearch(searchQuery: String) {
        if (searchQuery.isEmpty()) {
            return
        }

        focusManager.clearFocus()

        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = parsedUrl.id),
                    ),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                navController.navigate(SearchRoutes.resultRoute(searchQuery))
                coroutineScope.launch(Dispatchers.IO) {
                    RecentSearchesStore.addQuery(context, searchQuery)
                }
            }
        }
    }

    val onSearch: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }
    val onSearchFromSuggestion: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    if (isSearchActive) {
        Scaffold(
            topBar = {
                AuraTopBar(
                    floatTitle = false,
                    title = {
                        val searchPillShape = RoundedCornerShape(percent = 50)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .auraFloatingIsland(
                                        shape = searchPillShape,
                                        color = AuraElevated,
                                        elevation = 5.dp,
                                    )
                                    .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(18.dp),
                            )
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .focusRequester(focusRequester),
                                textStyle =
                                    TextStyle(
                                        color = Color.White,
                                        fontSize = 16.sp,
                                    ),
                                cursorBrush = SolidColor(AuraSpotifyGreen),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (query.text.isEmpty()) {
                                        Text(
                                            text =
                                                stringResource(
                                                    when (searchSource) {
                                                        SearchSource.LOCAL -> R.string.search_library
                                                        SearchSource.ONLINE -> R.string.search_what_do_you_want
                                                    },
                                                ),
                                            style =
                                                TextStyle(
                                                    color = Color.White.copy(alpha = 0.45f),
                                                    fontSize = 16.sp,
                                                ),
                                        )
                                    }
                                    innerTextField()
                                },
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onSearch = { onSearch(query.text) },
                                    ),
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (query.text.isNotEmpty()) {
                                    AuraFloatingChromeButton(
                                        onClick = { query = TextFieldValue("") },
                                        size = 32.dp,
                                        contentDescription = stringResource(R.string.dismiss),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                AuraFloatingChromeButton(
                                    onClick = {
                                        searchSource =
                                            if (searchSource == SearchSource.ONLINE) {
                                                SearchSource.LOCAL
                                            } else {
                                                SearchSource.ONLINE
                                            }
                                    },
                                    size = 32.dp,
                                ) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> R.drawable.library_music
                                                    SearchSource.ONLINE -> R.drawable.language
                                                },
                                            ),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        AuraFloatingChromeButton(
                            onClick = { exitSearchMode() },
                            contentDescription = stringResource(R.string.back),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        AuraFloatingChromeButton(
                            onClick = { navController.navigate("recognition") },
                            contentDescription = stringResource(R.string.recognize_music),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.mic),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                        ),
                )
            },
            containerColor = canvasColor,
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxSize(),
            ) {
                when (searchSource) {
                    SearchSource.LOCAL -> {
                        LocalSearchScreen(
                            query = query.text,
                            onDismiss = { exitSearchMode() },
                            pureBlack = pureBlack,
                        )
                    }

                    SearchSource.ONLINE -> {
                        OnlineSearchScreen(
                            query = query.text,
                            onQueryChange = { query = it },
                            onSearch = onSearchFromSuggestion,
                            onDismiss = { /* Keep active while picking suggestions */ },
                            pureBlack = pureBlack,
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(canvasColor),
        ) {
            SearchBrowseHub(
                listState = hubListState,
                onActivateSearch = { isSearchActive = true },
                onCategoryClick = { browseId, params ->
                    navController.navigate("youtube_browse/$browseId?params=$params")
                },
                viewModel = hiltViewModel(),
            )
        }
    }

    DisposableEffect(lifecycleOwner, isPlayerExpanded, isSearchActive) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isPlayerExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun SearchBrowseHub(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onActivateSearch: () -> Unit,
    onCategoryClick: (browseId: String, params: String?) -> Unit,
    viewModel: SearchHubViewModel = hiltViewModel(),
) {
    val configuration = LocalConfiguration.current
    val columns =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            3
        } else {
            2
        }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "search_field") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 24.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(SearchHubFieldColor)
                        .clickable(onClick = onActivateSearch)
                        .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.search_what_do_you_want),
                    style =
                        TextStyle(
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 16.sp,
                        ),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        item(key = "browse_all_title") {
            Text(
                text = stringResource(R.string.search_browse_all),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
            )
        }

        if (loading && categories.isEmpty()) {
            item(key = "browse_shimmer") {
                ShimmerHost(
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    repeat(6) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(columns) {
                                TextPlaceholder(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(SearchBrowseTileHeight),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            categories.chunked(columns).forEachIndexed { rowIndex, row ->
                item(key = "browse_row_$rowIndex") {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEachIndexed { columnIndex, category ->
                            val colorIndex = rowIndex * columns + columnIndex
                            SearchBrowseCategoryTile(
                                title = category.title,
                                backgroundColor = browseCategoryColor(category, colorIndex),
                                onClick = {
                                    onCategoryClick(
                                        category.endpoint.browseId,
                                        category.endpoint.params,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBrowseCategoryTile(
    title: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(SearchBrowseTileHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 18.dp, y = (-8).dp)
                    .size(72.dp)
                    .rotate(24f)
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape),
        )
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
        )
    }
}

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.metrolist.music.LocalNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ai.ListeningTasteTracker
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.DislikedSongIdsKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.HidePlayerThumbnailKey
import com.metrolist.music.constants.HideStatusBarOnFullscreenKey
import com.metrolist.music.constants.KeepScreenOn
import com.metrolist.music.constants.PlayerBackgroundStyle
import com.metrolist.music.constants.PlayerBackgroundStyleKey
import com.metrolist.music.constants.PlayerButtonsStyle
import com.metrolist.music.constants.PlayerButtonsStyleKey
import com.metrolist.music.constants.PlayerHorizontalPadding
import com.metrolist.music.constants.QueuePeekHeight
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.constants.SliderStyle
import com.metrolist.music.constants.SliderStyleKey
import com.metrolist.music.constants.SquigglySliderKey
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.constants.UseNewPlayerDesignKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.MetroDjChatButton
import com.metrolist.music.ui.component.Lyrics
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.ResizableIconButton
import com.metrolist.music.ui.component.SquigglySlider
import com.metrolist.music.ui.component.WavySlider
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.theme.PlayerColorExtractor
import com.metrolist.music.ui.theme.PlayerSliderColors
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.ShowOffsetDialog
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import com.metrolist.music.ui.component.Icon as MIcon
import com.metrolist.music.ui.component.aura.AuraIconButton
import com.metrolist.music.ui.component.aura.AuraOutlinedButton
import com.metrolist.music.ui.component.aura.AuraPlayButton
import com.metrolist.music.ui.component.aura.AuraPlayerCanvas
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyDark
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.AuraSpotifyOnDark
import com.metrolist.music.ui.component.aura.AuraSpotifyOnGreen
import com.metrolist.music.ui.component.aura.AuraTonalButton
import com.metrolist.music.ui.component.aura.AuraTransportButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val sleepTimerDefaultSetTemplate = stringResource(R.string.sleep_timer_default_set)
    val copiedTitleStr = stringResource(R.string.copied_title)
    val copiedArtistStr = stringResource(R.string.copied_artist)
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) =
        rememberPreference(
            UseNewPlayerDesignKey,
            defaultValue = true,
        )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(HidePlayerThumbnailKey, false)
    val (hideStatusBarOnFullscreen) = rememberPreference(HideStatusBarOnFullscreenKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT,
    )
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT,
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme =
        remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }

    val shouldUseDarkButtonColors =
        remember(playerBackground, useDarkTheme) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> true
                PlayerBackgroundStyle.DEFAULT -> useDarkTheme
            }
        }

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isKeepScreenOn by rememberPreference(KeepScreenOn, false)
    val keepScreenOn = isPlaying && isKeepScreenOn

    DisposableEffect(playerBackground, state.isExpanded, useDarkTheme, keepScreenOn, isFullScreen, hideStatusBarOnFullscreen) {
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                    insetsController.isAppearanceLightStatusBars = false
                }

                PlayerBackgroundStyle.DEFAULT -> {
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            if (isFullScreen && hideStatusBarOnFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }

            if (keepScreenOn && state.isExpanded) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(enabled = state.isExpanded) {
        state.collapseSoft()
    }

    val onBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurface
        }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }

    val playbackState by playerConnection.playbackState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val dislikedSongIds by rememberPreference(DislikedSongIdsKey, defaultValue = emptySet<String>())
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val squigglySlider by rememberPreference(SquigglySliderKey, defaultValue = false)

    // Listen Together state (reactive)
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST

    // Cast state - safely access castConnectionHandler to prevent crashes during service lifecycle changes
    val castHandler =
        remember(playerConnection) {
            try {
                playerConnection.service.castConnectionHandler
            } catch (e: Exception) {
                null
            }
        }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if focus request fails
            }
        }
    }

    // Use Cast state when casting, otherwise local player
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    // Use State objects for position/duration — never read them in this parent composition.
    // MiniPlayer / PlayerSeekBar read the states so position polls only recompose those leaves.
    val positionState = remember { mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)) }
    val durationState = remember {
        mutableLongStateOf(
            (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L),
        )
    }

    // Hold seek-drag state without reading it here — parent must not recompose on every drag tick.
    val sliderPositionState = remember { mutableStateOf<Long?>(null) }
    // Track when we last manually set position to avoid Cast overwriting it
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }
    val lastManualSeekTimeRef = rememberUpdatedState(lastManualSeekTime)

    // Position is always mirrored into positionState (local poll or cast collect).
    val playbackPositionProvider =
        remember(positionState) {
            { positionState.longValue }
        }
    val sliderPositionProvider =
        remember(sliderPositionState) {
            { sliderPositionState.value }
        }

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    if (!canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    }

    val defaultGradientColors = listOf(AuraPlayerCanvas, AuraSpotifyDark)
    val fallbackColor = AuraPlayerCanvas.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(currentMetadata.thumbnailUrl)
                            .size(100, 100)
                            .allowHardware(false)
                            .memoryCacheKey("gradient_${currentMetadata.id}")
                            .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette =
                                withContext(Dispatchers.Default) {
                                    Palette
                                        .from(bitmap)
                                        .maximumColorCount(8)
                                        .resizeBitmapArea(100 * 100)
                                        .generate()
                                }
                            val extractedColors =
                                PlayerColorExtractor.extractGradientColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor,
                                )
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val TextBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
                PlayerBackgroundStyle.BLUR -> Color.White
                PlayerBackgroundStyle.GRADIENT -> Color.White
            },
        label = "TextBackgroundColor",
    )

    val icBackgroundColor by animateColorAsState(
        targetValue =
            when (playerBackground) {
                PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
                PlayerBackgroundStyle.BLUR -> Color.Black
                PlayerBackgroundStyle.GRADIENT -> Color.Black
            },
        label = "icBackgroundColor",
    )

    // Secondary actions: Aura dark. Play uses AuraPlayButton green.
    val (textButtonColor, iconButtonColor) =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> Pair(Color.White.copy(alpha = 0.18f), Color.White)
                    PlayerButtonsStyle.PRIMARY, PlayerButtonsStyle.TERTIARY -> Pair(AuraSpotifyGreen, AuraSpotifyOnGreen)
                }
            }
            else -> {
                when (playerButtonsStyle) {
                    PlayerButtonsStyle.DEFAULT -> Pair(AuraSpotifyDark, AuraSpotifyOnDark)
                    PlayerButtonsStyle.PRIMARY, PlayerButtonsStyle.TERTIARY -> Pair(AuraSpotifyGreen, AuraSpotifyOnGreen)
                }
            }
        }

    val sideButtonContentColor =
        when {
            playerBackground == PlayerBackgroundStyle.BLUR ||
                playerBackground == PlayerBackgroundStyle.GRADIENT -> Color.White
            else -> TextBackgroundColor
        }

    val download by LocalDownloadUtil.current
        .getDownload(mediaMetadata?.id ?: "")
        .collectAsStateWithLifecycle(initialValue = null)

    // Sleep-timer countdown lives in Queue leaf composables — do not poll here
    // or BottomSheetPlayer recomposes every second for an unused value.

    val scope = rememberCoroutineScope()
    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }
    var showDismissClearQueueConfirm by remember {
        mutableStateOf(false)
    }

    val sleepTimerDefault by rememberPreference(SleepTimerDefaultKey, 30f)
    var sleepTimerValue by remember { mutableFloatStateOf(sleepTimerDefault) }
    val isAtDefault by remember {
        derivedStateOf { sleepTimerValue.roundToInt() == sleepTimerDefault.roundToInt() }
    }
    LaunchedEffect(sleepTimerDefault) { sleepTimerValue = sleepTimerDefault }
    val sleepTimerStopAfterCurrentSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val sleepTimerFadeOut by rememberPreference(SleepTimerFadeOutKey, false)



    if (showDismissClearQueueConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDismissClearQueueConfirm = false
                state.collapseSoft()
            },
            title = { Text(stringResource(R.string.player_dismiss_clear_queue_title)) },
            text = {
                Text(stringResource(R.string.player_dismiss_clear_queue_message))
            },
            confirmButton = {
                AuraSecondaryAction(onClick = {
                    showDismissClearQueueConfirm = false
                    playerConnection.service.clearAutomix()
                    playerConnection.player.stop()
                    playerConnection.player.clearMediaItems()
                }) {
                    Text(
                        stringResource(R.string.player_dismiss_clear_queue_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                AuraSecondaryAction(onClick = {
                    showDismissClearQueueConfirm = false
                    state.collapseSoft()
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                AuraSecondaryAction(onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer?.start(
                            minute = sleepTimerValue.roundToInt(),
                            stopAfterCurrentSong = sleepTimerStopAfterCurrentSong,
                            fadeOut = sleepTimerFadeOut,
                        )
                    }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                AuraSecondaryAction(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.minute,
                                sleepTimerValue.roundToInt(),
                                sleepTimerValue.roundToInt(),
                            ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isAtDefault) {
                            AuraTonalButton(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        } else {
                            AuraOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        context.safeDataStoreEdit { settings ->
                                            settings[SleepTimerDefaultKey] = sleepTimerValue
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        String.format(sleepTimerDefaultSetTemplate, sleepTimerValue.roundToInt()),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text(stringResource(R.string.set_as_default))
                            }
                        }

                        AuraOutlinedButton(
                            onClick = {
                                showSleepTimerDialog = false
                                playerConnection.service.sleepTimer?.start(minute = -1)
                            },
                        ) {
                            Text(stringResource(R.string.end_of_song))
                        }
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // Position update - only for local playback
    // When casting, we use castPosition directly to avoid sync issues
    // Use isPlaying instead of playbackState to ensure continuous updates during playback
    LaunchedEffect(isPlaying, isCasting) {
        if (!isCasting && isPlaying) {
            while (isActive) {
                delay(250)
                if (sliderPositionState.value == null) { // Only update if user isn't dragging
                    positionState.longValue = playerConnection.player.currentPosition
                    // Don't clobber a valid (metadata-derived) duration with 0/UNSET mid-resolve.
                    playerConnection.player.duration.takeIf { it > 0 }?.let { durationState.longValue = it }
                }
            }
        }
    }

    // Also update position when playback state changes (e.g., song change, seek)
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            positionState.longValue = playerConnection.player.currentPosition
            // Prefer the song's known duration (from metadata, available instantly from the restored
            // queue) so the slider range is right even when restored paused / before the stream
            // resolves; fall back to the player's duration once it is known.
            durationState.longValue = (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: playerConnection.player.duration
        }
    }

    // Auto-switch from repeat one to repeat all when song ends naturally
    var previousMediaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playbackState, mediaMetadata?.id) {
        val currentId = mediaMetadata?.id

        // Only switch from REPEAT_ONE to REPEAT_ALL when playback naturally ended
        // (i.e., the player transitioned to ENDED state and then moved to next track).
        // Do NOT switch on manual skips.
        if (currentId != null &&
            currentId != previousMediaId &&
            previousMediaId != null &&
            playbackState == Player.STATE_ENDED &&
            repeatMode == Player.REPEAT_MODE_ONE &&
            !isListenTogetherGuest) {
            playerConnection.player.setRepeatMode(Player.REPEAT_MODE_ALL)
        }

        previousMediaId = currentId
    }

    // Mirror cast ticks into positionState without collecting as composition state.
    LaunchedEffect(castHandler, isCasting) {
        val handler = castHandler ?: return@LaunchedEffect
        if (!isCasting) return@LaunchedEffect
        handler.castPosition.collect { pos ->
            if (sliderPositionState.value == null) {
                val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTimeRef.value
                if (timeSinceManualSeek > 1500) {
                    positionState.longValue = pos
                }
            }
        }
    }
    LaunchedEffect(castHandler, isCasting) {
        val handler = castHandler ?: return@LaunchedEffect
        if (!isCasting) return@LaunchedEffect
        handler.castDuration.collect { dur ->
            if (dur > 0) durationState.longValue = dur
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            expandedBound = state.expandedBound,
            collapsedBound = dismissedBound + 1.dp,
            initialAnchor = 1,
        )

    val bottomSheetBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> AuraPlayerCanvas
            else -> if (useBlackBackground) Color.Black else AuraPlayerCanvas
        }

    // Capture sheet progress only in draw/graphicsLayer — reading Animatable progress in
    // composition recomposes the whole expanded player every drag/expand frame.
    val sheetProgressState = rememberUpdatedState(state)

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(bottomSheetBackgroundColor)
                        .graphicsLayer {
                            alpha = sheetProgressState.value.progressValue().coerceIn(0f, 1f)
                        },
            ) {
                when (playerBackground) {
                    PlayerBackgroundStyle.BLUR -> {
                        // No Compose .blur / software decode — stretched cover + heavy scrim.
                        val thumbnailUrl = mediaMetadata?.thumbnailUrl
                        if (thumbnailUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .size(512)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                            )
                        }
                    }

                    PlayerBackgroundStyle.GRADIENT -> {
                        val colors = gradientColors.ifEmpty { defaultGradientColors }
                        val gradientColorStops =
                            if (colors.size >= 3) {
                                arrayOf(
                                    0.0f to colors[0],
                                    0.5f to colors[1],
                                    1.0f to colors[2],
                                )
                            } else {
                                arrayOf(
                                    0.0f to colors[0],
                                    0.6f to colors[0].copy(alpha = 0.7f),
                                    1.0f to Color.Black,
                                )
                            }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                .background(Color.Black.copy(alpha = 0.2f)),
                        )
                    }

                    else -> {
                        PlayerBackgroundStyle.DEFAULT
                    }
                }
            }
        },
        onDismiss =
            if (!isListenTogetherGuest) {
                {
                    showDismissClearQueueConfirm = true
                }
            } else {
                null
            },
        collapsedContent = {
            MiniPlayer(
                positionState = positionState,
                durationState = durationState,
                onClick = { state.expandSoft() },
            )
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                AnimatedContent(
                    targetState = showInlineLyrics,
                    label = "ThumbnailAnimation",
                ) { showLyrics ->
                    if (showLyrics) {
                        Row {
                            if (hidePlayerThumbnail) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.small_icon),
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = mediaMetadata.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                    modifier =
                                        Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedContent(
                        targetState = mediaMetadata.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextBackgroundColor,
                            modifier =
                                Modifier
                                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                    .combinedClickable(
                                        enabled = true,
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = {
                                            val albumId = mediaMetadata.album?.id
                                                ?: currentSong?.album?.id
                                                ?: currentSong?.song?.albumId
                                            if (albumId != null) {
                                                navController.navigate("album/$albumId")
                                                state.collapseSoft()
                                            }
                                        },
                                        onLongClick = {
                                            val clip = ClipData.newPlainText(copiedTitleStr, title)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast
                                                .makeText(context, copiedTitleStr, Toast.LENGTH_SHORT)
                                                .show()
                                        },
                                    ),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mediaMetadata.explicit) MIcon.Explicit()

                        if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                            val annotatedString =
                                buildAnnotatedString {
                                    mediaMetadata.artists.forEachIndexed { index, artist ->
                                        val tag = "artist_${artist.id.orEmpty()}"
                                        pushStringAnnotation(tag = tag, annotation = artist.id.orEmpty())
                                        withStyle(SpanStyle(color = TextBackgroundColor, fontSize = 16.sp)) {
                                            append(artist.name)
                                        }
                                        pop()
                                        if (index != mediaMetadata.artists.lastIndex) append(", ")
                                    }
                                }

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                        .padding(end = 12.dp),
                            ) {
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                var clickOffset by remember { mutableStateOf<Offset?>(null) }
                                Text(
                                    text = annotatedString,
                                    style = MaterialTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { layoutResult = it },
                                    modifier =
                                        Modifier
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val tapPosition = event.changes.firstOrNull()?.position
                                                        if (tapPosition != null) {
                                                            clickOffset = tapPosition
                                                        }
                                                    }
                                                }
                                            }.combinedClickable(
                                                enabled = true,
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() },
                                                onClick = {
                                                    val tapPosition = clickOffset
                                                    val layout = layoutResult
                                                    if (tapPosition != null && layout != null) {
                                                        val offset = layout.getOffsetForPosition(tapPosition)
                                                        annotatedString
                                                            .getStringAnnotations(offset, offset)
                                                            .firstOrNull()
                                                            ?.let { ann ->
                                                                val artistId = ann.item
                                                                if (artistId.isNotBlank()) {
                                                                    navController.navigate("artist/$artistId")
                                                                    state.collapseSoft()
                                                                }
                                                            }
                                                    }
                                                },
                                                onLongClick = {
                                                    val clip =
                                                        ClipData.newPlainText(
                                                            copiedArtistStr,
                                                            annotatedString,
                                                        )
                                                    clipboardManager.setPrimaryClip(clip)
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            copiedArtistStr,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                },
                                            ),
                                )
                            }
                        }
                    }
                }
            }

            if (useNewPlayerDesign) {
                    val shareShape =
                        RoundedCornerShape(
                            topStart = 50.dp,
                            bottomStart = 50.dp,
                            topEnd = 3.dp,
                            bottomEnd = 3.dp,
                        )

                    val favShape =
                        RoundedCornerShape(
                            topStart = 3.dp,
                            bottomStart = 3.dp,
                            topEnd = 50.dp,
                            bottomEnd = 50.dp,
                        )

                    val middleShape = RoundedCornerShape(3.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetroDjChatButton(tint = iconButtonColor)
                        AnimatedContent(targetState = showInlineLyrics, label = "ShareButton") { showLyrics ->
                            if (showLyrics) {
                                AuraIconButton(onClick = { isFullScreen = !isFullScreen },
                                    shape = shareShape, containerColor = textButtonColor,
                                            contentColor = iconButtonColor,
                                    modifier = Modifier.size(42.dp)) {
                                    Icon(
                                        painter = painterResource(R.drawable.fullscreen),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            } else {
                                AuraIconButton(onClick = {
                                        val intent =
                                            Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                                )
                                            }
                                        context.startActivity(Intent.createChooser(intent, null))
                                    },
                                    shape = shareShape, containerColor = textButtonColor,
                                            contentColor = iconButtonColor,
                                    modifier = Modifier.size(42.dp)) {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                            if (showLyrics) {
                                val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
                                AuraIconButton(onClick = {
                                        menuState.show {
                                            com.metrolist.music.ui.menu.LyricsMenu(
                                                lyricsProvider = { currentLyrics },
                                                songProvider = { currentSong?.song },
                                                mediaMetadataProvider = { mediaMetadata },
                                                onDismiss = menuState::dismiss,
                                                onShowOffsetDialog = {
                                                    bottomSheetPageState.show {
                                                        ShowOffsetDialog(
                                                            songProvider = { currentSong?.song },
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    },
                                    shape = favShape, containerColor = textButtonColor,
                                            contentColor = iconButtonColor,
                                    modifier = Modifier.size(42.dp)) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_horiz),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            } else {
                                // For episodes, show saved state (inLibrary); for songs, show liked state
                                val isEpisode = currentSong?.song?.isEpisode == true
                                val isFavorite = if (isEpisode) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true
                                val isDisliked = !isEpisode && mediaMetadata.id in dislikedSongIds
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AuraIconButton(
                                        onClick = {
                                            if (!isEpisode && isDisliked && !isFavorite) {
                                                scope.launch(Dispatchers.IO) {
                                                    ListeningTasteTracker.setDisliked(
                                                        context = context,
                                                        songId = mediaMetadata.id,
                                                        disliked = false,
                                                    )
                                                }
                                            }
                                            playerConnection.toggleLike()
                                        },
                                        shape = if (isEpisode) favShape else middleShape,
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                        modifier = Modifier.size(42.dp),
                                    ) {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    if (isFavorite) {
                                                        R.drawable.favorite
                                                    } else {
                                                        R.drawable.favorite_border
                                                    },
                                                ),
                                            contentDescription =
                                                stringResource(if (isFavorite) R.string.unlike_cd else R.string.like_cd),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                    if (!isEpisode) {
                                        AuraIconButton(
                                            onClick = {
                                                val disliked = !isDisliked
                                                scope.launch(Dispatchers.IO) {
                                                    ListeningTasteTracker.setDisliked(
                                                        context = context,
                                                        songId = mediaMetadata.id,
                                                        disliked = disliked,
                                                        title = mediaMetadata.title,
                                                        artists = mediaMetadata.artists.map { it.name },
                                                    )
                                                    if (disliked) {
                                                        playerConnection.service.onSongDisliked(mediaMetadata.id)
                                                    } else {
                                                        playerConnection.service.onSongUndisliked(mediaMetadata.id)
                                                    }
                                                }
                                                if (disliked && isFavorite) {
                                                    playerConnection.toggleLike()
                                                }
                                            },
                                            shape = favShape,
                                            containerColor = textButtonColor,
                                            contentColor = iconButtonColor,
                                            modifier = Modifier.size(42.dp),
                                        ) {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (isDisliked) {
                                                            R.drawable.media3_icon_thumb_down_filled
                                                        } else {
                                                            R.drawable.media3_icon_thumb_down_unfilled
                                                        },
                                                    ),
                                                contentDescription =
                                                    stringResource(
                                                        if (isDisliked) R.string.undislike_cd else R.string.dislike_cd,
                                                    ),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetroDjChatButton(tint = iconButtonColor)
                        Spacer(Modifier.width(8.dp))
                        AnimatedContent(targetState = showInlineLyrics, label = "ShareButton") { showLyrics ->
                        if (showLyrics) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(textButtonColor)
                                        .clickable { isFullScreen = !isFullScreen },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.fullscreen),
                                    contentDescription = null,
                                    tint = iconButtonColor,
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(24.dp),
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(textButtonColor)
                                        .clickable {
                                            val intent =
                                                Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                                    )
                                                }
                                            context.startActivity(Intent.createChooser(intent, null))
                                        },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    tint = iconButtonColor,
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(24.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                        if (showLyrics) {
                            val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(textButtonColor)
                                        .clickable {
                                            menuState.show {
                                                com.metrolist.music.ui.menu.LyricsMenu(
                                                    lyricsProvider = { currentLyrics },
                                                    songProvider = { currentSong?.song },
                                                    mediaMetadataProvider = { mediaMetadata },
                                                    onDismiss = menuState::dismiss,
                                                    onShowOffsetDialog = {
                                                        bottomSheetPageState.show {
                                                            ShowOffsetDialog(
                                                                songProvider = { currentSong?.song },
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                        },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_horiz),
                                    contentDescription = null,
                                    tint = iconButtonColor,
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(24.dp),
                                )
                            }
                        } else {
                            PlayerMoreMenuButton(
                                mediaMetadata = mediaMetadata,
                                state = state,
                                textButtonColor = textButtonColor,
                                iconButtonColor = iconButtonColor,
                            )
                        }
                        }
                    }
                }

            Spacer(Modifier.height(16.dp))

            PlayerSeekBar(
                positionState = positionState,
                durationState = durationState,
                sliderPositionState = sliderPositionState,
                onSeekFinished = { pos ->
                    if (isCasting) {
                        castHandler?.seekTo(pos)
                        lastManualSeekTime = System.currentTimeMillis()
                    } else {
                        playerConnection.player.seekTo(pos)
                    }
                    positionState.longValue = pos
                },
                enabled = !isListenTogetherGuest,
                sliderStyle = sliderStyle,
                squigglySlider = squigglySlider,
                isPlaying = effectiveIsPlaying,
                textButtonColor = textButtonColor,
                textBackgroundColor = TextBackgroundColor,
                playerBackground = playerBackground,
                useDarkTheme = useDarkTheme,
            )

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(
                visible = !isFullScreen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Column {
                    if (useNewPlayerDesign) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding),
                        ) {
                            AuraTransportButton(
                                onClick = playerConnection::seekToPrevious,
                                enabled = canSkipPrevious && !isListenTogetherGuest,
                                tint = sideButtonContentColor,
                                modifier = Modifier.size(56.dp),
                            ) {
                                Icon(painter = painterResource(R.drawable.skip_previous), contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                            AuraPlayButton(
                                onClick = {
                                    if (isListenTogetherGuest) { playerConnection.toggleMute(); return@AuraPlayButton }
                                    if (isCasting) { if (castIsPlaying) castHandler?.pause() else castHandler?.play() }
                                    else if (playbackState == STATE_ENDED) { playerConnection.player.seekTo(0, 0); playerConnection.player.playWhenReady = true }
                                    else playerConnection.togglePlayPause()
                                },
                                size = 72.dp,
                                modifier = Modifier.focusRequester(focusRequester),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isListenTogetherGuest) if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                        else if (playbackState == STATE_ENDED) R.drawable.replay
                                        else if (effectiveIsPlaying) R.drawable.pause
                                        else R.drawable.play
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            AuraTransportButton(
                                onClick = playerConnection::seekToNext,
                                enabled = canSkipNext && !isListenTogetherGuest,
                                tint = sideButtonContentColor,
                                modifier = Modifier.size(56.dp),
                            ) {
                                Icon(painter = painterResource(R.drawable.skip_next), contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    icon =
                                        when (repeatMode) {
                                            Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                            else -> throw IllegalStateException()
                                        },
                                    color = TextBackgroundColor,
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .padding(4.dp)
                                            .align(Alignment.Center)
                                            .alpha(
                                                if (isListenTogetherGuest || repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f,
                                            ),
                                    enabled = !isListenTogetherGuest,
                                    onClick = {
                                        playerConnection.player.toggleRepeatMode()
                                    },
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    icon = R.drawable.skip_previous,
                                    enabled = canSkipPrevious && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                            .alpha(if (isListenTogetherGuest) 0.5f else 1f),
                                    onClick = playerConnection::seekToPrevious,
                                )
                            }

Spacer(Modifier.width(8.dp))

                            AuraPlayButton(
                                onClick = {
                                    if (isListenTogetherGuest) { playerConnection.toggleMute(); return@AuraPlayButton }
                                    if (isCasting) { if (castIsPlaying) castHandler?.pause() else castHandler?.play() }
                                    else if (playbackState == STATE_ENDED) { playerConnection.player.seekTo(0, 0); playerConnection.player.playWhenReady = true }
                                    else playerConnection.player.togglePlayPause()
                                },
                                size = 72.dp,
                                modifier = Modifier.focusRequester(focusRequester),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isListenTogetherGuest) if (isMuted) R.drawable.volume_off else R.drawable.volume_up
                                        else if (playbackState == STATE_ENDED) R.drawable.replay
                                        else if (effectiveIsPlaying) R.drawable.pause
                                        else R.drawable.play
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    icon = R.drawable.skip_next,
                                    enabled = canSkipNext && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                            .alpha(if (isListenTogetherGuest) 0.5f else 1f),
                                    onClick = playerConnection::seekToNext,
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                // For episodes, show saved state (inLibrary); for songs, show liked state
                                val isEpisode = currentSong?.song?.isEpisode == true
                                val isFavorite = if (isEpisode) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true
                                val isDisliked = !isEpisode && mediaMetadata.id in dislikedSongIds
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ResizableIconButton(
                                        icon = if (isFavorite) R.drawable.favorite else R.drawable.favorite_border,
                                        color = if (isFavorite) MaterialTheme.colorScheme.error else TextBackgroundColor,
                                        contentDescription =
                                            stringResource(if (isFavorite) R.string.unlike_cd else R.string.like_cd),
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .padding(4.dp),
                                        onClick = {
                                            if (!isEpisode && isDisliked && !isFavorite) {
                                                scope.launch(Dispatchers.IO) {
                                                    ListeningTasteTracker.setDisliked(
                                                        context = context,
                                                        songId = mediaMetadata.id,
                                                        disliked = false,
                                                    )
                                                }
                                            }
                                            playerConnection.toggleLike()
                                        },
                                    )
                                    if (!isEpisode) {
                                        ResizableIconButton(
                                            icon =
                                                if (isDisliked) {
                                                    R.drawable.media3_icon_thumb_down_filled
                                                } else {
                                                    R.drawable.media3_icon_thumb_down_unfilled
                                                },
                                            color =
                                                if (isDisliked) {
                                                    MaterialTheme.colorScheme.tertiary
                                                } else {
                                                    TextBackgroundColor
                                                },
                                            contentDescription =
                                                stringResource(
                                                    if (isDisliked) R.string.undislike_cd else R.string.dislike_cd,
                                                ),
                                            modifier =
                                                Modifier
                                                    .size(32.dp)
                                                    .padding(4.dp),
                                            onClick = {
                                                val disliked = !isDisliked
                                                scope.launch(Dispatchers.IO) {
                                                    ListeningTasteTracker.setDisliked(
                                                        context = context,
                                                        songId = mediaMetadata.id,
                                                        disliked = disliked,
                                                        title = mediaMetadata.title,
                                                        artists = mediaMetadata.artists.map { it.name },
                                                    )
                                                    if (disliked) {
                                                        playerConnection.service.onSongDisliked(mediaMetadata.id)
                                                    } else {
                                                        playerConnection.service.onSongUndisliked(mediaMetadata.id)
                                                    }
                                                }
                                                if (disliked && isFavorite) {
                                                    playerConnection.toggleLike()
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Calculate vertical padding like OuterTune
                val density = LocalDensity.current
                val verticalPadding =
                    max(
                        WindowInsets.systemBars.getTop(density),
                        WindowInsets.systemBars.getBottom(density),
                    )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)

                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets),
                            ).padding(bottom = 24.dp)
                            .fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                    ) {
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    positionProvider = playbackPositionProvider,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.animateContentSize(),
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .weight(if (showInlineLyrics) 0.65f else 1f, false)
                                .animateContentSize()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                    ) {
                        NanoDjCommentaryBanner()
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen) 0.dp else queueSheetState.collapsedBound,
                    label = "bottomPadding",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = bottomPadding)
                            .animateContentSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f),
                    ) {
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    positionProvider = playbackPositionProvider,
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = isExpandedProvider,
                                    isListenTogetherGuest = isListenTogetherGuest,
                                )
                            }
                        }
                    }

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = !isFullScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
                background =
                    if (useBlackBackground) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                onBackgroundColor = onBackgroundColor,
                TextBackgroundColor = TextBackgroundColor,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                pureBlack = pureBlack,
                showInlineLyrics = showInlineLyrics,
                playerBackground = playerBackground,
                onToggleLyrics = {
                    showInlineLyrics = !showInlineLyrics
                },
            )
        }
    }
}


/**
 * Isolated seek bar + time labels so position polls only recompose this leaf,
 * not the entire expanded player tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(
    positionState: MutableLongState,
    durationState: MutableLongState,
    sliderPositionState: MutableState<Long?>,
    onSeekFinished: (Long) -> Unit,
    enabled: Boolean,
    sliderStyle: SliderStyle,
    squigglySlider: Boolean,
    isPlaying: Boolean,
    textButtonColor: Color,
    textBackgroundColor: Color,
    playerBackground: PlayerBackgroundStyle,
    useDarkTheme: Boolean,
) {
    val position by positionState
    val duration by durationState
    var sliderPosition by sliderPositionState
    val effectivePosition = position
    val sliderColors = PlayerSliderColors.getSliderColors(textButtonColor, playerBackground, useDarkTheme)
    val value = (sliderPosition ?: effectivePosition).toFloat()
    val valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat())

    when (sliderStyle) {
        SliderStyle.DEFAULT -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = {
                    if (enabled) sliderPosition = it.toLong()
                },
                onValueChangeFinished = {
                    if (enabled) {
                        sliderPosition?.let(onSeekFinished)
                        sliderPosition = null
                    }
                },
                enabled = enabled,
                colors = sliderColors,
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
            )
        }

        SliderStyle.WAVY -> {
            if (squigglySlider) {
                SquigglySlider(
                    value = value,
                    valueRange = valueRange,
                    onValueChange = { sliderPosition = it.toLong() },
                    onValueChangeFinished = {
                        sliderPosition?.let(onSeekFinished)
                        sliderPosition = null
                    },
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    colors = sliderColors,
                    isPlaying = isPlaying,
                )
            } else {
                WavySlider(
                    value = value,
                    valueRange = valueRange,
                    onValueChange = { sliderPosition = it.toLong() },
                    onValueChangeFinished = {
                        sliderPosition?.let(onSeekFinished)
                        sliderPosition = null
                    },
                    colors = sliderColors,
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    isPlaying = isPlaying,
                )
            }
        }

        SliderStyle.SLIM -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = {
                    if (enabled) sliderPosition = it.toLong()
                },
                onValueChangeFinished = {
                    if (enabled) {
                        sliderPosition?.let(onSeekFinished)
                        sliderPosition = null
                    }
                },
                enabled = enabled,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = sliderColors,
                    )
                },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding + 4.dp),
    ) {
        Text(
            text = makeTimeString(sliderPosition ?: effectivePosition),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    positionProvider: () -> Long,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var appInForeground by remember {
        mutableStateOf(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }
    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer =
            LifecycleEventObserver { _, _ ->
                appInForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val nextMetadata =
        remember(queueWindows, currentWindowIndex) {
            if (currentWindowIndex >= 0 && currentWindowIndex + 1 < queueWindows.size) {
                queueWindows[currentWindowIndex + 1].mediaItem.metadata
            } else {
                null
            }
        }

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entryPoint =
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                        )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    // Prefetch lyrics for the next queue item only while the lyrics pane is visible, the app is in the
    // foreground, and the current track's lyrics row has finished loading (avoids competing with the
    // active fetch).
    LaunchedEffect(
        nextMetadata?.id,
        showLyrics,
        appInForeground,
        mediaMetadata?.id,
        currentLyrics,
    ) {
        if (!showLyrics || !appInForeground || nextMetadata == null) return@LaunchedEffect
        val loadedForCurrent =
            currentLyrics?.let { lyrics ->
                mediaMetadata == null || lyrics.id == mediaMetadata.id
            } == true
        if (mediaMetadata != null && !loadedForCurrent) return@LaunchedEffect
        val nextId = nextMetadata.id
        delay(400)
        if (!showLyrics || !appInForeground || !isActive) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val existing = database.lyrics(nextId).first()
                if (existing != null) return@withContext
                val entryPoint =
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                    )
                val lyricsHelper = entryPoint.lyricsHelper()
                val fetched = lyricsHelper.getLyrics(nextMetadata)
                database.query {
                    upsert(LyricsEntity(nextId, fetched.lyrics, fetched.provider))
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }

            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = positionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics,
                    )
                }
                ProvideTextStyle(
                    value =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        ),
                ) {
                    lyricsContent()
                }
            }
        }
    }
}

@Composable
fun MoreActionsButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    AuraIconButton(
        onClick = {
            menuState.show {
                PlayerMenu(
                    mediaMetadata = mediaMetadata,
                    playerBottomSheetState = state,
                    onShowDetailsDialog = {
                        mediaMetadata.id.let {
                            bottomSheetPageState.show {
                                ShowMediaInfo(it)
                            }
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = Modifier.size(40.dp),
        containerColor = textButtonColor,
        contentColor = iconButtonColor,
    ) {
        Icon(painter = painterResource(R.drawable.more_horiz), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    AuraIconButton(
        onClick = {
            menuState.show {
                PlayerMenu(
                    mediaMetadata = mediaMetadata,
                    playerBottomSheetState = state,
                    onShowDetailsDialog = {
                        mediaMetadata.id.let {
                            bottomSheetPageState.show {
                                ShowMediaInfo(it)
                            }
                        }
                    },
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = Modifier.size(40.dp),
        containerColor = textButtonColor,
        contentColor = iconButtonColor,
    ) {
        Icon(painter = painterResource(R.drawable.more_horiz), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

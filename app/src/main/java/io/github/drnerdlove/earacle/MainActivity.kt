package io.github.drnerdlove.earacle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import io.github.drnerdlove.earacle.lyrics.CountdownTiming
import io.github.drnerdlove.earacle.lyrics.LyricLine
import io.github.drnerdlove.earacle.lyrics.StageSettings
import io.github.drnerdlove.earacle.lyrics.StageText
import io.github.drnerdlove.earacle.lyrics.SyncShift
import io.github.drnerdlove.earacle.lyrics.nextTextSize
import io.github.drnerdlove.earacle.lyrics.showsStageMode
import io.github.drnerdlove.earacle.lyrics.stageLines
import io.github.drnerdlove.earacle.lyrics.formatShift
import io.github.drnerdlove.earacle.lyrics.syncedPositionMs
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.countdown
import io.github.drnerdlove.earacle.lyrics.currentLineIndex
import io.github.drnerdlove.earacle.lyrics.displayTitle
import io.github.drnerdlove.earacle.lyrics.songDurationSeconds
import io.github.drnerdlove.earacle.recognition.SongTracking
import io.github.drnerdlove.earacle.recognition.RecognitionResult
import io.github.drnerdlove.earacle.recognition.formatPosition
import io.github.drnerdlove.earacle.recognition.positionSeconds
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import io.github.drnerdlove.earacle.lyrics.filledEndIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.drnerdlove.earacle.lyrics.splitBacking

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        viewModel.setAppVisible(true)
    }

    override fun onStop() {
        viewModel.setAppVisible(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.attachStageStore(StagePreferences(this))
        enableEdgeToEdge()
        // Keep the screen on while the app is visible, so the lyrics stay readable. The flag
        // only applies while this window is shown; the system ignores it in the background.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val state by viewModel.state.collectAsState()
            val stage by viewModel.stage.collectAsState()
            // Stage mode is always dark, whatever the system theme says, also while "Try again" listens.
            val stageLook = stage.enabled && when (val current = state) {
                is UiState.Found -> showsStageMode(true, current.lyrics)
                is UiState.Listening, UiState.Recognizing -> true
                else -> false
            }
            DisposableEffect(stageLook) {
                // Light icons in the status and navigation bar on the black stage.
                val bars = if (stageLook) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            EaracleTheme(forceDark = stageLook) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (stageLook) Color.Black else MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    MainScreen(viewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun EaracleTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme() || forceDark
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val stage by viewModel.stage.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            @Suppress("MissingPermission")
            viewModel.recognize()
        } else {
            viewModel.onPermissionDenied()
        }
    }
    val onRecognize = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            @Suppress("MissingPermission")
            viewModel.recognize()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    MainContent(
        state,
        onRecognize,
        modifier,
        onTapLine = { viewModel.tapLine(it) },
        onNudge = viewModel::nudgeShift,
        onResetShift = viewModel::resetShift,
        stage = stage,
        onStageChange = viewModel::setStageMode,
        onStageTextSize = viewModel::changeStageTextSize,
    )
}

@Composable
fun MainContent(
    state: UiState,
    onRecognize: () -> Unit,
    modifier: Modifier = Modifier,
    /** A lyric line was tapped as "being sung now"; returns whether the tap counted. */
    onTapLine: (Long) -> Boolean = { false },
    onNudge: (Long) -> Unit = {},
    onResetShift: () -> Unit = {},
    stage: StageSettings = StageSettings(),
    onStageChange: (Boolean) -> Unit = {},
    /** `A+` (true) or `A−` (false) in stage mode. */
    onStageTextSize: (Boolean) -> Unit = {},
) {
    val busy = state is UiState.Listening || state is UiState.Recognizing
    val retry = state is UiState.NotFound || state is UiState.Error ||
        state is UiState.PermissionDenied || state is UiState.Found
    val buttonLabel = stringResource(if (retry) R.string.button_try_again else R.string.button_recognize)
    if (state is UiState.Found && showsStageMode(stage.enabled, state.lyrics)) {
        StageContent(
            state,
            stage,
            busy = busy,
            onRecognize = onRecognize,
            onLeave = { onStageChange(false) },
            onTextSize = onStageTextSize,
            onNudge = onNudge,
            onResetShift = onResetShift,
            modifier = modifier,
        )
        return
    }
    if (state is UiState.Found) {
        // Compact header at the top, lyrics take all remaining space, button always visible.
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Synced lyrics follow the current line until the user scrolls them by hand; the round
            // button next to "Try again" brings them back. A new song follows again.
            val syncedLines = (state.lyrics as? Lyrics.Synced)?.lines
            var following by remember(syncedLines) { mutableStateOf(true) }
            FoundContent(
                state,
                following,
                onScrolledByHand = { following = false },
                onTapLine = { timeMs -> if (onTapLine(timeMs)) following = true },
                onNudge = onNudge,
                onResetShift = onResetShift,
            )
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                // "Try again" stays centred; the round button sits at the end of the same row
                // and is exactly as high as it.
                Button(
                    onClick = onRecognize,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.Center),
                ) { Text(buttonLabel) }
                if (syncedLines != null) {
                    // Stage mode: very large lyrics for singing along from a distance.
                    FilledTonalIconButton(
                        onClick = { onStageChange(true) },
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(ButtonDefaults.MinHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fullscreen),
                            contentDescription = stringResource(R.string.stage_enter),
                        )
                    }
                }
                if (syncedLines != null && !following) {
                    FilledTonalIconButton(
                        onClick = { following = true },
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(ButtonDefaults.MinHeight),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = stringResource(R.string.lyrics_back_to_current),
                        )
                    }
                }
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(24.dp))
        StatusArea(state)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRecognize, enabled = !busy) { Text(buttonLabel) }
    }
}

@Composable
private fun StatusArea(state: UiState) {
    when (state) {
        UiState.Idle -> Hint(stringResource(R.string.hint_idle))
        is UiState.Listening -> {
            Text(stringResource(R.string.status_listening), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Hint(stringResource(R.string.hint_listening))
        }
        UiState.Recognizing -> {
            Text(stringResource(R.string.status_recognizing), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        is UiState.Found -> Unit // shown by FoundArea
        UiState.NotFound -> Hint(stringResource(R.string.status_not_found))
        is UiState.Error -> Hint(
            stringResource(
                when (state.kind) {
                    ErrorKind.MICROPHONE -> R.string.error_microphone
                    ErrorKind.NETWORK -> R.string.error_network
                    ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
                    ErrorKind.SERVER -> R.string.error_server
                },
            ),
        )
        UiState.PermissionDenied -> Hint(stringResource(R.string.status_permission_denied))
    }
}

/** The clock of the lyrics screen: [SystemClock.elapsedRealtime], refreshed every 200 ms while [state] shows. */
@Composable
private fun rememberNowMs(state: UiState.Found): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    return now
}

/**
 * The song position (with the manual shift, which applies to the shown time and to the lyrics alike)
 * at a given SystemClock.elapsedRealtime(). The countdown dots and the fill call it once per frame.
 */
@Composable
private fun rememberPositionAt(state: UiState.Found): (Long) -> Long = remember(state) {
    { elapsedMs: Long ->
        val seconds = SongTracking.displayPosition(
            positionSeconds(state.match.offsetSeconds, state.recordingStartedAtElapsedMs, elapsedMs),
            state.lyrics.songDurationSeconds,
        )
        syncedPositionMs((seconds * 1000).toLong(), state.shiftMs)
    }
}

@Composable
private fun ColumnScope.FoundContent(
    state: UiState.Found,
    following: Boolean,
    onScrolledByHand: () -> Unit,
    onTapLine: (Long) -> Unit,
    onNudge: (Long) -> Unit,
    onResetShift: () -> Unit,
) {
    val match: RecognitionResult.Match = state.match
    val now = rememberNowMs(state)
    val positionAt = rememberPositionAt(state)
    val positionMs = positionAt(now)
    val position = formatPosition(positionMs / 1000.0)
    // No app name above the song: the lyrics get the space.
    Text(
        displayTitle(match.title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        match.artist,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.position_in_song, position),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (state.relistening) {
        Text(
            stringResource(R.string.status_relistening),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.lyrics is Lyrics.Synced) {
        ShiftRow(state.shiftMs, onNudge, onResetShift)
    }
    Spacer(Modifier.height(8.dp))
    LyricsArea(state.lyrics, positionMs, positionAt, following, onScrolledByHand, onTapLine)
}

@Composable
private fun ColumnScope.LyricsArea(
    lyrics: Lyrics?,
    positionMs: Long,
    positionAt: (Long) -> Long,
    following: Boolean,
    onScrolledByHand: () -> Unit,
    onTapLine: (Long) -> Unit,
) {
    when (lyrics) {
        null -> CenteredInRemainingSpace { CircularProgressIndicator() }
        Lyrics.NotFound -> CenteredInRemainingSpace { Hint(stringResource(R.string.lyrics_not_found)) }
        Lyrics.Instrumental -> CenteredInRemainingSpace { Hint(stringResource(R.string.lyrics_instrumental)) }
        Lyrics.Failure -> CenteredInRemainingSpace { Hint(stringResource(R.string.lyrics_error)) }
        is Lyrics.Plain -> PlainLyrics(lyrics.text)
        is Lyrics.Synced -> SyncedLyrics(lyrics.lines, positionMs, positionAt, following, onScrolledByHand, onTapLine)
    }
}

@Composable
private fun ColumnScope.CenteredInRemainingSpace(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ColumnScope.SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    positionAt: (Long) -> Long,
    following: Boolean,
    onScrolledByHand: () -> Unit,
    onTapLine: (Long) -> Unit,
) {
    val current = currentLineIndex(lines, positionMs)
    // Set during a long pause before the next line: it gets the countdown dots and a brighter preview.
    val pause = countdown(lines, positionMs)
    // The dots stand at the line being sung, or above the first line before it starts.
    val dotsHost = maxOf(current, 0)
    val listState = rememberLazyListState()
    // The effect below outlives recompositions; always call the latest callback.
    val currentOnScrolledByHand by rememberUpdatedState(onScrolledByHand)
    LaunchedEffect(listState) {
        // Only a finger on the list starts a drag; the automatic scrolling below does not.
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) currentOnScrolledByHand()
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        // Half the viewport of padding above and below lets every line, the first and last
        // included, reach the vertical middle.
        val halfHeight = maxHeight / 2
        // Also re-centre when the area changes size (window resized, split screen).
        LaunchedEffect(current, maxHeight, following) {
            if (!following || current < 0) return@LaunchedEffect
            val layout = listState.layoutInfo
            val viewport = layout.viewportSize.height
            if (viewport <= 0) return@LaunchedEffect
            val visible = layout.visibleItemsInfo
            val itemSize = visible.firstOrNull { it.index == current }?.size
                ?: if (visible.isEmpty()) 0 else visible.sumOf { it.size } / visible.size
            // The item's start sits at beforeContentPadding minus scrollOffset; its middle
            // belongs at viewport / 2.
            val before = layout.beforeContentPadding
            listState.animateScrollToItem(current, before - viewport / 2 + itemSize / 2)
            // A line that was off screen has only been estimated; correct once if the guess was off.
            val actual = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }?.size
            if (actual != null && abs(actual - itemSize) > 2) {
                listState.animateScrollToItem(current, before - viewport / 2 + actual / 2)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = halfHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == current
                val isBlank = line.text.isBlank()
                val isDotsHost = index == dotsHost
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTapLine(line.timeMs) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Before the first line the dots stand above it; a pause (♪) shows them in its place.
                    if (isDotsHost && current < 0 && !isBlank) {
                        CountdownSlot(pause != null, lines, positionAt)
                    }
                    if (isDotsHost && isBlank && pause != null) {
                        CountdownSlot(true, lines, positionAt)
                    } else if (isCurrent && !isBlank) {
                        FillingLine(lines, index, positionAt)
                    } else {
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = if (isBlank) AnnotatedString("♪") else lyricText(line.text, muted),
                            style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                // The next line to sing is already a little brighter during the countdown.
                                pause != null && index == pause.nextIndex -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (isDotsHost && current >= 0 && !isBlank) {
                        CountdownSlot(pause != null, lines, positionAt)
                    }
                }
            }
        }
    }
}

/** Size of backing vocals (text in parentheses) relative to the lead text. */
private val BackingTextSize = 0.75.em

/** [text] with its backing vocals ([splitBacking]) smaller and in [mutedColor]; the characters are unchanged. */
private fun lyricText(text: String, mutedColor: Color): AnnotatedString = buildAnnotatedString {
    for (segment in splitBacking(text)) {
        if (segment.backing) {
            withStyle(SpanStyle(fontSize = BackingTextSize, color = mutedColor)) { append(segment.text) }
        } else {
            append(segment.text)
        }
    }
}

/**
 * The line being sung: drawn in the highlight colour, with the part not sung yet recoloured to a muted
 * colour. The filled part grows at an even pace ([lineProgress]) and wraps with the text: each visual line
 * fills after the one above it. The position is read where the line is drawn (in `drawWithContent`), so a
 * frame redraws only this line and does not recompose the lyrics list.
 */
@Composable
private fun FillingLine(
    lines: List<LyricLine>,
    index: Int,
    positionAt: (Long) -> Long,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val latestLines by rememberUpdatedState(lines)
    val latestPositionAt by rememberUpdatedState(positionAt)
    var frameTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTime = SystemClock.elapsedRealtime() }
        }
    }
    val plain = lines[index].text
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Backing vocals stay muted and small even in the part already sung; the text itself is unchanged.
    val text = lyricText(plain, muted)
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            // Offscreen, so the muted colour below (SrcAtop) only lands on the letters, not on the background.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val textLayout = layout ?: return@drawWithContent
                // The layout can still belong to the previous text for one frame.
                if (textLayout.layoutInput.text.text != plain) return@drawWithContent
                val filledEnd = filledEndIndex(latestLines, index, latestPositionAt(frameTime))
                for (line in 0 until textLayout.lineCount) {
                    val lineEnd = textLayout.getLineEnd(line, visibleEnd = true)
                    if (filledEnd >= lineEnd) continue
                    val top = textLayout.getLineTop(line)
                    val bottom = textLayout.getLineBottom(line)
                    // The edge of the filled part on this visual line; centred text starts at the line's own
                    // left edge, not at 0, which the layout's position already accounts for.
                    val (left, right) = if (filledEnd <= textLayout.getLineStart(line)) {
                        0f to size.width
                    } else {
                        val x = textLayout.getHorizontalPosition(filledEnd, usePrimaryDirection = true)
                        if (textLayout.getParagraphDirection(filledEnd) == ResolvedTextDirection.Rtl) {
                            0f to x
                        } else {
                            x to size.width
                        }
                    }
                    drawRect(
                        color = muted,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}

/** Diameter of a countdown dot on the normal screen; stage mode uses bigger ones. */
private val CountdownDotSize = 8.dp

/**
 * A fixed-height row that holds the countdown dots while [show] is true. Its height is reserved (2.5 dots),
 * so a line does not change size when the dots appear.
 */
@Composable
private fun CountdownSlot(
    show: Boolean,
    lines: List<LyricLine>,
    positionAt: (Long) -> Long,
    dot: Dp = CountdownDotSize,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dot * 2.5f),
        contentAlignment = Alignment.Center,
    ) {
        if (show) CountdownDots(lines, positionAt, dot)
    }
}

/**
 * The three dots of the countdown, redrawn on every frame. The position is read where the dots are drawn
 * (in `drawBehind`), so a frame redraws only these dots and does not recompose the lyrics list.
 */
@Composable
private fun CountdownDots(lines: List<LyricLine>, positionAt: (Long) -> Long, dot: Dp) {
    val latestLines by rememberUpdatedState(lines)
    val latestPositionAt by rememberUpdatedState(positionAt)
    var frameTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTime = SystemClock.elapsedRealtime() }
        }
    }
    val lit = MaterialTheme.colorScheme.primary
    val out = lit.copy(alpha = 0.2f)
    val gap = dot
    Box(
        modifier = Modifier
            .size(width = dot * CountdownTiming.DOTS + gap * (CountdownTiming.DOTS - 1), height = dot)
            .drawBehind {
                val litCount = countdown(latestLines, latestPositionAt(frameTime))?.litDots ?: 0
                val radius = dot.toPx() / 2
                val step = (dot + gap).toPx()
                for (i in 0 until CountdownTiming.DOTS) {
                    drawCircle(
                        color = if (i < litCount) lit else out,
                        radius = radius,
                        center = Offset(radius + i * step, radius),
                    )
                }
            },
    )
}

/**
 * Stage mode: black background, the current line very large in the middle, the next one smaller below it,
 * all other lines hidden. One small line with title and artist at the top, small controls at the bottom.
 * The theme is dark here (see [MainActivity]); the countdown, the fill and the backing vocals work as on the
 * normal screen.
 */
@Composable
private fun StageContent(
    state: UiState.Found,
    stage: StageSettings,
    busy: Boolean,
    onRecognize: () -> Unit,
    onLeave: () -> Unit,
    onTextSize: (Boolean) -> Unit,
    onNudge: (Long) -> Unit,
    onResetShift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = (state.lyrics as? Lyrics.Synced)?.lines
    val now = rememberNowMs(state)
    val positionAt = rememberPositionAt(state)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${displayTitle(state.match.title)} – ${state.match.artist}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (lines == null) {
            // The lyrics of a new song are still being looked up.
            CenteredInRemainingSpace { CircularProgressIndicator() }
        } else {
            StageLyrics(lines, positionAt(now), positionAt, stage.textSizeSp)
        }
        StageControls(
            textSizeSp = stage.textSizeSp,
            shiftMs = state.shiftMs,
            busy = busy,
            onRecognize = onRecognize,
            onLeave = onLeave,
            onTextSize = onTextSize,
            onNudge = onNudge,
            onResetShift = onResetShift,
        )
    }
}

/** The lines of stage mode ([stageLines]) centred in the remaining space; long lines wrap, never get cut. */
@Composable
private fun ColumnScope.StageLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    positionAt: (Long) -> Long,
    textSizeSp: Int,
) {
    val shown = stageLines(lines, positionMs)
    val pause = countdown(lines, positionMs)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val currentStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = textSizeSp.sp,
        lineHeight = (textSizeSp * 1.2f).sp,
    )
    val nextSizeSp = textSizeSp * StageText.NEXT_LINE_SCALE
    val nextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = nextSizeSp.sp,
        lineHeight = (nextSizeSp * 1.25f).sp,
    )
    val dot = (textSizeSp / 3f).dp
    // A line too long for the screen at this size scrolls (and starts at the top again with every new line).
    val scroll = rememberScrollState()
    LaunchedEffect(shown.currentIndex) { scroll.scrollTo(0) }
    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val current = shown.currentIndex.takeIf { it >= 0 }?.let { lines[it] }
            when {
                // A line with text: the fill runs over it, the dots (in a pause) stand below it.
                current != null && current.text.isNotBlank() -> {
                    FillingLine(lines, shown.currentIndex, positionAt, currentStyle)
                    CountdownSlot(pause != null, lines, positionAt, dot)
                }
                // Before the first line: the dots stand above the next one.
                current == null -> CountdownSlot(pause != null, lines, positionAt, dot)
                // An empty line (instrumental part): the dots in place of the ♪.
                pause != null -> CountdownSlot(true, lines, positionAt, dot)
                else -> Text("♪", style = currentStyle, color = muted, textAlign = TextAlign.Center)
            }
            shown.nextIndex?.let { next ->
                Spacer(Modifier.height(dot))
                Text(
                    text = lyricText(lines[next].text, muted),
                    style = nextStyle,
                    // Already a little brighter while the dots count down, as on the normal screen.
                    color = if (pause != null) MaterialTheme.colorScheme.onSurface else muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Width from which the stage controls fit in one row (landscape); below it they take two rows. */
private val StageControlsOneRowWidth = 600.dp

/** Small controls at the bottom of stage mode: leave, text size, timing shift and "Try again". */
@Composable
private fun StageControls(
    textSizeSp: Int,
    shiftMs: Long,
    busy: Boolean,
    onRecognize: () -> Unit,
    onLeave: () -> Unit,
    onTextSize: (Boolean) -> Unit,
    onNudge: (Long) -> Unit,
    onResetShift: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val smaller = stringResource(R.string.stage_text_smaller)
    val larger = stringResource(R.string.stage_text_larger)
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLeave) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen_exit),
                    contentDescription = stringResource(R.string.stage_exit),
                    tint = muted,
                )
            }
            TextButton(
                onClick = { onTextSize(false) },
                enabled = nextTextSize(textSizeSp, up = false) != textSizeSp,
                modifier = Modifier.semantics { contentDescription = smaller },
            ) { Text("A−") }
            TextButton(
                onClick = { onTextSize(true) },
                enabled = nextTextSize(textSizeSp, up = true) != textSizeSp,
                modifier = Modifier.semantics { contentDescription = larger },
            ) { Text("A+") }
            TextButton(onClick = onRecognize, enabled = !busy) {
                Text(stringResource(R.string.button_try_again))
            }
        }
    }
    val shift: @Composable () -> Unit = { ShiftRow(shiftMs, onNudge, onResetShift) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= StageControlsOneRowWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
                shift()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                shift()
                actions()
            }
        }
    }
}

/** "−0.5 s", the current shift (tap to reset) and "+0.5 s"; positive moves the lyrics ahead. */
@Composable
private fun ShiftRow(shiftMs: Long, onNudge: (Long) -> Unit, onReset: () -> Unit) {
    val later = stringResource(R.string.shift_later)
    val earlier = stringResource(R.string.shift_earlier)
    val reset = stringResource(R.string.shift_reset)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { onNudge(-SyncShift.STEP_MS) },
            modifier = Modifier.semantics { contentDescription = later },
        ) { Text(formatShift(-SyncShift.STEP_MS)) }
        TextButton(
            onClick = onReset,
            modifier = Modifier.semantics { contentDescription = reset },
        ) { Text(formatShift(shiftMs), fontWeight = FontWeight.Bold) }
        TextButton(
            onClick = { onNudge(SyncShift.STEP_MS) },
            modifier = Modifier.semantics { contentDescription = earlier },
        ) { Text(formatShift(SyncShift.STEP_MS)) }
    }
}

@Composable
private fun ColumnScope.PlainLyrics(text: String) {
    Hint(stringResource(R.string.lyrics_not_synced))
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    EaracleTheme {
        MainContent(UiState.Idle, onRecognize = {})
    }
}

private val previewMatch = RecognitionResult.Match(
    title = "Zero to Hero (Music from \"Marvel's Guardians of the Galaxy: Original Video Game Soundtrack\")",
    artist = "Some Artist & The Band",
    offsetSeconds = 30.0,
    shazamKey = null,
    coverArtUrl = null,
)

private val previewLyrics = Lyrics.Synced(
    List(30) { LyricLine(it * 4000L, "Lyric line number ${it + 1} of the preview song") },
)

private fun previewFound() = UiState.Found(
    previewMatch,
    recordingStartedAtElapsedMs = SystemClock.elapsedRealtime(),
    lyrics = previewLyrics,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 380)
@Composable
fun FoundSmallHeightPreview() {
    EaracleTheme {
        MainContent(previewFound(), onRecognize = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun FoundPhoneHeightPreview() {
    EaracleTheme {
        MainContent(previewFound(), onRecognize = {})
    }
}

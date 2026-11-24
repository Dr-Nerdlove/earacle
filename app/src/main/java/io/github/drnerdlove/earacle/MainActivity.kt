package io.github.drnerdlove.earacle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import io.github.drnerdlove.earacle.lyrics.LyricLine
import io.github.drnerdlove.earacle.lyrics.SyncShift
import io.github.drnerdlove.earacle.lyrics.formatShift
import io.github.drnerdlove.earacle.lyrics.syncedPositionMs
import io.github.drnerdlove.earacle.lyrics.Lyrics
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
        enableEdgeToEdge()
        // Keep the screen on while the app is visible, so the lyrics stay readable. The flag
        // only applies while this window is shown; the system ignores it in the background.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            EaracleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(viewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun EaracleTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
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
) {
    val busy = state is UiState.Listening || state is UiState.Recognizing
    val retry = state is UiState.NotFound || state is UiState.Error ||
        state is UiState.PermissionDenied || state is UiState.Found
    val buttonLabel = stringResource(if (retry) R.string.button_try_again else R.string.button_recognize)
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
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val seconds = SongTracking.displayPosition(
        positionSeconds(match.offsetSeconds, state.recordingStartedAtElapsedMs, now),
        state.lyrics.songDurationSeconds,
    )
    // The manual shift applies to the shown time and to the lyrics alike.
    val positionMs = syncedPositionMs((seconds * 1000).toLong(), state.shiftMs)
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
    LyricsArea(state.lyrics, positionMs, following, onScrolledByHand, onTapLine)
}

@Composable
private fun ColumnScope.LyricsArea(
    lyrics: Lyrics?,
    positionMs: Long,
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
        is Lyrics.Synced -> SyncedLyrics(lyrics.lines, positionMs, following, onScrolledByHand, onTapLine)
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
    following: Boolean,
    onScrolledByHand: () -> Unit,
    onTapLine: (Long) -> Unit,
) {
    val current = currentLineIndex(lines, positionMs)
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
                Text(
                    text = line.text.ifEmpty { "♪" },
                    style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else null,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTapLine(line.timeMs) }
                        .padding(vertical = 4.dp),
                )
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

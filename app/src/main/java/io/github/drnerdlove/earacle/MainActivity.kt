package io.github.drnerdlove.earacle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import io.github.drnerdlove.earacle.lyrics.LyricLine
import io.github.drnerdlove.earacle.lyrics.Lyrics
import io.github.drnerdlove.earacle.lyrics.currentLineIndex
import io.github.drnerdlove.earacle.recognition.RecognitionResult
import io.github.drnerdlove.earacle.recognition.formatPosition
import io.github.drnerdlove.earacle.recognition.positionSeconds
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    MainContent(state, onRecognize, modifier)
}

@Composable
fun MainContent(state: UiState, onRecognize: () -> Unit, modifier: Modifier = Modifier) {
    val busy = state is UiState.Listening || state is UiState.Recognizing
    val retry = state is UiState.NotFound || state is UiState.Error ||
        state is UiState.PermissionDenied || state is UiState.Found
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(24.dp))
        if (state is UiState.Found) {
            FoundArea(state)
        } else {
            StatusArea(state)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRecognize, enabled = !busy) {
            Text(stringResource(if (retry) R.string.button_try_again else R.string.button_recognize))
        }
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
private fun ColumnScope.FoundArea(state: UiState.Found) {
    val match: RecognitionResult.Match = state.match
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val seconds = positionSeconds(match.offsetSeconds, state.recordingStartedAtElapsedMs, now)
    val position = formatPosition(seconds)
    val positionMs = (seconds * 1000).toLong()
    Text(match.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(match.artist, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.position_in_song, position),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    LyricsArea(state.lyrics, positionMs)
}

@Composable
private fun ColumnScope.LyricsArea(lyrics: Lyrics?, positionMs: Long) {
    when (lyrics) {
        null -> CircularProgressIndicator()
        Lyrics.NotFound -> Hint(stringResource(R.string.lyrics_not_found))
        Lyrics.Instrumental -> Hint(stringResource(R.string.lyrics_instrumental))
        Lyrics.Failure -> Hint(stringResource(R.string.lyrics_error))
        is Lyrics.Plain -> LyricsText(lyrics.text, plainNote = true)
        is Lyrics.Synced -> SyncedLyrics(lyrics.lines, positionMs)
    }
}

@Composable
private fun ColumnScope.SyncedLyrics(lines: List<LyricLine>, positionMs: Long) {
    val current = currentLineIndex(lines, positionMs)
    val listState = rememberLazyListState()
    // Keep the current line about third from the top.
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(maxOf(0, current - 2))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth(),
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
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.LyricsText(text: String, plainNote: Boolean) {
    if (plainNote) {
        Hint(stringResource(R.string.lyrics_not_synced))
        Spacer(Modifier.height(8.dp))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f, fill = false)
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

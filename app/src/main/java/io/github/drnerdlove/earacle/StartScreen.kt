package io.github.drnerdlove.earacle

import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.drnerdlove.earacle.history.DayKind
import io.github.drnerdlove.earacle.history.HistoryEntry
import io.github.drnerdlove.earacle.history.dayKindOf
import io.github.drnerdlove.earacle.history.formatDay
import io.github.drnerdlove.earacle.lyrics.displayTitle
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/** How many songs of the history the start screen shows under "Recently heard". */
const val START_RECENT_COUNT = 3

/** Size of the recognize button, the ring and the loading indicator: one box, so nothing jumps between phases. */
private val ElementSize = 168.dp

/** The microphone icon has this size in every phase. */
private val MicSize = 44.dp

/**
 * The start screen: search bar, headline, the recognize button (which becomes the listening ring and then the
 * loading indicator), a hint or message, and the recently heard songs at the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartContent(
    state: UiState,
    recent: List<HistoryEntry>,
    onRecognize: () -> Unit,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onOpenRecent: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = state is UiState.Listening || state is UiState.Recognizing
    val showRecent = !busy && recent.isNotEmpty()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxHeight >= CentredLayoutMinHeight) {
            // The element sits in the middle of the screen in every phase: both outer parts have the same weight.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SearchSlot(busy, onSearch, onHistory)
                    Spacer(Modifier.weight(1f))
                    Headline(state)
                    Spacer(Modifier.height(24.dp))
                }
                RecognizeElement(state, onRecognize)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Hint(startHint(state))
                    Spacer(Modifier.weight(1f))
                    if (showRecent) {
                        RecentList(recent, onOpenRecent)
                    }
                }
            }
        } else {
            // Small screens and landscape: the page scrolls, so nothing is cut off.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SearchSlot(busy, onSearch, onHistory)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Headline(state)
                    Spacer(Modifier.height(24.dp))
                    RecognizeElement(state, onRecognize)
                    Spacer(Modifier.height(24.dp))
                    Hint(startHint(state))
                    Spacer(Modifier.height(16.dp))
                }
                if (showRecent) {
                    RecentList(recent, onOpenRecent)
                } else {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
    }
}

/** Screens at least this tall get the layout with the element exactly in the middle; smaller ones scroll. */
private val CentredLayoutMinHeight = 560.dp

/** The search bar, or a blank of the same height while recording (the bar is not usable then; nothing jumps). */
@Composable
private fun SearchSlot(busy: Boolean, onSearch: () -> Unit, onHistory: () -> Unit) {
    if (busy) {
        Spacer(Modifier.height(SearchBarHeight))
    } else {
        SearchBar(onSearch, onHistory)
    }
}

@Composable
private fun Headline(state: UiState) {
    Text(
        text = stringResource(
            when (state) {
                is UiState.Listening -> R.string.status_listening
                UiState.Recognizing -> R.string.status_recognizing
                else -> R.string.start_headline
            },
        ),
        style = MaterialTheme.typography.displaySmallEmphasized,
        textAlign = TextAlign.Center,
    )
}

private val SearchBarHeight = 56.dp

/** The text under the central element; a blank line while recognizing keeps the layout from jumping. */
@Composable
private fun startHint(state: UiState): String = when (state) {
    UiState.Idle -> stringResource(R.string.hint_idle)
    is UiState.Listening -> stringResource(R.string.hint_listening)
    UiState.Recognizing, is UiState.Found -> " "
    UiState.NotFound -> stringResource(R.string.status_not_found)
    is UiState.Error -> stringResource(
        when (state.kind) {
            ErrorKind.MICROPHONE -> R.string.error_microphone
            ErrorKind.NETWORK -> R.string.error_network
            ErrorKind.RATE_LIMITED -> R.string.error_rate_limited
            ErrorKind.SERVER -> R.string.error_server
        },
    )
    UiState.PermissionDenied -> stringResource(R.string.status_permission_denied)
}

@Composable
private fun SearchBar(onSearch: () -> Unit, onHistory: () -> Unit) {
    Surface(
        onClick = onSearch,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchBarHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.search_open),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onHistory) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = stringResource(R.string.history_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The big element in the middle: cookie button (idle), wavy ring (listening) or loading indicator (recognizing). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognizeElement(state: UiState, onRecognize: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(ElementSize),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is UiState.Listening -> {
                val reduced = rememberReducedMotion()
                // The ring reads these States while it draws, so a new level does not recompose the screen.
                val progress = rememberUpdatedState(state.progress)
                val level = rememberUpdatedState(state.level)
                val stroke = with(LocalDensity.current) { Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round) }
                CircularWavyProgressIndicator(
                    progress = { progress.value },
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.24f),
                    stroke = stroke,
                    trackStroke = stroke,
                    amplitude = { if (reduced) 0f else level.value },
                    waveSpeed = if (reduced) 0.dp else WavyProgressIndicatorDefaults.CircularWavelength,
                    modifier = Modifier.size(ElementSize),
                )
                MicIcon(colors.primary)
            }
            UiState.Recognizing -> {
                LoadingIndicator(color = colors.primary, modifier = Modifier.size(ElementSize))
                MicIcon(colors.onPrimary)
            }
            else -> {
                val label = stringResource(R.string.button_recognize)
                Surface(
                    onClick = onRecognize,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = colors.primary,
                    modifier = Modifier
                        .size(ElementSize)
                        .semantics { contentDescription = label },
                ) {
                    // Surface passes its full size to its child; this box lets the icon keep its own size.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MicIcon(colors.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicIcon(tint: androidx.compose.ui.graphics.Color) {
    Icon(
        painter = painterResource(R.drawable.ic_mic),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(MicSize),
    )
}

/** True when the phone's animations are switched off (animator duration scale 0): the ring then only fills. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
private fun RecentList(recent: List<HistoryEntry>, onOpen: (HistoryEntry) -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val now = remember(recent) { System.currentTimeMillis() }
    val yesterday = stringResource(R.string.history_yesterday)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.history_recent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        recent.forEachIndexed { index, entry ->
            val whenText = when (dayKindOf(entry.timeMs, now, zone)) {
                DayKind.TODAY -> timeFormat.format(Date(entry.timeMs))
                DayKind.YESTERDAY -> yesterday
                DayKind.OTHER -> {
                    val date = Instant.ofEpochMilli(entry.timeMs).atZone(zone).toLocalDate()
                    val withYear = date.year != Instant.ofEpochMilli(now).atZone(zone).year
                    formatDay(date, dayPattern(locale, withYear), locale)
                }
            }
            Surface(
                onClick = { onOpen(entry) },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = recentShape(index, recent.size),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(displayTitle(entry.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(entry.artist, whenText).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Big corners at the outer ends of the list, small ones between the items. */
private fun recentShape(index: Int, count: Int): RoundedCornerShape {
    val big = 20.dp
    val small = 6.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun previewEntries() = listOf(
    HistoryEntry("1", System.currentTimeMillis() - 60_000, "Blinding Lights", "The Weeknd", false),
    HistoryEntry("2", System.currentTimeMillis() - 30L * 3_600_000, "Atemlos durch die Nacht", "Helene Fischer", false),
    HistoryEntry("3", System.currentTimeMillis() - 5L * 86_400_000, "Zero to Hero", "Some Artist", true),
)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun StartIdlePreview() {
    EaracleTheme {
        StartContent(UiState.Idle, previewEntries(), {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun StartListeningPreview() {
    EaracleTheme {
        StartContent(UiState.Listening(0.4f, 0.6f), previewEntries(), {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
fun StartRecognizingPreview() {
    EaracleTheme {
        StartContent(UiState.Recognizing, emptyList(), {}, {}, {}, {})
    }
}

package io.github.drnerdlove.earacle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.drnerdlove.earacle.history.HistoryEntry
import io.github.drnerdlove.earacle.lyrics.LrclibTrack
import io.github.drnerdlove.earacle.lyrics.PickerKind
import io.github.drnerdlove.earacle.lyrics.displayTitle
import io.github.drnerdlove.earacle.lyrics.pickerKind
import io.github.drnerdlove.earacle.recognition.formatPosition

/**
 * The search for a song by hand (point 7): a top bar with a back arrow, a rounded text field, a search button and
 * the entries LRCLIB found as rounded list items. The system back gesture is handled by the caller ([MainScreen]);
 * the arrow in the top bar does the same.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPick: (LrclibTrack) -> Unit,
    onBack: () -> Unit,
    /** The newest songs of the history (at most [RECENT_COUNT]), shown while the field is empty. */
    recent: List<HistoryEntry> = emptyList(),
    onPickRecent: (HistoryEntry) -> Unit = {},
    onShowAllHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        PageTopBar(
            title = stringResource(R.string.search_open),
            backLabel = stringResource(R.string.search_back),
            onBack = onBack,
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_field_label)) },
                leadingIcon = {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSearch,
                enabled = state.query.isNotBlank() && !state.loading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.search_go)) }
            Spacer(Modifier.height(16.dp))
            val results = state.results
            when {
                state.query.isBlank() && recent.isNotEmpty() -> RecentSongs(recent, onPickRecent, onShowAllHistory)
                state.loading -> CenteredBox { CircularProgressIndicator() }
                state.failed -> CenteredBox { Hint(stringResource(R.string.search_error)) }
                results == null -> CenteredBox { Hint(stringResource(R.string.search_hint)) }
                results.isEmpty() -> CenteredBox { Hint(stringResource(R.string.search_empty)) }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SegmentGap),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(results) { index, track -> ResultRow(track, segmentShape(index, results.size), onPick) }
                }
            }
        }
    }
}

/** The last songs of the history, with a way to the whole history (point 8). */
@Composable
private fun RecentSongs(recent: List<HistoryEntry>, onPick: (HistoryEntry) -> Unit, onShowAll: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.history_recent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
        ) {
            itemsIndexed(recent, key = { _, entry -> entry.id }) { index, entry ->
                Surface(
                    onClick = { onPick(entry) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = segmentShape(index, recent.size),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(displayTitle(entry.title), style = MaterialTheme.typography.titleMedium)
                        if (entry.artist.isNotBlank()) {
                            Text(
                                entry.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                TextButton(onClick = onShowAll, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.history_show_all))
                }
            }
        }
    }
}

/** How many songs the search screen shows from the history while its field is empty. */
const val RECENT_COUNT = 5

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Title, artist and album, then length and what kind of lyrics the entry has. */
@Composable
private fun ResultRow(track: LrclibTrack, shape: Shape, onPick: (LrclibTrack) -> Unit) {
    val kind = when (pickerKind(track)) {
        PickerKind.SYNCED -> stringResource(R.string.search_kind_synced)
        PickerKind.PLAIN -> stringResource(R.string.search_kind_plain)
        PickerKind.INSTRUMENTAL, null -> stringResource(R.string.search_kind_instrumental)
    }
    val artistAndAlbum = listOfNotNull(track.artistName.takeIf { it.isNotBlank() }, track.albumName).joinToString(" · ")
    val details = listOfNotNull(
        track.durationSeconds.takeIf { it > 0 }?.let { formatPosition(it) },
        kind,
    ).joinToString(" · ")
    Surface(
        onClick = { onPick(track) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(track.trackName, style = MaterialTheme.typography.titleMedium)
            if (artistAndAlbum.isNotEmpty()) {
                Text(
                    artistAndAlbum,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

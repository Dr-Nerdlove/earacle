package io.github.drnerdlove.earacle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The search for a song by hand (point 7): one text field, a search button and the entries LRCLIB found.
 * The system back gesture is handled by the caller ([MainScreen]); the "Back" button here does the same.
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
            .imePadding()
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.search_back)) }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_field_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results) { track -> ResultRow(track, onPick) }
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
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(recent, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(entry) }
                        .padding(vertical = 8.dp),
                ) {
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
            item {
                TextButton(onClick = onShowAll) { Text(stringResource(R.string.history_show_all)) }
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
private fun ResultRow(track: LrclibTrack, onPick: (LrclibTrack) -> Unit) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(track) }
            .padding(vertical = 8.dp),
    ) {
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

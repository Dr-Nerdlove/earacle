package io.github.drnerdlove.earacle

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.drnerdlove.earacle.history.DayKind
import io.github.drnerdlove.earacle.history.HistoryEntry
import io.github.drnerdlove.earacle.history.formatDay
import io.github.drnerdlove.earacle.history.groupByDay
import io.github.drnerdlove.earacle.lyrics.displayTitle
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The history page (point 8): every song heard or picked, newest first, grouped by day. Tap opens a song, a swipe
 * to the side deletes it (with "Undo"), "Clear history" deletes everything after a confirmation. The system back
 * gesture is handled by the caller ([MainScreen]); the "Back" button here does the same.
 *
 * @param entries newest first.
 */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val deletedText = stringResource(R.string.history_deleted)
    val undoText = stringResource(R.string.history_undo)
    val context = LocalContext.current
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    // The heading groups follow the clock only when the list changes; good enough for a page one looks at briefly.
    val groups = remember(entries) { groupByDay(entries, System.currentTimeMillis(), zone) }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.history_back)) }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
                }
            }
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (entries.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Hint(stringResource(R.string.history_empty))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    for (group in groups) {
                        item(key = "day-${group.date}") {
                            val heading = when (group.kind) {
                                DayKind.TODAY -> stringResource(R.string.history_today)
                                DayKind.YESTERDAY -> stringResource(R.string.history_yesterday)
                                DayKind.OTHER -> formatDay(group.date, dayPattern(locale, group.withYear), locale)
                            }
                            Text(
                                heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                time = timeFormat.format(Date(entry.timeMs)),
                                onOpen = { onOpen(entry) },
                                onDelete = {
                                    onDelete(entry.id)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = snackbar.showSnackbar(
                                            deletedText,
                                            actionLabel = undoText,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    snackbar.currentSnackbarData?.dismiss()
                    onClear()
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.history_clear_cancel)) }
            },
        )
    }
}

/** One entry: time, title, artist and a small mark for a song picked by hand. Swipe to the side deletes it. */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    time: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = stringResource(R.string.history_delete_entry)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) {
                false
            } else {
                onDelete()
                true
            }
        },
    )
    // rememberSwipeToDismissBoxState is saveable, and the list keeps saved state per entry id: an entry that comes
    // back through "Undo" would come back still swiped away (red, with the bin). Start every row settled.
    LaunchedEffect(Unit) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = deleteLabel,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                time,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(displayTitle(entry.title), style = MaterialTheme.typography.titleMedium)
                val artistLine = if (entry.byHand) {
                    listOf(entry.artist, stringResource(R.string.history_by_hand)).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    entry.artist
                }
                if (artistLine.isNotBlank()) {
                    Text(
                        artistLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** The language of the phone. */
@Composable
internal fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
}

/** The pattern for a day heading in the phone's language, e.g. "Mon, Nov 23" (with the year when [withYear]). */
internal fun dayPattern(locale: Locale, withYear: Boolean): String =
    DateFormat.getBestDateTimePattern(locale, if (withYear) "EEEdMMMy" else "EEEdMMM")

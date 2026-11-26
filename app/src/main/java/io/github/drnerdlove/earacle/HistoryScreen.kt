package io.github.drnerdlove.earacle

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.drnerdlove.earacle.history.DayKind
import io.github.drnerdlove.earacle.history.HistoryEntry
import io.github.drnerdlove.earacle.history.HistorySelection
import io.github.drnerdlove.earacle.history.formatDay
import io.github.drnerdlove.earacle.history.groupByDay
import io.github.drnerdlove.earacle.lyrics.displayTitle
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The history page (point 8): every song heard or picked, newest first, grouped by day. Tap opens a song, a swipe
 * to the side deletes it (with "Undo"), "Clear history" deletes everything after a confirmation. A long press
 * starts selection mode: taps then select or deselect entries (swiping and opening are off), the top bar shows the
 * number selected and a bin that deletes all of them at once (one "Undo" puts them all back); the arrow or the
 * system back gesture ends the selection first ([historyBackAction]). Without a selection the system back gesture
 * is handled by the caller ([MainScreen]); the arrow in the top bar does the same.
 *
 * @param entries newest first.
 */
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteSelected: (Set<String>) -> Unit,
    onUndoDelete: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // The selected ids are saved as a plain list; ids that are no longer in the list (deleted, undone elsewhere) drop out.
    var savedIds by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    val selection = remember(savedIds, entries) { HistorySelection.of(savedIds).prune(entries.map { it.id }) }
    fun select(next: HistorySelection) {
        savedIds = ArrayList(next.ids)
    }
    LaunchedEffect(selection) {
        if (selection.ids != savedIds) savedIds = ArrayList(selection.ids)
    }
    val selecting = selection.isSelecting
    // Composed after the one in MainActivity, so it wins: back ends the selection and keeps the page open.
    BackHandler(enabled = selecting) {
        when (historyBackAction(selecting)) {
            HistoryBackAction.END_SELECTION -> select(HistorySelection())
            HistoryBackAction.CLOSE_HISTORY -> onBack()
        }
    }
    val deletedText = stringResource(R.string.history_deleted)
    val deletedManyText = pluralStringResource(R.plurals.history_deleted_count, selection.count, selection.count)
    val selectLabel = stringResource(R.string.history_select)
    val undoText = stringResource(R.string.history_undo)
    val context = LocalContext.current
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    // The heading groups follow the clock only when the list changes; good enough for a page one looks at briefly.
    val groups = remember(entries) { groupByDay(entries, System.currentTimeMillis(), zone) }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageTopBar(
                title = if (selecting) {
                    pluralStringResource(R.plurals.history_selected_count, selection.count, selection.count)
                } else {
                    stringResource(R.string.history_title)
                },
                backLabel = stringResource(if (selecting) R.string.history_cancel_selection else R.string.history_back),
                onBack = if (selecting) ({ select(HistorySelection()) }) else onBack,
            ) {
                if (selecting) {
                    IconButton(onClick = {
                        onDeleteSelected(selection.ids.toSet())
                        select(HistorySelection())
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(
                                deletedManyText,
                                actionLabel = undoText,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.history_delete_selected),
                        )
                    }
                } else if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
                }
            }
            if (entries.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Hint(stringResource(R.string.history_empty))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(SegmentGap),
                ) {
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
                                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                            )
                        }
                        itemsIndexed(group.entries, key = { _, entry -> entry.id }) { index, entry ->
                            HistoryRow(
                                entry = entry,
                                time = timeFormat.format(Date(entry.timeMs)),
                                shape = segmentShape(index, group.entries.size),
                                selecting = selecting,
                                selected = entry.id in selection,
                                selectLabel = selectLabel,
                                onOpen = { onOpen(entry) },
                                onToggle = { select(selection.toggle(entry.id)) },
                                onStartSelection = { select(selection.start(entry.id)) },
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

/**
 * One entry as a rounded list item: time, title, artist and a small mark for a song picked by hand. Swipe to the
 * side deletes it; the red background behind it has the same [shape]. A long press starts the selection; while
 * [selecting], a tap toggles the entry (a selected one shows a check mark in place of the time) and swiping is off.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    time: String,
    shape: Shape,
    selecting: Boolean,
    selected: Boolean,
    selectLabel: String,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onStartSelection: () -> Unit,
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
        modifier = Modifier.clip(shape),
        gesturesEnabled = !selecting,
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
        val shapes = ListItemDefaults.shapes(shape = shape)
        val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        val leading: @Composable () -> Unit = {
            if (selecting && selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Text(
                    time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val title: @Composable () -> Unit = {
            Text(displayTitle(entry.title), style = MaterialTheme.typography.titleMedium)
        }
        val artistLine = if (entry.byHand) {
            listOf(entry.artist, stringResource(R.string.history_by_hand)).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            entry.artist
        }
        val supporting: (@Composable () -> Unit)? = if (artistLine.isNotBlank()) {
            {
                Text(
                    artistLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        } else {
            null
        }
        if (selecting) {
            SegmentedListItem(
                checked = selected,
                onCheckedChange = { onToggle() },
                shapes = shapes,
                modifier = Modifier.fillMaxWidth(),
                leadingContent = { Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) { leading() } },
                supportingContent = supporting,
                verticalAlignment = Alignment.Top,
                onLongClick = onToggle,
                onLongClickLabel = selectLabel,
                colors = colors,
                content = title,
            )
        } else {
            SegmentedListItem(
                onClick = onOpen,
                shapes = shapes,
                modifier = Modifier.fillMaxWidth(),
                leadingContent = { Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) { leading() } },
                supportingContent = supporting,
                verticalAlignment = Alignment.Top,
                onLongClick = onStartSelection,
                onLongClickLabel = selectLabel,
                colors = colors,
                content = title,
            )
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

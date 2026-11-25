package io.github.drnerdlove.earacle.history

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Where the history is kept. Calls block (file access): never call them on the main thread. */
interface HistoryStore {
    /** All entries, oldest first. Broken lines are skipped; never throws. */
    fun load(): List<HistoryEntry>

    /** Adds [entry] at the end. */
    fun append(entry: HistoryEntry)

    /** Replaces everything with [entries] (after deleting or restoring one). */
    fun rewrite(entries: List<HistoryEntry>)

    fun clear()
}

/**
 * The history in a file of its own, one JSON object per line ([HistoryLine]): adding appends one line, so it stays
 * fast however long the history gets. Deleting rewrites the file through a temporary file, so a crash never leaves a
 * half-written history. Write failures are swallowed: the history is a convenience and must never crash the app.
 */
class FileHistoryStore(private val file: File) : HistoryStore {
    override fun load(): List<HistoryEntry> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            file.useLines(Charsets.UTF_8) { lines -> lines.mapNotNull { HistoryLine.decode(it) }.toList() }
        }
    } catch (e: IOException) {
        emptyList()
    }

    override fun append(entry: HistoryEntry) {
        try {
            file.parentFile?.mkdirs()
            // A line cut off when the phone died would swallow the new entry: start a fresh line first.
            val prefix = if (endsInsideLine()) "\n" else ""
            file.appendText(prefix + HistoryLine.encode(entry) + "\n", Charsets.UTF_8)
        } catch (e: IOException) {
            // Not saved; the entry stays in memory until the app closes.
        }
    }

    override fun rewrite(entries: List<HistoryEntry>) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(entries.joinToString("") { HistoryLine.encode(it) + "\n" }, Charsets.UTF_8)
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            // Not saved; see append.
        }
    }

    override fun clear() {
        try {
            Files.deleteIfExists(file.toPath())
        } catch (e: IOException) {
            // Not deleted; see append.
        }
    }

    /** True when the file is not empty and its last byte is not a line break. */
    private fun endsInsideLine(): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        RandomAccessFile(file, "r").use {
            it.seek(file.length() - 1)
            return it.read() != '\n'.code
        }
    }
}

package com.offlinep2p.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class DebugLevel {
    INFO,
    WARNING,
    ERROR
}

data class DebugEntry(
    val timestamp: Long,
    val level: DebugLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
) {
    fun format(): String {
        val time = SimpleDateFormat(
            "HH:mm:ss.SSS",
            Locale.US,
        ).format(Date(timestamp))

        val exception = throwable?.let {
            "\n${it.stackTraceToString()}"
        }.orEmpty()

        return "$time  ${level.name.padEnd(7)} $tag  $message$exception"
    }
}

object DebugLogger {

    private const val MAX_ENTRIES = 1000

    private val entries = CopyOnWriteArrayList<DebugEntry>()

    private val listeners =
        CopyOnWriteArrayList<() -> Unit>()

    fun info(
        tag: String,
        message: String,
    ) {
        add(
            DebugEntry(
                timestamp = System.currentTimeMillis(),
                level = DebugLevel.INFO,
                tag = tag,
                message = message,
            )
        )
    }

    fun warning(
        tag: String,
        message: String,
    ) {
        add(
            DebugEntry(
                timestamp = System.currentTimeMillis(),
                level = DebugLevel.WARNING,
                tag = tag,
                message = message,
            )
        )
    }

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        add(
            DebugEntry(
                timestamp = System.currentTimeMillis(),
                level = DebugLevel.ERROR,
                tag = tag,
                message = message,
                throwable = throwable,
            )
        )
    }

    fun snapshot(): List<DebugEntry> {
        return entries.toList()
    }

    fun clear() {
        entries.clear()
        notifyListeners()
    }

    fun addListener(
        listener: () -> Unit,
    ) {
        listeners += listener
    }

    fun removeListener(
        listener: () -> Unit,
    ) {
        listeners -= listener
    }

    fun exportText(): String {
        return buildString {
            appendLine("Turmus Debug Log")
            appendLine("================")
            appendLine("Generated: ${Date()}")
            appendLine()

            entries.forEach {
                appendLine(it.format())
            }
        }
    }

    private fun add(entry: DebugEntry) {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeAt(0)
        }

        entries += entry

        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { listener ->
            runCatching {
                listener()
            }
        }
    }
}

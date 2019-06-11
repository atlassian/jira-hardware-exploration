package com.atlassian.performance.tools.lib.table

import java.util.*

/**
 * Provides generic functionality for creating basic ascii tables
 *
 * see @see GenericPlainTextTableTest.shouldGenerateTable() for basic usage
 *
 * Assumes the first row added is the header, but that can be defined in the constructor. Dynamically works out column widths so you don't have to.
 */
class GenericPlainTextTable(private val headerRowCount: Int = 1) {
    private val _builder = StringBuilder()
    private val _formatter = Formatter(_builder)
    private val _rows = mutableListOf<List<String>>()
    private val _columnWidths = mutableListOf<Int>()

    fun addRow(entries: List<String>) {
        val limit = entries.size - 1
        for (i in 0..limit) {
            val requiredWidth = entries[i].length
            val currentWidth =
                if (_columnWidths.size > i)
                    _columnWidths[i]
                else {
                    _columnWidths.add(i, 0)
                    0
                }
            if (requiredWidth > currentWidth)
                _columnWidths[i] = requiredWidth
        }

        _rows.add(entries)
    }

    fun addRowEmpty(filler: String = "") {
        val entries = mutableListOf<String>()
        repeat(_columnWidths.size) { entries.add(filler) }
        addRow(entries)
    }

    fun generate(): String {
        val divider = divider()

        // top
        _formatter.format(divider)

        _rows.forEach {
            val rowFormat = StringBuilder()
            val limit = it.size - 1
            for (i in 0..limit) {
                rowFormat.append("| %-${_columnWidths[i]}s")
            }
            rowFormat.append("|\n")

            _formatter.format(rowFormat.toString(), *it.toTypedArray())

            if (rowCount() == headerRowCount)
                _formatter.format(divider)
        }

        // top
        _formatter.format(divider)

        return _formatter.toString()
    }

    // extra -1 to account for top line of the table
    private fun rowCount() = _formatter.toString().split("\n").size - 1 - 1

    private fun divider(): String {
        val builder = StringBuilder()
        _columnWidths
            .asSequence()
            .forEach {
                builder.append("*")
                // allow for 1 space before as padding
                for (i in 1..(it + 1))
                    builder.append("-")
            }
        builder.append("*\n")

        return builder.toString()
    }

}

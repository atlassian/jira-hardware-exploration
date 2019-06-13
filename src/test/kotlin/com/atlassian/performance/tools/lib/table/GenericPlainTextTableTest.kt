package com.atlassian.performance.tools.lib.table

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThat
import org.junit.Test

class GenericPlainTextTableTest {

    /**
     * Prove basic usage
     *
     *  val table = GenericPlainTextTable()
     *  table.addRow(listOf( "", "Run", "Apdex", "Total Actions", "Error %", "Satisfactory %", "Tolerable %"))
     *  table.addRow(listOf( "best", "5", "0.7713201820940819", "23155", "0.389", "59.490", "34.684"))
     *  table.addRow(listOf( "worst", "6", "0.7677944046844503", "23143", "0.380", "58.925", "35.125"))
     *  println(table.generate())
     *
     * giving
     *
     *  *------*----*-------------------*--------------*--------*---------------*------------*
     *  |      | Run| Apdex             | Total Actions| Error %| Satisfactory %| Tolerable %|
     *  *------*----*-------------------*--------------*--------*---------------*------------*
     *  | best | 5  | 0.7713201820940819| 23155        | 0.389  | 59.490        | 34.684     |
     *  | worst| 6  | 0.7677944046844503| 23143        | 0.380  | 58.925        | 35.125     |
     *  *------*----*-------------------*--------------*--------*---------------*------------*
     *
     *

     */
    @Test
    fun shouldGenerateTable() {
        val table = GenericPlainTextTable()
        val headerValues = listOf("", "Run", "Apdex", "Total Actions", "Error %", "Satisfactory %", "Tolerable %")
        table.addRow(headerValues)
        table.addRow(listOf("best", "5", "0.7713201820940819", "23155", "0.389", "59.490", "34.684"))
        table.addRow(listOf("worst", "6", "0.7677944046844503", "23143", "0.380", "58.925", "35.125"))

        val tableText = table.generate()
        val lines = tableText.split('\n')


        assertThat("# of lines should be top + bottom + header divider + header + # of rows + final line feed",
            lines.size,
            equalTo(7))

        assertThat("With a header there should be 3 divider lines",
            lines.filter { it.startsWith("*") }.count(),
            equalTo(3))

        assertArrayEquals("Dividers should be the first, last and the one after the header",
            intArrayOf(0, 2, 5),
            lines.withIndex().filter { it.value.startsWith("*") }.map { it.index }.toIntArray())

        assertArrayEquals("* indicates the boundaries of a column in a divider, there should be 1 more than the column count",
            intArrayOf(8, 8, 8),
            lines.withIndex().filter { it.value.startsWith("*") }.map { it.value.count { c -> "*".contains(c) } }.toIntArray())

        assertArrayEquals("Data should be the header line and all between the header divier and the last line",
            intArrayOf(1, 3, 4),
            lines.withIndex().filter { it.value.startsWith("|") }.map { it.index }.toIntArray())

        assertArrayEquals("| indicates the boundaries of a column in a data line, there should be 1 more than the column count",
            intArrayOf(8, 8, 8),
            lines.withIndex().filter { it.value.startsWith("|") }.map { it.value.count { c -> "|".contains(c) } }.toIntArray())

        assertThat("2nd line is the header", "|      | Run| Apdex             | Total Actions| Error %| Satisfactory %| Tolerable %|", equalTo(lines[1]))
        assertThat("4th line is data", "| best | 5  | 0.7713201820940819| 23155        | 0.389  | 59.490        | 34.684     |", equalTo(lines[3]))
        assertThat("5th line is data", "| worst| 6  | 0.7677944046844503| 23143        | 0.380  | 58.925        | 35.125     |", equalTo(lines[4]))

    }

    @Test
    fun shouldGenerateTableWithDeepHeader() {
        val table = GenericPlainTextTable(2)
        val headerValues1 = listOf("", "Run", "Apdex", "Total Actions", "Error %", "Satisfactory %", "Tolerable %")
        val headerValues2 = listOf("2", "2", "2", "2", "2", "2", "2")
        table.addRow(headerValues1)
        table.addRow(headerValues2)
        table.addRow(listOf("best", "5", "0.7713201820940819", "23155", "0.389", "59.490", "34.684"))
        table.addRow(listOf("worst", "6", "0.7677944046844503", "23143", "0.380", "58.925", "35.125"))

        val tableText = table.generate()
        val lines = tableText.split('\n')

        assertThat("# of lines should be top + bottom + header divider + header + # of rows + final line feed",
            lines.size,
            equalTo(8))

        assertThat("With a header there should be 3 divider lines",
            lines.filter { it.startsWith("*") }.count(),
            equalTo(3))

        assertArrayEquals("Dividers should be the first, last and the one after the header",
            intArrayOf(0, 3, 6),
            lines.withIndex().filter { it.value.startsWith("*") }.map { it.index }.toIntArray())
    }
}


package com.skrypko.dicefamiliar.domain.cards.io

/**
 * Minimal RFC 4180 reader: comma-separated, `"` quoting with `""` escapes, quoted line breaks,
 * CRLF or LF line ends, an optional UTF-8 BOM. Blank lines are dropped. Shared by CSV importers.
 */
object CsvReader {

    /** Rows of cells; throws [IllegalArgumentException] on an unterminated quote. */
    fun read(text: String): List<List<String>> {
        val input = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() {
            row += cell.toString()
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows += row.toList()
            row.clear()
        }
        while (i < input.length) {
            val c = input[i]
            when {
                quoted && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' && (quoted || cell.isBlank()) -> {
                    if (!quoted) cell.setLength(0)
                    quoted = !quoted
                }
                quoted -> cell.append(c)
                c == ',' -> endCell()
                c == '\r' -> Unit
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "Unterminated quote" }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}

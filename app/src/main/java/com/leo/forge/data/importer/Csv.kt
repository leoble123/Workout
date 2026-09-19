package com.leo.forge.data.importer

/**
 * RFC 4180 CSV reader.
 *
 * Hevy puts free-text workout and exercise notes into the export, which routinely contain
 * commas, quotes and newlines. Splitting on ',' shifts every later column on those rows and
 * silently corrupts the import, so quoting is handled properly here.
 */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        // Strip a UTF-8 BOM, which Excel-exported files often carry.
        val s = if (text.isNotEmpty() && text[0] == '﻿') text.substring(1) else text

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            if (row.size > 1 || row.firstOrNull()?.isNotBlank() == true) rows.add(row)
            row = mutableListOf()
        }

        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> { if (i + 1 < s.length && s[i + 1] == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Normalises a header cell so "Weight (kg)", "weight_kg" and "WeightKg" all match. */
    fun normalizeHeader(h: String): String =
        h.lowercase().filter { it.isLetterOrDigit() }
}

package dev.wfhdiary.app.export

import dev.wfhdiary.app.backup.BackupDay
import dev.wfhdiary.app.data.store.DiaryRules
import dev.wfhdiary.app.util.FinancialYearHelper
import dev.wfhdiary.app.util.TimeHelpers
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    private const val HEADER_ROW = 5
    private const val FIRST_DATA_ROW = 6
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val COLUMN_WIDTHS = listOf(12, 18, 11, 11, 11, 9, 36, 12)
    private val HEADERS = listOf("Day", "Date", "Started", "Finished", "Break (hrs)", "Hours", "Description", "Home/Office")

    private const val S_TITLE = 1
    private const val S_BOLD = 2
    private const val S_HEADER = 3
    private const val S_DATE = 4
    private const val S_INPUT_TIME = 5
    private const val S_INPUT_BREAK = 6
    private const val S_HOURS = 7
    private const val S_HOME = 8
    private const val S_OFFICE = 9
    private const val S_LEAVE = 10
    private const val S_SUM_DECIMAL = 11
    private const val S_SUM_CURRENCY = 12
    private const val S_SUM_INTEGER = 13

    fun filename(fyYear: Int): String = "WFH-Diary-$fyYear-${fyYear + 1}.xlsx"

    fun write(fyYear: Int, displayName: String, days: List<BackupDay>): ByteArray {
        val (fyStart, fyEnd) = FinancialYearHelper.fyRange(fyYear)
        val lastDataRow = FIRST_DATA_ROW + ChronoUnit.DAYS.between(fyStart, fyEnd).toInt()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbookXml(lastDataRow))
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheetXml(fyYear, displayName, days, fyStart, fyEnd, lastDataRow))
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(
        fyYear: Int,
        displayName: String,
        days: List<BackupDay>,
        fyStart: LocalDate,
        fyEnd: LocalDate,
        lastDataRow: Int,
    ): String {
        val byDate = days.associateBy { it.date }
        val rows = StringBuilder()
        val fyLabel = "$fyYear/${"%02d".format(Locale.ROOT, (fyYear + 1) % 100)}"

        rows.row(1) { text("A", "$fyLabel Working from Home Diary", S_TITLE) }
        rows.row(2) {
            text("A", "Name:", S_BOLD)
            text("B", displayName, 0)
        }
        rows.row(HEADER_ROW) {
            HEADERS.forEachIndexed { i, header -> text(column(i), header, S_HEADER) }
        }

        var r = FIRST_DATA_ROW
        for (date in FinancialYearHelper.eachDay(fyStart, fyEnd)) {
            val entry = byDate[date.toString()]
            rows.row(r) { dayRow(this, r, date, entry) }
            r++
        }

        val summaryRow = lastDataRow + 2
        val hours = "F$FIRST_DATA_ROW:F$lastDataRow"
        val locations = "H$FIRST_DATA_ROW:H$lastDataRow"
        val summary =
            listOf(
                Triple("Total hours worked", "SUM($hours)", S_SUM_DECIMAL),
                Triple("Home hours", "SUMIF($locations,\"Home\",$hours)", S_SUM_DECIMAL),
                Triple("Claimable WFH (Home hours × \$0.67)", "B${summaryRow + 1}*0.67", S_SUM_CURRENCY),
                Triple("Office hours", "SUMIF($locations,\"Office\",$hours)", S_SUM_DECIMAL),
                Triple("Days at home", "COUNTIF($locations,\"Home\")", S_SUM_INTEGER),
                Triple("Days in office", "COUNTIF($locations,\"Office\")", S_SUM_INTEGER),
            )
        summary.forEachIndexed { i, (label, formula, style) ->
            rows.row(summaryRow + i) {
                text("A", label, S_BOLD)
                formula("B", formula, style)
            }
        }

        val cols =
            COLUMN_WIDTHS.mapIndexed { i, w ->
                "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>"
            }.joinToString("")

        return XML_HEADER +
            "<worksheet xmlns=\"$MAIN_NS\">" +
            "<sheetViews><sheetView workbookViewId=\"0\">" +
            "<pane ySplit=\"5\" topLeftCell=\"A6\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
            "</sheetView></sheetViews>" +
            "<sheetFormatPr defaultRowHeight=\"15\"/>" +
            "<cols>$cols</cols>" +
            "<sheetData>$rows</sheetData>" +
            "<autoFilter ref=\"A$HEADER_ROW:H$lastDataRow\"/>" +
            "</worksheet>"
    }

    private fun dayRow(row: RowBuilder, r: Int, date: LocalDate, entry: BackupDay?) {
        row.text("A", DiaryRules.dayName(date.toString()), 0)
        row.number("B", ChronoUnit.DAYS.between(EXCEL_EPOCH, date).toString(), S_DATE)

        val start = TimeHelpers.parseTimeString(entry?.startTime)
        val finish = TimeHelpers.parseTimeString(entry?.finishTime)
        row.numberOrEmpty("C", start?.let { (it / 1440.0).toString() }, S_INPUT_TIME)
        row.numberOrEmpty("D", finish?.let { (it / 1440.0).toString() }, S_INPUT_TIME)
        row.numberOrEmpty("E", entry?.breakHours?.let { BigDecimal.valueOf(it).toString() }, S_INPUT_BREAK)

        when {
            start != null && finish != null -> row.formula("F", "(D$r-C$r)*24-E$r", S_HOURS)
            entry?.description == DiaryRules.LEAVE -> row.number("F", "0", S_HOURS)
            else -> row.empty("F", S_HOURS)
        }

        row.text("G", entry?.description.orEmpty(), 0)
        val locationStyle =
            when {
                entry?.location.equals("Home", ignoreCase = true) -> S_HOME
                entry?.location.equals("Office", ignoreCase = true) -> S_OFFICE
                entry?.description.equals(DiaryRules.LEAVE, ignoreCase = true) -> S_LEAVE
                else -> 0
            }
        row.text("H", entry?.location.orEmpty(), locationStyle)
    }

    private fun column(index: Int): String = ('A' + index).toString()

    private inline fun StringBuilder.row(r: Int, build: RowBuilder.() -> Unit) {
        val builder = RowBuilder(r)
        builder.build()
        append("<row r=\"").append(r).append("\">").append(builder.cells).append("</row>")
    }

    private class RowBuilder(private val r: Int) {
        val cells = StringBuilder()

        private fun open(col: String, style: Int, type: String? = null): StringBuilder {
            cells.append("<c r=\"").append(col).append(r).append('"')
            if (style != 0) cells.append(" s=\"").append(style).append('"')
            if (type != null) cells.append(" t=\"").append(type).append('"')
            return cells
        }

        fun text(col: String, value: String, style: Int) {
            if (value.isEmpty()) {
                if (style != 0) empty(col, style)
                return
            }
            open(col, style, "inlineStr")
                .append("><is><t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>")
        }

        fun number(col: String, value: String, style: Int) {
            open(col, style).append("><v>").append(value).append("</v></c>")
        }

        fun numberOrEmpty(col: String, value: String?, style: Int) {
            if (value == null) empty(col, style) else number(col, value, style)
        }

        fun formula(col: String, formula: String, style: Int) {
            open(col, style).append("><f>").append(formula).append("</f></c>")
        }

        fun empty(col: String, style: Int) {
            open(col, style).append("/>")
        }
    }

    private fun escape(value: String): String =
        buildString(value.length) {
            for (c in value) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
                }
            }
        }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val CONTENT_TYPES =
        XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private const val WORKBOOK_RELS =
        XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

    private fun workbookXml(lastDataRow: Int): String =
        XML_HEADER +
            "<workbook xmlns=\"$MAIN_NS\" xmlns:r=\"$REL_NS\">" +
            "<sheets><sheet name=\"Diary\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">" +
            "Diary!\$A\$$HEADER_ROW:\$H\$$lastDataRow</definedName></definedNames>" +
            "<calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/>" +
            "</workbook>"

    private fun xf(numFmt: Int = 0, font: Int = 0, fill: Int = 0, center: Boolean = false): String {
        val attrs = StringBuilder("numFmtId=\"$numFmt\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\"")
        if (numFmt != 0) attrs.append(" applyNumberFormat=\"1\"")
        if (font != 0) attrs.append(" applyFont=\"1\"")
        if (fill != 0) attrs.append(" applyFill=\"1\"")
        return if (center) {
            "<xf $attrs applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>"
        } else {
            "<xf $attrs/>"
        }
    }

    private fun solidFill(rgb: String) =
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$rgb\"/><bgColor indexed=\"64\"/></patternFill></fill>"

    // cellXfs order must match the S_* constants above.
    private val STYLES: String =
        XML_HEADER +
            "<styleSheet xmlns=\"$MAIN_NS\">" +
            "<numFmts count=\"3\">" +
            "<numFmt numFmtId=\"164\" formatCode=\"ddd d mmm yyyy\"/>" +
            "<numFmt numFmtId=\"165\" formatCode=\"h:mm AM/PM\"/>" +
            "<numFmt numFmtId=\"166\" formatCode=\"\$#,##0.00\"/>" +
            "</numFmts>" +
            "<fonts count=\"4\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            "<fills count=\"7\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            solidFill("12262F") +
            solidFill("DEEAF6") +
            solidFill("70AD47") +
            solidFill("81D4FA") +
            solidFill("E0E0E0") +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"14\">" +
            xf() +
            xf(font = 1) +
            xf(font = 2) +
            xf(font = 3, fill = 2, center = true) +
            xf(numFmt = 164) +
            xf(numFmt = 165, fill = 3) +
            xf(numFmt = 2, fill = 3) +
            xf(numFmt = 2) +
            xf(font = 3, fill = 4) +
            xf(font = 2, fill = 5) +
            xf(fill = 6) +
            xf(numFmt = 2, font = 2) +
            xf(numFmt = 166, font = 2) +
            xf(numFmt = 1, font = 2) +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
}

package dev.wfhdiary.app.export

import dev.wfhdiary.app.backup.BackupDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {
    private val days =
        listOf(
            BackupDay("2025-07-01", "08:00", "17:00", 1.0, "Work & stuff", "Home"),
            BackupDay("2025-07-02", "08:30", "16:30", 0.5, null, "Office"),
            BackupDay("2025-07-03", description = "LEAVE"),
        )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    private val sheet by lazy { unzip(XlsxWriter.write(2025, "Jane <Doe>", days))["xl/worksheets/sheet1.xml"]!! }

    private fun cell(ref: String): String {
        val match = Regex("<c r=\"$ref\"[^>]*?(/>|>.*?</c>)").find(sheet)
        return match?.value ?: error("cell $ref not found")
    }

    @Test
    fun package_has_required_parts() {
        val parts = unzip(XlsxWriter.write(2025, "x", days)).keys
        assertTrue(
            parts.containsAll(
                listOf(
                    "[Content_Types].xml",
                    "_rels/.rels",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/worksheets/sheet1.xml",
                ),
            ),
        )
    }

    @Test
    fun workbook_names_sheet_diary() {
        val workbook = unzip(XlsxWriter.write(2025, "x", days))["xl/workbook.xml"]!!
        assertTrue(workbook.contains("<sheet name=\"Diary\" sheetId=\"1\" r:id=\"rId1\"/>"))
    }

    @Test
    fun title_and_name_are_written_and_escaped() {
        assertTrue(cell("A1").contains("2025/26 Working from Home Diary"))
        assertTrue(cell("A1").contains("s=\"1\""))
        assertTrue(cell("A2").contains("Name:"))
        assertTrue(cell("B2").contains("Jane &lt;Doe&gt;"))
    }

    @Test
    fun headers_are_on_row_five() {
        val expected = listOf("Day", "Date", "Started", "Finished", "Break (hrs)", "Hours", "Description", "Home/Office")
        "ABCDEFGH".forEachIndexed { i, col ->
            val c = cell("${col}5")
            assertTrue(c.contains(expected[i]))
            assertTrue(c.contains("s=\"3\""))
        }
    }

    @Test
    fun first_day_row_has_values_formula_and_styles() {
        assertTrue(cell("A6").contains("Tuesday"))
        assertTrue(cell("B6").contains("<v>45839</v>"))
        assertTrue(cell("C6").contains("<v>0.3333333333333333</v>"))
        assertTrue(cell("E6").contains("<v>1.0</v>"))
        assertTrue(cell("F6").contains("<f>(D6-C6)*24-E6</f>"))
        assertTrue(cell("G6").contains("Work &amp; stuff"))
        assertTrue(cell("H6").contains("s=\"8\""))
        assertTrue(cell("H7").contains("s=\"9\""))
    }

    @Test
    fun leave_day_has_zero_hours_and_grey_location() {
        assertTrue(cell("F8").contains("<v>0</v>"))
        assertEquals("<c r=\"H8\" s=\"10\"/>", cell("H8"))
    }

    @Test
    fun empty_day_keeps_input_styling() {
        assertEquals("<c r=\"C9\" s=\"5\"/>", cell("C9"))
        assertEquals("<c r=\"F9\" s=\"7\"/>", cell("F9"))
    }

    @Test
    fun one_row_per_fy_day_and_summary_formulas() {
        // FY2025 has 365 days: rows 6..370.
        assertTrue(cell("A370").contains("Tuesday"))
        assertTrue(cell("B372").contains("<f>SUM(F6:F370)</f>"))
        assertTrue(cell("B373").contains("<f>SUMIF(H6:H370,\"Home\",F6:F370)</f>"))
        assertTrue(cell("A374").contains("Claimable WFH (Home hours × \$0.67)"))
        assertTrue(cell("B374").contains("<f>B373*0.67</f>"))
        assertTrue(cell("B374").contains("s=\"12\""))
        assertTrue(cell("B376").contains("<f>COUNTIF(H6:H370,\"Home\")</f>"))
        assertTrue(cell("B377").contains("s=\"13\""))
    }

    @Test
    fun layout_freeze_widths_and_autofilter() {
        assertTrue(sheet.contains("<pane ySplit=\"5\" topLeftCell=\"A6\" activePane=\"bottomLeft\" state=\"frozen\"/>"))
        assertTrue(sheet.contains("<col min=\"7\" max=\"7\" width=\"36\" customWidth=\"1\"/>"))
        assertTrue(sheet.contains("<autoFilter ref=\"A5:H370\"/>"))
    }

    @Test
    fun filename_matches_previous_server_name() {
        assertEquals("WFH-Diary-2025-2026.xlsx", XlsxWriter.filename(2025))
    }
}

package com.kasirpro.app.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.lang.StringBuilder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ExcelHelper {

    /**
     * Parses a CSV or XLSX stream and returns rows of string columns.
     */
    fun parseStream(inputStream: InputStream, isSpreadsheet: Boolean): List<List<String>> {
        return if (isSpreadsheet) {
            readXlsx(inputStream)
        } else {
            readCsv(inputStream)
        }
    }

    /**
     * Parses a standard CSV stream. Supports quoted cells with commas.
     */
    fun readCsv(inputStream: InputStream): List<List<String>> {
        val result = mutableListOf<List<String>>()
        try {
            val reader = inputStream.bufferedReader()
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.trim().isEmpty()) return@forEach
                    val row = mutableListOf<String>()
                    var inQuotes = false
                    val currentField = StringBuilder()
                    var i = 0
                    while (i < line.length) {
                        val c = line[i]
                        if (c == '\"') {
                            // Check if doubled quote representing a literal quote
                            if (i + 1 < line.length && line[i + 1] == '\"') {
                                currentField.append('\"')
                                i++ // skip next quote
                            } else {
                                inQuotes = !inQuotes
                            }
                        } else if (c == ',' && !inQuotes) {
                            row.add(currentField.toString().trim())
                            currentField.setLength(0)
                        } else if (c == ';' && !inQuotes) {
                            // Support semicolon-delimited CSV as well
                            row.add(currentField.toString().trim())
                            currentField.setLength(0)
                        } else {
                            currentField.append(c)
                        }
                        i++
                    }
                    row.add(currentField.toString().trim())
                    if (row.isNotEmpty() && row.any { it.isNotBlank() }) {
                        result.add(row)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * Parses custom multi-sheet .xlsx ZIP archive using XmlPullParser.
     */
    fun readXlsx(inputStream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableMapOf<Int, MutableMap<Int, String>>() // RowIndex -> ColIndex -> CellValue

        try {
            val zipBytes = inputStream.readBytes()

            // Pass 1: Extract xl/sharedStrings.xml
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        parseSharedStrings(zip, sharedStrings)
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Pass 2: Extract xl/worksheets/sheet1.xml
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/worksheets/sheet1.xml") {
                        parseSheetXml(zip, sharedStrings, sheetRows)
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Convert the map data structure to list of lists sorted by row order
        if (sheetRows.isEmpty()) return emptyList()
        val maxRowIdx = sheetRows.keys.maxOrNull() ?: 1
        val result = mutableListOf<List<String>>()

        for (r in 1..maxRowIdx) {
            val cols = sheetRows[r] ?: emptyMap()
            val maxColIdx = if (cols.isEmpty()) -1 else cols.keys.maxOrNull() ?: 0
            val rowList = mutableListOf<String>()
            for (c in 0..maxColIdx) {
                rowList.add(cols[c] ?: "")
            }
            // Add non-empty rows
            result.add(rowList)
        }
        return result
    }

    private fun parseSharedStrings(inputStream: InputStream, outList: MutableList<String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType
            val textBuilder = StringBuilder()
            var insideT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "t") {
                            insideT = true
                            textBuilder.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideT) {
                            textBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "t") {
                            insideT = false
                            outList.add(textBuilder.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseSheetXml(
        inputStream: InputStream,
        sharedStrings: List<String>,
        outRows: MutableMap<Int, MutableMap<Int, String>>
    ) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType

            var currentRowIdx = -1
            var currentColCode = ""
            var cellType = "" // "s" for shared string, "inlineStr" for inline string
            var insideV = false
            var insideInlineT = false
            val valueBuilder = StringBuilder()
            val inlineTextBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "row") {
                            val rAttr = parser.getAttributeValue(null, "r")
                            currentRowIdx = rAttr?.toIntOrNull() ?: -1
                        } else if (tag == "c") {
                            val rAttr = parser.getAttributeValue(null, "r") ?: ""
                            currentColCode = rAttr.filter { it.isLetter() }
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                        } else if (tag == "v") {
                            insideV = true
                            valueBuilder.setLength(0)
                        } else if (tag == "t") {
                            // Inside inlineStr cell
                            insideInlineT = true
                            inlineTextBuilder.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideV) {
                            valueBuilder.append(parser.text)
                        }
                        if (insideInlineT) {
                            inlineTextBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "v") {
                            insideV = false
                            if (currentRowIdx != -1 && currentColCode.isNotEmpty()) {
                                val rawValue = valueBuilder.toString()
                                val colIndex = colCodeToIndex(currentColCode)
                                val finalVal = if (cellType == "s") {
                                    val idx = rawValue.toIntOrNull() ?: -1
                                    if (idx in sharedStrings.indices) sharedStrings[idx] else ""
                                } else {
                                    rawValue
                                }
                                val colMap = outRows.getOrPut(currentRowIdx) { mutableMapOf() }
                                colMap[colIndex] = finalVal
                            }
                        } else if (tag == "t") {
                            insideInlineT = false
                            if (currentRowIdx != -1 && currentColCode.isNotEmpty() && cellType == "inlineStr") {
                                val colIndex = colCodeToIndex(currentColCode)
                                val colMap = outRows.getOrPut(currentRowIdx) { mutableMapOf() }
                                colMap[colIndex] = inlineTextBuilder.toString()
                            }
                        } else if (tag == "c") {
                            currentColCode = ""
                            cellType = ""
                        } else if (tag == "row") {
                            currentRowIdx = -1
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun colCodeToIndex(code: String): Int {
        var index = 0
        for (i in 0 until code.length) {
            index = index * 26 + (code[i].uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    /**
     * Generates a fully compliant, beautiful XLSX template with 3 sheets (Sheet 1: Template, Sheet 2: Kategori, Sheet 3: Satuan).
     */
    fun writeProductTemplateXlsx(outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // File 1: [Content_Types].xml
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(contentTypes.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 2: _rels/.rels
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(rels.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 3: xl/workbook.xml
        val workbook = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Template Produk" sheetId="1" r:id="rId1"/>
                <sheet name="Daftar Kategori" sheetId="2" r:id="rId2"/>
                <sheet name="Daftar Satuan" sheetId="3" r:id="rId3"/>
              </sheets>
            </workbook>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(workbook.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 4: xl/_rels/workbook.xml.rels
        val workbookRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
            </Relationships>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(workbookRels.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 5: xl/worksheets/sheet1.xml (Template Produk)
        val sheet1 = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>Nama Produk (wajib)</t></is></c>
                  <c r="B1" t="inlineStr"><is><t>Kategori (wajib)</t></is></c>
                  <c r="C1" t="inlineStr"><is><t>Harga Jual (wajib)</t></is></c>
                  <c r="D1" t="inlineStr"><is><t>Harga Modal</t></is></c>
                  <c r="E1" t="inlineStr"><is><t>Stok Awal</t></is></c>
                  <c r="F1" t="inlineStr"><is><t>Stok Minimum</t></is></c>
                  <c r="G1" t="inlineStr"><is><t>Barcode (opsional)</t></is></c>
                  <c r="H1" t="inlineStr"><is><t>Nama File Foto (opsional)</t></is></c>
                  <c r="I1" t="inlineStr"><is><t>Satuan</t></is></c>
                </row>
                <row r="2">
                  <c r="A2" t="inlineStr"><is><t>Kopi Susu Gula Aren</t></is></c>
                  <c r="B2" t="inlineStr"><is><t>Minuman</t></is></c>
                  <c r="C2"><v>15000</v></c>
                  <c r="D2"><v>10000</v></c>
                  <c r="E2"><v>50</v></c>
                  <c r="F2"><v>5</v></c>
                  <c r="G2" t="inlineStr"><is><t>8991234567890</t></is></c>
                  <c r="H2" t="inlineStr"><is><t>kopi_aren.jpg</t></is></c>
                  <c r="I2" t="inlineStr"><is><t>Pcs</t></is></c>
                </row>
                <row r="3">
                  <c r="A3" t="inlineStr"><is><t>Roti Bakar Cokelat</t></is></c>
                  <c r="B3" t="inlineStr"><is><t>Makanan</t></is></c>
                  <c r="C3"><v>18000</v></c>
                  <c r="D3"><v>12000</v></c>
                  <c r="E3"><v>20</v></c>
                  <c r="F3"><v>5</v></c>
                  <c r="G3" t="inlineStr"><is><t></t></is></c>
                  <c r="H3" t="inlineStr"><is><t>roti_bakar.png</t></is></c>
                  <c r="I3" t="inlineStr"><is><t>Pack</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zip.write(sheet1.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 6: xl/worksheets/sheet2.xml (Daftar Kategori yang Disarankan)
        val sheet2 = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>Kategori yang Disarankan</t></is></c>
                </row>
                <row r="2"><c r="A2" t="inlineStr"><is><t>Makanan</t></is></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>Minuman</t></is></c></row>
                <row r="4"><c r="A4" t="inlineStr"><is><t>Snack</t></is></c></row>
                <row r="5"><c r="A5" t="inlineStr"><is><t>Rokok</t></is></c></row>
                <row r="6"><c r="A6" t="inlineStr"><is><t>Sembako</t></is></c></row>
                <row r="7"><c r="A7" t="inlineStr"><is><t>Kebersihan</t></is></c></row>
                <row r="8"><c r="A8" t="inlineStr"><is><t>Kesehatan</t></is></c></row>
                <row r="9"><c r="A9" t="inlineStr"><is><t>Elektronik</t></is></c></row>
                <row r="10"><c r="A10" t="inlineStr"><is><t>Pakaian</t></is></c></row>
                <row r="11"><c r="A11" t="inlineStr"><is><t>Alat Tulis</t></is></c></row>
                <row r="12"><c r="A12" t="inlineStr"><is><t>Lainnya</t></is></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
        zip.write(sheet2.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // File 7: xl/worksheets/sheet3.xml (Daftar Satuan yang Tersedia)
        val sheet3 = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>Daftar Satuan yang Tersedia</t></is></c>
                </row>
                <row r="2"><c r="A2" t="inlineStr"><is><t>Pcs</t></is></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>Kg</t></is></c></row>
                <row r="4"><c r="A4" t="inlineStr"><is><t>Gram</t></is></c></row>
                <row r="5"><c r="A5" t="inlineStr"><is><t>Liter</t></is></c></row>
                <row r="6"><c r="A6" t="inlineStr"><is><t>Ml</t></is></c></row>
                <row r="7"><c r="A7" t="inlineStr"><is><t>Lusin</t></is></c></row>
                <row r="8"><c r="A8" t="inlineStr"><is><t>Karton</t></is></c></row>
                <row r="9"><c r="A9" t="inlineStr"><is><t>Pack</t></is></c></row>
                <row r="10"><c r="A10" t="inlineStr"><is><t>Botol</t></is></c></row>
                <row r="11"><c r="A11" t="inlineStr"><is><t>Sachet</t></is></c></row>
                <row r="12"><c r="A12" t="inlineStr"><is><t>Lainnya</t></is></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet3.xml"))
        zip.write(sheet3.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.close()
    }

    /**
     * Extracts images embedded directly inside an XML spreadsheet file (.xlsx) under xl/media/
     * and maps row indices (1-based because spreadsheet data starts after headers) to image ByteArrays.
     */
    fun extractImagesFromXlsx(inputStream: InputStream): Map<Int, ByteArray> {
        val results = mutableMapOf<Int, ByteArray>()
        try {
            val zipBytes = inputStream.readBytes()
            var drawingFile = ""
            var drawingRelsFile = ""

            // Pass 1: Find sheet1 drawing ID
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/worksheets/_rels/sheet1.xml.rels") {
                        val relsStr = zip.reader(Charsets.UTF_8).readText()
                        val match = Regex("""Id="([^"]+)"[^>]+Target="[^"]*drawings/drawing([^"]+)"""").find(relsStr)
                        if (match != null) {
                            drawingFile = "xl/drawings/drawing" + match.groupValues[2]
                            drawingRelsFile = "xl/drawings/_rels/drawing" + match.groupValues[2] + ".rels"
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (drawingFile.isEmpty()) {
                drawingFile = "xl/drawings/drawing1.xml"
                drawingRelsFile = "xl/drawings/_rels/drawing1.xml.rels"
            }

            // Pass 2: Parse relationships mapped rId -> target (filename)
            val relsMap = mutableMapOf<String, String>()
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == drawingRelsFile) {
                        parseDelsXml(zip, relsMap)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Pass 3: Parse drawing xml mapped: 0-based RowIndex -> rId
            val rowToRId = mutableMapOf<Int, String>()
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == drawingFile) {
                        parseDrawingXml(zip, rowToRId)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Pass 4: Map media files: filename -> bytes
            val fileToBytes = mutableMapOf<String, ByteArray>()
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("xl/media/")) {
                        val filename = entry.name.substringAfterLast("/")
                        fileToBytes[filename] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // Combine: Row index (1-based Excel row, so anchor row + 1) -> bytes
            for ((anchorRow, rId) in rowToRId) {
                val targetFilename = relsMap[rId] ?: continue
                val bytes = fileToBytes[targetFilename] ?: continue
                results[anchorRow + 1] = bytes
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun parseDelsXml(inputStream: InputStream, outMap: MutableMap<String, String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val idAttr = parser.getAttributeValue(null, "Id") ?: ""
                    val targetAttr = parser.getAttributeValue(null, "Target") ?: ""
                    if (idAttr.isNotEmpty() && targetAttr.isNotEmpty()) {
                        val filename = targetAttr.substringAfterLast("/")
                        outMap[idAttr] = filename
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseDrawingXml(inputStream: InputStream, outMap: MutableMap<Int, String>) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType
            var currentRow = -1
            var insideFrom = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "from") {
                            insideFrom = true
                        } else if (tag == "row" && insideFrom) {
                            currentRow = parser.nextText().toIntOrNull() ?: -1
                        } else if (tag == "blip") {
                            var rId = ""
                            for (i in 0 until parser.attributeCount) {
                                val name = parser.getAttributeName(i)
                                if (name == "embed" || name.endsWith(":embed")) {
                                    rId = parser.getAttributeValue(i)
                                }
                            }
                            if (rId.isNotEmpty() && currentRow != -1) {
                                outMap[currentRow] = rId
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "from") {
                            insideFrom = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

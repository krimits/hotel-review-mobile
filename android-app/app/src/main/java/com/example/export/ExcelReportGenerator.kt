package com.example.export

import com.example.domain.HostPulseEngine
import com.example.model.ActionTaskStatus
import com.example.model.AssignedActionTask
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.RecommendedAction
import com.example.model.TopicPatternSignal
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

data class ExcelCellData(
    val displayValue: String,
    val numericValue: Double? = null,
    val styleIndex: Int = 0
)

data class ExcelSheetPreview(
    val sheetName: String,
    val columnWidthsChars: List<Int>,
    val rows: List<List<ExcelCellData>>
)

data class ExcelWorkbookPreview(
    val file: File,
    val sheets: List<ExcelSheetPreview>
)

object ExcelReportGenerator {

    // Style indices inside xl/styles.xml:
    // 0 = Normal cell
    // 1 = Title banner (#0F4C5C fill, white bold font)
    // 2 = Table header (#CDE9F2 fill, dark bold font, thin border)
    // 3 = Metric label (bold)
    // 4 = Numeric / Metric value (bold)
    // 5 = Wrapped text cell for reviews, quotes & AI replies

    fun generateExcelWorkbook(
        outputFile: File,
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        reviews: List<HotelReview>,
        patterns: List<TopicPatternSignal> = emptyList(),
        recommendations: List<RecommendedAction> = emptyList(),
        reviewPropertyMap: Map<String, String> = emptyMap(),
        checkedReviewIds: Set<String> = emptySet(),
        assignedTasks: List<AssignedActionTask> = emptyList()
    ): ExcelWorkbookPreview {
        outputFile.parentFile?.mkdirs()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("el-GR"))
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("el-GR"))
        val generatedStr = dateTimeFormat.format(Date(metadata.generatedAtMillis))

        val sheet1 = buildSummaryKpiSheet(
            metadata = metadata,
            snapshot = snapshot,
            generatedStr = generatedStr,
            recommendations = recommendations,
            assignedTasks = assignedTasks
        )
        val sheet2 = buildPatternsAndActionsSheet(
            snapshot = snapshot,
            patterns = patterns,
            recommendations = recommendations,
            assignedTasks = assignedTasks
        )
        val sheet3 = buildDetailedReviewsSheet(
            reviews = reviews,
            dateFormat = dateFormat,
            reviewPropertyMap = reviewPropertyMap,
            checkedReviewIds = checkedReviewIds,
            assignedTasks = assignedTasks
        )
        val sheets = listOf(sheet1, sheet2, sheet3)

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", buildContentTypesXml(sheets.size))
            writeEntry(zip, "_rels/.rels", buildRootRelsXml())
            writeEntry(zip, "xl/workbook.xml", buildWorkbookXml(sheets))
            writeEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            writeEntry(zip, "xl/styles.xml", buildStylesXml())
            sheets.forEachIndexed { idx, sheet ->
                writeEntry(
                    zip,
                    "xl/worksheets/sheet${idx + 1}.xml",
                    buildWorksheetXml(sheet)
                )
            }
        }

        return ExcelWorkbookPreview(file = outputFile, sheets = sheets)
    }

    private fun buildSummaryKpiSheet(
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        generatedStr: String,
        recommendations: List<RecommendedAction>,
        assignedTasks: List<AssignedActionTask>
    ): ExcelSheetPreview {
        val rows = mutableListOf<List<ExcelCellData>>()

        rows.add(
            listOf(
                ExcelCellData("ΑΝΑΦΟΡΑ ΑΠΟΔΟΣΗΣ — ${metadata.hotelName}", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1)
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Εύρος / Τοποθεσία", styleIndex = 3),
                ExcelCellData(metadata.hotelLocation.ifBlank { "—" }),
                ExcelCellData("Έκδοση: $generatedStr")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Περίοδος Αναφοράς", styleIndex = 3),
                ExcelCellData(metadata.periodLabel),
                ExcelCellData("")
            )
        )
        if (!metadata.executiveSummary.isNullOrBlank()) {
            rows.add(
                listOf(
                    ExcelCellData("Επιτελική Σύνοψη", styleIndex = 3),
                    ExcelCellData(metadata.executiveSummary, styleIndex = 5),
                    ExcelCellData("")
                )
            )
        }
        rows.add(emptyList())

        // KPI Table
        rows.add(
            listOf(
                ExcelCellData("Δείκτης Απόδοσης (KPI)", styleIndex = 2),
                ExcelCellData("Τιμή", styleIndex = 2),
                ExcelCellData("Σημείωση / Λεπτομέρεια", styleIndex = 2)
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Μέση Βαθμολογία (/10)", styleIndex = 3),
                ExcelCellData(snapshot.formattedAverageRating, numericValue = snapshot.averageRating, styleIndex = 4),
                ExcelCellData("Κλίμακα 1.0 έως 10.0")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Σύνολο Κριτικών", styleIndex = 3),
                ExcelCellData("${snapshot.totalReviews}", numericValue = snapshot.totalReviews.toDouble(), styleIndex = 4),
                ExcelCellData("Αναλυμένες κριτικές περιόδου")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Θετικές Κριτικές (%)", styleIndex = 3),
                ExcelCellData("${snapshot.positivePercent}%", numericValue = snapshot.positivePercent.toDouble(), styleIndex = 4),
                ExcelCellData("${snapshot.positiveCount} θετικές κριτικές")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Ουδέτερες Κριτικές (%)", styleIndex = 3),
                ExcelCellData("${snapshot.neutralPercent}%", numericValue = snapshot.neutralPercent.toDouble(), styleIndex = 4),
                ExcelCellData("${snapshot.neutralCount} ουδέτερες κριτικές")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Αρνητικές Κριτικές (%)", styleIndex = 3),
                ExcelCellData("${snapshot.negativePercent}%", numericValue = snapshot.negativePercent.toDouble(), styleIndex = 4),
                ExcelCellData("${snapshot.negativeCount} αρνητικές κριτικές")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Εκκρεμή Επείγοντα Ζητήματα", styleIndex = 3),
                ExcelCellData("${snapshot.urgentUnresolvedCount}", numericValue = snapshot.urgentUnresolvedCount.toDouble(), styleIndex = 4),
                ExcelCellData("Υψηλής προτεραιότητας προς επίλυση")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Ποσοστό Τακτοποίησης (%)", styleIndex = 3),
                ExcelCellData("${snapshot.resolutionRatePercent}%", numericValue = snapshot.resolutionRatePercent.toDouble(), styleIndex = 4),
                ExcelCellData("${snapshot.resolvedCount} από ${snapshot.totalReviews} τακτοποιήθηκαν")
            )
        )

        val openTasksCount = assignedTasks.count { it.status != ActionTaskStatus.DONE }
        val doneTasksCount = assignedTasks.count { it.status == ActionTaskStatus.DONE }
        rows.add(
            listOf(
                ExcelCellData("Προτεινόμενες Εκκρεμείς Ενέργειες", styleIndex = 3),
                ExcelCellData("${recommendations.size}", numericValue = recommendations.size.toDouble(), styleIndex = 4),
                ExcelCellData("Μόνο πραγματικά εκκρεμή προβλήματα (όχι λυμένα/υποθέσεις)")
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Ανατεθειμένες Εργασίες Ομάδας", styleIndex = 3),
                ExcelCellData("$openTasksCount ανοικτές / $doneTasksCount ολοκληρωμένες", styleIndex = 4),
                ExcelCellData("Σύνολο αναθέσεων: ${assignedTasks.size}")
            )
        )

        rows.add(emptyList())
        rows.add(
            listOf(
                ExcelCellData("Πηγή Κριτικών", styleIndex = 2),
                ExcelCellData("Πλήθος Κριτικών", styleIndex = 2),
                ExcelCellData("Μερίδιο", styleIndex = 2)
            )
        )
        snapshot.sourceCounts.entries.sortedByDescending { it.value }.forEach { (source, count) ->
            val pct = if (snapshot.totalReviews == 0) 0 else ((count * 100.0) / snapshot.totalReviews).roundToInt()
            rows.add(
                listOf(
                    ExcelCellData(source.labelEl),
                    ExcelCellData("$count", numericValue = count.toDouble(), styleIndex = 4),
                    ExcelCellData("$pct%")
                )
            )
        }

        return ExcelSheetPreview(
            sheetName = "Συνοπτικά KPIs",
            columnWidthsChars = listOf(36, 48, 42),
            rows = rows
        )
    }

    private fun buildPatternsAndActionsSheet(
        snapshot: HotelAnalyticsSnapshot,
        patterns: List<TopicPatternSignal>,
        recommendations: List<RecommendedAction>,
        assignedTasks: List<AssignedActionTask>
    ): ExcelSheetPreview {
        val rows = mutableListOf<List<ExcelCellData>>()

        // Section 1: Repeating Negative Patterns (7 columns)
        rows.add(
            listOf(
                ExcelCellData("ΕΠΑΝΑΛΑΜΒΑΝΟΜΕΝΑ ΜΟΤΙΒΑ ΠΑΡΑΠΟΝΩΝ", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1)
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Θέμα / Μοτίβο", styleIndex = 2),
                ExcelCellData("Αρνητικές", styleIndex = 2),
                ExcelCellData("Σύνολο Αναφ.", styleIndex = 2),
                ExcelCellData("Ποσοστό (%)", styleIndex = 2),
                ExcelCellData("Καταλύματα", styleIndex = 2),
                ExcelCellData("Μεταβολή (μ.π.)", styleIndex = 2),
                ExcelCellData("Ενδεικτικό Απόσπασμα", styleIndex = 2)
            )
        )
        if (patterns.isEmpty()) {
            rows.add(
                listOf(
                    ExcelCellData("Δεν εντοπίστηκαν επαναλαμβανόμενα αρνητικά μοτίβα στην περίοδο.")
                )
            )
        } else {
            patterns.forEach { signal ->
                val deltaStr = when {
                    signal.rateChangePoints == null -> "Νέο σήμα"
                    signal.rateChangePoints > 0 -> "+${signal.rateChangePoints} μ.π."
                    else -> "${signal.rateChangePoints} μ.π."
                }
                val topQuote = signal.evidence.firstOrNull()?.let { "«${it.quote}» (${it.propertyName})" } ?: "—"
                rows.add(
                    listOf(
                        ExcelCellData(signal.aspect.labelEl, styleIndex = 3),
                        ExcelCellData("${signal.negativeCount}", numericValue = signal.negativeCount.toDouble(), styleIndex = 4),
                        ExcelCellData("${signal.totalMentions}", numericValue = signal.totalMentions.toDouble()),
                        ExcelCellData("${signal.negativeRatePercent}%", numericValue = signal.negativeRatePercent.toDouble()),
                        ExcelCellData("${signal.propertyCount}", numericValue = signal.propertyCount.toDouble()),
                        ExcelCellData(deltaStr),
                        ExcelCellData(topQuote, styleIndex = 5)
                    )
                )
            }
        }

        rows.add(emptyList())

        // Section 2: Prioritized Action Recommendations (Severity x 30d Recurrence)
        rows.add(
            listOf(
                ExcelCellData("ΠΡΟΤΕΡΑΙΟΠΟΙΗΜΕΝΕΣ ΠΡΟΤΑΣΕΙΣ ΕΝΕΡΓΕΙΩΝ (Σοβαρότητα × Συχνότητα)", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1)
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Σκορ", styleIndex = 2),
                ExcelCellData("Σοβαρ.", styleIndex = 2),
                ExcelCellData("Επαν. (30μ)", styleIndex = 2),
                ExcelCellData("Κατάλυμα", styleIndex = 2),
                ExcelCellData("Τμήμα", styleIndex = 2),
                ExcelCellData("Προτεινόμενη Ενέργεια", styleIndex = 2),
                ExcelCellData("Χρόνος (λ.)", styleIndex = 2),
                ExcelCellData("Κόστος (€)", styleIndex = 2),
                ExcelCellData("Επιβεβαίωση", styleIndex = 2),
                ExcelCellData("Ακριβές Απόσπασμα", styleIndex = 2)
            )
        )
        if (recommendations.isEmpty()) {
            rows.add(
                listOf(
                    ExcelCellData("Δεν υπάρχουν εκκρεμείς προτάσεις ενεργειών (όλα τα ζητήματα ανατέθηκαν ή δεν εκκρεμούν).")
                )
            )
        } else {
            recommendations.forEach { rec ->
                rows.add(
                    listOf(
                        ExcelCellData(String.format(Locale.US, "%.1f", rec.priorityScore), numericValue = rec.priorityScore, styleIndex = 4),
                        ExcelCellData(if (rec.severity == 3) "Επείγον (3)" else "Κανονικό (2)"),
                        ExcelCellData("${rec.recurrence30d}", numericValue = rec.recurrence30d.toDouble()),
                        ExcelCellData(rec.propertyName, styleIndex = 3),
                        ExcelCellData(rec.departmentNameEl),
                        ExcelCellData(rec.title, styleIndex = 5),
                        ExcelCellData("${rec.effortMinutes}", numericValue = rec.effortMinutes.toDouble()),
                        ExcelCellData("${rec.costEuros}", numericValue = rec.costEuros.toDouble()),
                        ExcelCellData(if (rec.isAnalysisConfirmed) "Επιβεβαιωμένη" else "Προς επιβεβαίωση"),
                        ExcelCellData("«${rec.quote}»", styleIndex = 5)
                    )
                )
            }
        }

        if (assignedTasks.isNotEmpty()) {
            rows.add(emptyList())
            rows.add(
                listOf(
                    ExcelCellData("ΑΝΑΤΕΘΕΙΜΕΝΕΣ ΕΡΓΑΣΙΕΣ ΟΜΑΔΑΣ", styleIndex = 1),
                    ExcelCellData("", styleIndex = 1),
                    ExcelCellData("", styleIndex = 1),
                    ExcelCellData("", styleIndex = 1),
                    ExcelCellData("", styleIndex = 1),
                    ExcelCellData("", styleIndex = 1)
                )
            )
            rows.add(
                listOf(
                    ExcelCellData("Κατάσταση", styleIndex = 2),
                    ExcelCellData("Κατάλυμα", styleIndex = 2),
                    ExcelCellData("Τμήμα", styleIndex = 2),
                    ExcelCellData("Εργασία", styleIndex = 2),
                    ExcelCellData("Χρόνος / Κόστος", styleIndex = 2),
                    ExcelCellData("Απόσπασμα", styleIndex = 2)
                )
            )
            assignedTasks.forEach { task ->
                rows.add(
                    listOf(
                        ExcelCellData(task.status.labelEl, styleIndex = 3),
                        ExcelCellData(task.propertyName),
                        ExcelCellData(task.departmentNameEl),
                        ExcelCellData(task.title, styleIndex = 5),
                        ExcelCellData("${task.effortMinutes} λ. • ${task.costEuros}€"),
                        ExcelCellData("«${task.quote}»", styleIndex = 5)
                    )
                )
            }
        }

        rows.add(emptyList())

        // Section 3: Department Triage Breakdown
        rows.add(
            listOf(
                ExcelCellData("ΤΑΣΕΙΣ ΤΜΗΜΑΤΩΝ ΞΕΝΟΔΟΧΕΙΟΥ", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1),
                ExcelCellData("", styleIndex = 1)
            )
        )
        rows.add(
            listOf(
                ExcelCellData("Τμήμα Ξενοδοχείου", styleIndex = 2),
                ExcelCellData("Σύνολο Αναφορών", styleIndex = 2),
                ExcelCellData("Παράπονα", styleIndex = 2),
                ExcelCellData("Επείγοντα Εκκρεμή", styleIndex = 2),
                ExcelCellData("Μέση Βαθμολογία", styleIndex = 2),
                ExcelCellData("Μερίδιο (%)", styleIndex = 2)
            )
        )
        snapshot.departmentStats.forEach { stat ->
            val avgFormatted = if (stat.totalMentions == 0) "0.0" else String.format(Locale.US, "%.1f", stat.averageRating)
            rows.add(
                listOf(
                    ExcelCellData(stat.department.labelEl, styleIndex = 3),
                    ExcelCellData("${stat.totalMentions}", numericValue = stat.totalMentions.toDouble()),
                    ExcelCellData("${stat.complaintCount}", numericValue = stat.complaintCount.toDouble(), styleIndex = 4),
                    ExcelCellData("${stat.urgentCount}", numericValue = stat.urgentCount.toDouble()),
                    ExcelCellData(avgFormatted, numericValue = stat.averageRating),
                    ExcelCellData("${stat.shareOfReviewsPercent}%", numericValue = stat.shareOfReviewsPercent.toDouble())
                )
            )
        }

        return ExcelSheetPreview(
            sheetName = "Μοτίβα & Ενέργειες",
            columnWidthsChars = listOf(22, 16, 15, 24, 18, 42, 14, 14, 20, 48),
            rows = rows
        )
    }

    private fun buildDetailedReviewsSheet(
        reviews: List<HotelReview>,
        dateFormat: SimpleDateFormat,
        reviewPropertyMap: Map<String, String>,
        checkedReviewIds: Set<String>,
        assignedTasks: List<AssignedActionTask>
    ): ExcelSheetPreview {
        val rows = mutableListOf<List<ExcelCellData>>()
        rows.add(
            listOf(
                ExcelCellData("Ημερομηνία", styleIndex = 2),
                ExcelCellData("Κατάλυμα", styleIndex = 2),
                ExcelCellData("Πελάτης", styleIndex = 2),
                ExcelCellData("Πηγή", styleIndex = 2),
                ExcelCellData("Βαθμολογία", styleIndex = 2),
                ExcelCellData("Συναίσθημα", styleIndex = 2),
                ExcelCellData("Διαλογή (4 κατ.)", styleIndex = 2),
                ExcelCellData("Έλεγχος", styleIndex = 2),
                ExcelCellData("Κατάσταση Εργασίας", styleIndex = 2),
                ExcelCellData("Τμήματα", styleIndex = 2),
                ExcelCellData("Σύνοψη", styleIndex = 2),
                ExcelCellData("Πλήρες Κείμενο Κριτικής", styleIndex = 2),
                ExcelCellData("Προτεινόμενη Απάντηση", styleIndex = 2)
            )
        )

        reviews.forEach { r ->
            val propName = HotelProperty.displayName(reviewPropertyMap[r.id])
            val analysis = HostPulseEngine.analyzeReviewText(r.reviewText)
            val workflow = HostPulseEngine.reviewWorkflowStatus(r, assignedTasks, analysis)
            val checkedLabel = if (r.id in checkedReviewIds) "Ελέγχθηκε" else "Προς έλεγχο"
            rows.add(
                listOf(
                    ExcelCellData(dateFormat.format(Date(r.stayDateMillis))),
                    ExcelCellData(propName, styleIndex = 3),
                    ExcelCellData(r.guestName, styleIndex = 3),
                    ExcelCellData(r.sourceEnum.labelEl),
                    ExcelCellData(String.format(Locale.US, "%.1f", r.rating), numericValue = r.rating, styleIndex = 4),
                    ExcelCellData(r.sentimentEnum.labelEl),
                    ExcelCellData(analysis.triageCategory.labelEl),
                    ExcelCellData(checkedLabel),
                    ExcelCellData(workflow.labelEl),
                    ExcelCellData(r.departmentEnums.joinToString(", ") { it.shortLabelEl }),
                    ExcelCellData(r.summaryLine, styleIndex = 5),
                    ExcelCellData(r.reviewText, styleIndex = 5),
                    ExcelCellData(r.suggestedReply, styleIndex = 5)
                )
            )
        }

        return ExcelSheetPreview(
            sheetName = "Αναλυτικές Κριτικές",
            columnWidthsChars = listOf(14, 24, 20, 16, 12, 13, 22, 14, 18, 26, 36, 52, 52),
            rows = rows
        )
    }

    private fun writeEntry(zip: ZipOutputStream, entryPath: String, xmlContent: String) {
        val entry = ZipEntry(entryPath)
        zip.putNextEntry(entry)
        zip.write(xmlContent.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun buildWorkbookXml(sheets: List<ExcelSheetPreview>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets>""")
        sheets.forEachIndexed { idx, sheet ->
            val safeName = escapeXml(sheet.sheetName.take(31))
            append("""<sheet name="$safeName" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("""</sheets></workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildStylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="3">""" +
            """<font><sz val="11"/><color rgb="FF191C1D"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FF001F27"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="4">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF0F4C5C"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFCDE9F2"/></patternFill></fill>""" +
            """</fills>""" +
            """<borders count="2">""" +
            """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
            """<border><left style="thin"><color rgb="FFB0BEC5"/></left><right style="thin"><color rgb="FFB0BEC5"/></right><top style="thin"><color rgb="FFB0BEC5"/></top><bottom style="thin"><color rgb="FFB0BEC5"/></bottom><diagonal/></border>""" +
            """</borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="6">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
            """<xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>""" +
            """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="right"/></xf>""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1" vertical="top"/></xf>""" +
            """</cellXfs>""" +
            """</styleSheet>"""

    private fun buildWorksheetXml(sheet: ExcelSheetPreview): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (sheet.columnWidthsChars.isNotEmpty()) {
            append("""<cols>""")
            sheet.columnWidthsChars.forEachIndexed { idx, width ->
                val colNum = idx + 1
                append("""<col min="$colNum" max="$colNum" width="$width" customWidth="1"/>""")
            }
            append("""</cols>""")
        }
        append("""<sheetData>""")
        sheet.rows.forEachIndexed { rIdx, row ->
            val rowNumber = rIdx + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { cIdx, cell ->
                val cellRef = "${columnLetters(cIdx)}$rowNumber"
                val styleAttr = if (cell.styleIndex > 0) """ s="${cell.styleIndex}"""" else ""
                if (cell.numericValue != null && cell.numericValue.isFinite()) {
                    val formattedNum = if (cell.numericValue % 1.0 == 0.0) {
                        cell.numericValue.toLong().toString()
                    } else {
                        String.format(Locale.US, "%.2f", cell.numericValue)
                    }
                    append("""<c r="$cellRef"$styleAttr t="n"><v>$formattedNum</v></c>""")
                } else {
                    val safeText = escapeXml(cell.displayValue)
                    append("""<c r="$cellRef"$styleAttr t="inlineStr"><is><t xml:space="preserve">$safeText</t></is></c>""")
                }
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }

    private fun columnLetters(zeroBasedCol: Int): String {
        var n = zeroBasedCol
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\n' || ch == '\r' || ch == '\t' || ch.code >= 0x20 -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

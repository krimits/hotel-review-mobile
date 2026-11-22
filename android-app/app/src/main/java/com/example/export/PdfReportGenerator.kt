package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelReview
import com.example.model.SentimentType
import com.example.model.TriagePriority
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportMetadata(
    val hotelName: String,
    val hotelLocation: String,
    val periodLabel: String,
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val executiveSummary: String? = null
)

data class GeneratedPdfBundle(
    val pdfFile: File,
    val previewBitmaps: List<Bitmap>
)

object PdfReportGenerator {

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    private const val MARGIN = 32f

    private val colorAegean = Color.parseColor("#0F4C5C")
    private val colorAegeanLight = Color.parseColor("#E6F2F6")
    private val colorTerracotta = Color.parseColor("#9C5426")
    private val colorAlabaster = Color.parseColor("#FAF8F5")
    private val colorCardBorder = Color.parseColor("#D8DFE2")
    private val colorTextPrimary = Color.parseColor("#191C1D")
    private val colorTextSecondary = Color.parseColor("#4B5558")
    private val colorPositive = Color.parseColor("#1B7F5A")
    private val colorNeutral = Color.parseColor("#B26E00")
    private val colorNegative = Color.parseColor("#C4322B")

    private data class PreparedReviewRow(
        val index: Int,
        val review: HotelReview,
        val dateStr: String,
        val textLayout: StaticLayout,
        val rowHeight: Float
    )

    fun generatePdfAndPreview(
        context: Context,
        outputFile: File,
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        reviews: List<HotelReview>,
        previewScale: Int = 2
    ): GeneratedPdfBundle {
        outputFile.parentFile?.mkdirs()
        val pageDrawers = buildPageDrawers(context, metadata, snapshot, reviews)

        // 1. Write native vector multi-page A4 PDF
        val document = PdfDocument()
        try {
            pageDrawers.forEachIndexed { idx, drawPage ->
                val pageNumber = idx + 1
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                val page = document.startPage(pageInfo)
                drawPage(page.canvas)
                document.finishPage(page)
            }
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }

        // 2. Render crisp high-DPI page bitmaps for instant in-app preview
        val bitmaps = renderPreviewBitmapsFromDrawers(pageDrawers, previewScale)
        return GeneratedPdfBundle(pdfFile = outputFile, previewBitmaps = bitmaps)
    }

    fun renderPreviewBitmapsOnly(
        context: Context,
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        reviews: List<HotelReview>,
        scale: Int = 2
    ): List<Bitmap> {
        val pageDrawers = buildPageDrawers(context, metadata, snapshot, reviews)
        return renderPreviewBitmapsFromDrawers(pageDrawers, scale)
    }

    private fun renderPreviewBitmapsFromDrawers(
        pageDrawers: List<(Canvas) -> Unit>,
        scale: Int
    ): List<Bitmap> {
        val safeScale = scale.coerceIn(1, 3)
        return pageDrawers.map { drawPage ->
            val bmp = Bitmap.createBitmap(
                PAGE_WIDTH * safeScale,
                PAGE_HEIGHT * safeScale,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            canvas.save()
            canvas.scale(safeScale.toFloat(), safeScale.toFloat())
            drawPage(canvas)
            canvas.restore()
            bmp
        }
    }

    private fun buildPageDrawers(
        context: Context,
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        reviews: List<HotelReview>
    ): List<(Canvas) -> Unit> {
        val headingFace = runCatching {
            ResourcesCompat.getFont(context, R.font.manrope)
        }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val bodyFace = runCatching {
            ResourcesCompat.getFont(context, R.font.ibm_plex_sans)
        }.getOrNull() ?: Typeface.SANS_SERIF

        val boldHeading = Typeface.create(headingFace, Typeface.BOLD)
        val mediumHeading = Typeface.create(headingFace, Typeface.NORMAL)
        val regularBody = Typeface.create(bodyFace, Typeface.NORMAL)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("el", "GR"))
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("el", "GR"))
        val generatedDateText = dateTimeFormat.format(Date(metadata.generatedAtMillis))

        // Paginate review rows ahead of time so total page count is exact
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val commentColWidth = (contentWidth * 0.48f).toInt()
        val reviewPages = mutableListOf<List<PreparedReviewRow>>()

        if (reviews.isNotEmpty()) {
            val headerStartY = 96f
            val maxBottomY = PAGE_HEIGHT - 48f
            var currentPageRows = mutableListOf<PreparedReviewRow>()
            var runningY = headerStartY

            reviews.forEachIndexed { idx, review ->
                val reviewExcerpt = buildString {
                    if (review.summaryLine.isNotBlank()) {
                        append(review.summaryLine.trim())
                        append("\n")
                    }
                    append("«")
                    append(review.reviewText.trim().take(280))
                    if (review.reviewText.length > 280) append("…")
                    append("»")
                }
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = regularBody
                    textSize = 8.5f
                    color = colorTextPrimary
                }
                val textLayout = buildStaticLayout(reviewExcerpt, textPaint, commentColWidth - 12)
                val rowHeight = maxOf(48f, textLayout.height + 16f)

                if (currentPageRows.isNotEmpty() && runningY + rowHeight > maxBottomY) {
                    reviewPages.add(currentPageRows)
                    currentPageRows = mutableListOf()
                    runningY = headerStartY
                }

                currentPageRows.add(
                    PreparedReviewRow(
                        index = idx,
                        review = review,
                        dateStr = dateFormat.format(Date(review.stayDateMillis)),
                        textLayout = textLayout,
                        rowHeight = rowHeight
                    )
                )
                runningY += rowHeight
            }
            if (currentPageRows.isNotEmpty()) {
                reviewPages.add(currentPageRows)
            }
        }

        val totalPages = 1 + reviewPages.size
        val drawers = mutableListOf<(Canvas) -> Unit>()

        // Page 1 drawer
        drawers.add { canvas ->
            drawFirstPage(
                canvas = canvas,
                metadata = metadata,
                snapshot = snapshot,
                generatedDateText = generatedDateText,
                boldHeading = boldHeading,
                mediumHeading = mediumHeading,
                regularBody = regularBody
            )
            drawFooter(canvas, 1, totalPages, metadata.hotelName, regularBody)
        }

        // Pages 2..N drawers
        reviewPages.forEachIndexed { rPageIdx, rowsOnPage ->
            val pageNum = 2 + rPageIdx
            drawers.add { canvas ->
                var y = drawReviewsPageHeader(
                    canvas = canvas,
                    metadata = metadata,
                    totalFiltered = reviews.size,
                    boldHeading = boldHeading,
                    regularBody = regularBody
                )
                rowsOnPage.forEach { row ->
                    drawReviewRow(
                        canvas = canvas,
                        y = y,
                        rowHeight = row.rowHeight,
                        index = row.index,
                        review = row.review,
                        dateStr = row.dateStr,
                        textLayout = row.textLayout,
                        boldHeading = boldHeading,
                        regularBody = regularBody
                    )
                    y += row.rowHeight
                }
                drawFooter(canvas, pageNum, totalPages, metadata.hotelName, regularBody)
            }
        }

        return drawers
    }

    private fun drawFirstPage(
        canvas: Canvas,
        metadata: ReportMetadata,
        snapshot: HotelAnalyticsSnapshot,
        generatedDateText: String,
        boldHeading: Typeface,
        mediumHeading: Typeface,
        regularBody: Typeface
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = colorCardBorder
        }

        canvas.drawColor(Color.WHITE)

        // Header Banner
        fillPaint.color = colorAegean
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 112f, fillPaint)
        fillPaint.color = colorTerracotta
        canvas.drawRect(0f, 108f, PAGE_WIDTH.toFloat(), 113f, fillPaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 18f
            color = Color.WHITE
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 10f
            color = Color.parseColor("#D6EEF5")
        }

        canvas.drawText("ΑΝΑΦΟΡΑ ΑΠΟΔΟΣΗΣ & ΚΡΙΤΙΚΩΝ ΞΕΝΟΔΟΧΕΙΟΥ", MARGIN, 36f, headerSubPaint)
        canvas.drawText(metadata.hotelName.take(48), MARGIN, 62f, headerTitlePaint)
        val subtitleLine = buildString {
            if (metadata.hotelLocation.isNotBlank()) {
                append(metadata.hotelLocation)
                append("  •  ")
            }
            append("Περίοδος: ${metadata.periodLabel}")
            append("  •  Έκδοση: $generatedDateText")
        }
        canvas.drawText(subtitleLine, MARGIN, 86f, headerSubPaint)

        var y = 132f
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        // 4 KPI Cards
        val gap = 10f
        val cardWidth = (contentWidth - 3 * gap) / 4f
        val cardHeight = 68f

        val kpis = listOf(
            Triple("ΜΕΣΗ ΒΑΘΜΟΛΟΓΙΑ", "${snapshot.formattedAverageRating} / 10", colorAegean),
            Triple("ΣΥΝΟΛΟ ΚΡΙΤΙΚΩΝ", "${snapshot.totalReviews}", colorTextPrimary),
            Triple("ΘΕΤΙΚΟ ΚΛΙΜΑ", "${snapshot.positivePercent}%", colorPositive),
            Triple(
                "ΕΠΕΙΓΟΝΤΑ ΕΚΚΡΕΜΗ",
                "${snapshot.urgentUnresolvedCount}",
                if (snapshot.urgentUnresolvedCount > 0) colorNegative else colorPositive
            )
        )

        kpis.forEachIndexed { idx, (label, value, valueColor) ->
            val left = MARGIN + idx * (cardWidth + gap)
            val rect = RectF(left, y, left + cardWidth, y + cardHeight)
            fillPaint.color = colorAlabaster
            canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

            val kpiLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldHeading
                textSize = 7.5f
                color = colorTextSecondary
            }
            val kpiValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldHeading
                textSize = 16f
                color = valueColor
            }
            canvas.drawText(label, left + 10f, y + 22f, kpiLabelPaint)
            canvas.drawText(value, left + 10f, y + 48f, kpiValPaint)
        }

        y += cardHeight + 16f

        // Optional AI Executive Summary Box
        val summaryText = metadata.executiveSummary?.trim().orEmpty()
        if (summaryText.isNotEmpty()) {
            val summaryTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regularBody
                textSize = 9.5f
                color = colorTextPrimary
            }
            val layout = buildStaticLayout(summaryText, summaryTextPaint, (contentWidth - 28f).toInt())
            val boxHeight = layout.height + 38f
            val boxRect = RectF(MARGIN, y, MARGIN + contentWidth, y + boxHeight)
            fillPaint.color = colorAegeanLight
            canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint)

            val boxTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = boldHeading
                textSize = 10f
                color = colorAegean
            }
            canvas.drawText(
                "ΕΠΙΤΕΛΙΚΗ ΣΥΝΟΨΗ AI ΓΙΑ ΤΗ ΔΙΟΙΚΗΣΗ & ΤΑ ΤΜΗΜΑΤΑ",
                MARGIN + 14f,
                y + 18f,
                boxTitlePaint
            )
            canvas.save()
            canvas.translate(MARGIN + 14f, y + 26f)
            layout.draw(canvas)
            canvas.restore()

            y += boxHeight + 16f
        }

        // Two-Column Charts Section
        val chartHeight = 232f
        val leftWidth = contentWidth * 0.42f
        val rightWidth = contentWidth * 0.55f
        val rightLeft = MARGIN + contentWidth - rightWidth

        val leftRect = RectF(MARGIN, y, MARGIN + leftWidth, y + chartHeight)
        val rightRect = RectF(rightLeft, y, rightLeft + rightWidth, y + chartHeight)

        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(leftRect, 8f, 8f, fillPaint)
        canvas.drawRoundRect(leftRect, 8f, 8f, strokePaint)
        canvas.drawRoundRect(rightRect, 8f, 8f, fillPaint)
        canvas.drawRoundRect(rightRect, 8f, 8f, strokePaint)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 10.5f
            color = colorTextPrimary
        }
        canvas.drawText("Κατανομή Συναισθήματος", leftRect.left + 14f, leftRect.top + 22f, sectionTitlePaint)
        canvas.drawText("Triage Παραπόνων ανά Τμήμα", rightRect.left + 14f, rightRect.top + 22f, sectionTitlePaint)

        // Donut Chart
        val donutCenterX = leftRect.centerX()
        val donutCenterY = leftRect.top + 96f
        val donutRadius = 46f
        val arcRect = RectF(
            donutCenterX - donutRadius,
            donutCenterY - donutRadius,
            donutCenterX + donutRadius,
            donutCenterY + donutRadius
        )
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.BUTT
        }

        if (snapshot.totalReviews == 0) {
            arcPaint.color = colorCardBorder
            canvas.drawArc(arcRect, 0f, 360f, false, arcPaint)
        } else {
            var startAngle = -90f
            val totalF = snapshot.totalReviews.toFloat()
            val posSweep = (snapshot.positiveCount / totalF) * 360f
            val neuSweep = (snapshot.neutralCount / totalF) * 360f
            val negSweep = 360f - posSweep - neuSweep

            if (posSweep > 0f) {
                arcPaint.color = colorPositive
                canvas.drawArc(arcRect, startAngle, posSweep, false, arcPaint)
                startAngle += posSweep
            }
            if (neuSweep > 0f) {
                arcPaint.color = colorNeutral
                canvas.drawArc(arcRect, startAngle, neuSweep, false, arcPaint)
                startAngle += neuSweep
            }
            if (negSweep > 0f) {
                arcPaint.color = colorNegative
                canvas.drawArc(arcRect, startAngle, negSweep, false, arcPaint)
            }
        }

        val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 12f
            color = colorTextPrimary
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${snapshot.positivePercent}%", donutCenterX, donutCenterY + 4f, centerLabelPaint)

        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 9f
            color = colorTextPrimary
        }
        val legendItems = listOf(
            Triple("Θετικές", "${snapshot.positiveCount} (${snapshot.positivePercent}%)", colorPositive),
            Triple("Ουδέτερες", "${snapshot.neutralCount} (${snapshot.neutralPercent}%)", colorNeutral),
            Triple("Αρνητικές", "${snapshot.negativeCount} (${snapshot.negativePercent}%)", colorNegative)
        )
        legendItems.forEachIndexed { idx, (name, stat, col) ->
            val ly = leftRect.top + 168f + idx * 19f
            fillPaint.color = col
            canvas.drawCircle(leftRect.left + 20f, ly - 3f, 4.5f, fillPaint)
            canvas.drawText(name, leftRect.left + 30f, ly, legendPaint)
            legendPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(stat, leftRect.right - 14f, ly, legendPaint)
            legendPaint.textAlign = Paint.Align.LEFT
        }

        // Department Horizontal Bars
        val maxDeptCount = maxOf(1, snapshot.departmentStats.maxOfOrNull { it.totalMentions } ?: 1)
        val barLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mediumHeading
            textSize = 8.5f
            color = colorTextPrimary
        }
        val barSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 8f
            color = colorTextSecondary
        }

        snapshot.departmentStats.take(6).forEachIndexed { idx, deptStat ->
            val rowTop = rightRect.top + 42f + idx * 30f
            canvas.drawText(deptStat.department.shortLabelEl, rightRect.left + 14f, rowTop + 9f, barLabelPaint)

            val trackLeft = rightRect.left + 98f
            val trackRight = rightRect.right - 68f
            val trackWidth = trackRight - trackLeft

            fillPaint.color = Color.parseColor("#ECEFF1")
            canvas.drawRoundRect(RectF(trackLeft, rowTop, trackRight, rowTop + 11f), 4f, 4f, fillPaint)

            if (deptStat.totalMentions > 0) {
                val mentionW = (deptStat.totalMentions.toFloat() / maxDeptCount) * trackWidth
                fillPaint.color = colorAegean
                canvas.drawRoundRect(
                    RectF(trackLeft, rowTop, trackLeft + maxOf(6f, mentionW), rowTop + 11f),
                    4f,
                    4f,
                    fillPaint
                )
            }
            if (deptStat.complaintCount > 0) {
                val compW = (deptStat.complaintCount.toFloat() / maxDeptCount) * trackWidth
                fillPaint.color = colorNegative
                canvas.drawRoundRect(
                    RectF(trackLeft, rowTop, trackLeft + maxOf(6f, compW), rowTop + 11f),
                    4f,
                    4f,
                    fillPaint
                )
            }

            val countLabel = "${deptStat.complaintCount} παρ. / ${deptStat.totalMentions}"
            canvas.drawText(countLabel, trackRight + 6f, rowTop + 9f, barSmallPaint)
        }

        y += chartHeight + 18f

        // Resolution & Channel Summary Strip
        val stripRect = RectF(MARGIN, y, MARGIN + contentWidth, y + 54f)
        fillPaint.color = colorAlabaster
        canvas.drawRoundRect(stripRect, 8f, 8f, fillPaint)
        canvas.drawRoundRect(stripRect, 8f, 8f, strokePaint)

        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 9.5f
            color = colorTextPrimary
        }
        val stripSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 9f
            color = colorTextSecondary
        }
        val channelsSummary = snapshot.sourceCounts.entries
            .sortedByDescending { it.value }
            .joinToString("  •  ") { "${it.key.labelEl}: ${it.value}" }
            .ifBlank { "Δεν υπάρχουν καταχωρημένα κανάλια" }

        canvas.drawText(
            "Ρυθμός Ανταπόκρισης Ομάδας: ${snapshot.resolvedCount}/${snapshot.totalReviews} κριτικές τακτοποιήθηκαν (${snapshot.resolutionRatePercent}%)",
            stripRect.left + 14f,
            stripRect.top + 22f,
            stripPaint
        )
        canvas.drawText("Κατανομή Πηγών: $channelsSummary", stripRect.left + 14f, stripRect.top + 40f, stripSubPaint)
    }

    private fun drawReviewsPageHeader(
        canvas: Canvas,
        metadata: ReportMetadata,
        totalFiltered: Int,
        boldHeading: Typeface,
        regularBody: Typeface
    ): Float {
        canvas.drawColor(Color.WHITE)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 13f
            color = colorAegean
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 9f
            color = colorTextSecondary
        }

        canvas.drawText(
            "Αναλυτικός Πίνακας Κριτικών & Triage ($totalFiltered κριτικές)",
            MARGIN,
            40f,
            titlePaint
        )
        canvas.drawText(
            "${metadata.hotelName}  •  Περίοδος: ${metadata.periodLabel}",
            MARGIN,
            55f,
            subPaint
        )

        val headerTop = 68f
        val headerHeight = 24f
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        fillPaint.color = colorAegean
        canvas.drawRoundRect(
            RectF(MARGIN, headerTop, MARGIN + contentWidth, headerTop + headerHeight),
            4f,
            4f,
            fillPaint
        )

        val colHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 8.5f
            color = Color.WHITE
        }
        canvas.drawText("Ημ/νία & Πελάτης", MARGIN + 8f, headerTop + 15f, colHeaderPaint)
        canvas.drawText("Βαθμ.", MARGIN + contentWidth * 0.24f, headerTop + 15f, colHeaderPaint)
        canvas.drawText("Συναίσθημα & Τμήματα", MARGIN + contentWidth * 0.33f, headerTop + 15f, colHeaderPaint)
        canvas.drawText("Σύνοψη & Κριτική Πελάτη", MARGIN + contentWidth * 0.52f, headerTop + 15f, colHeaderPaint)

        return headerTop + headerHeight + 4f
    }

    private fun drawReviewRow(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        index: Int,
        review: HotelReview,
        dateStr: String,
        textLayout: StaticLayout,
        boldHeading: Typeface,
        regularBody: Typeface
    ) {
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        if (index % 2 == 1) {
            fillPaint.color = colorAlabaster
            canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + rowHeight, fillPaint)
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorCardBorder
            strokeWidth = 0.7f
        }
        canvas.drawLine(MARGIN, y + rowHeight, MARGIN + contentWidth, y + rowHeight, dividerPaint)

        val boldCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = boldHeading
            textSize = 8.5f
            color = colorTextPrimary
        }
        val smallCell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 7.8f
            color = colorTextSecondary
        }

        canvas.drawText(review.guestName.take(18), MARGIN + 6f, y + 15f, boldCell)
        canvas.drawText(dateStr, MARGIN + 6f, y + 27f, smallCell)
        canvas.drawText(review.sourceEnum.labelEl.take(16), MARGIN + 6f, y + 38f, smallCell)

        val ratingStr = String.format(Locale.US, "%.1f", review.rating)
        canvas.drawText("$ratingStr/10", MARGIN + contentWidth * 0.24f, y + 16f, boldCell)
        canvas.drawText(
            if (review.resolved) "Τακτοπ." else "Εκκρεμεί",
            MARGIN + contentWidth * 0.24f,
            y + 28f,
            smallCell
        )

        val sentColor = when (review.sentimentEnum) {
            SentimentType.POSITIVE -> colorPositive
            SentimentType.NEUTRAL -> colorNeutral
            SentimentType.NEGATIVE -> colorNegative
        }
        val sentPaint = Paint(boldCell).apply { color = sentColor }
        val prioSuffix = if (review.priorityEnum == TriagePriority.HIGH) " (Επείγον)" else ""
        canvas.drawText(
            "${review.sentimentEnum.labelEl}$prioSuffix",
            MARGIN + contentWidth * 0.33f,
            y + 15f,
            sentPaint
        )
        val deptLine1 = review.departmentEnums.take(2).joinToString(", ") { it.shortLabelEl }
        val deptLine2 = review.departmentEnums.drop(2).take(2).joinToString(", ") { it.shortLabelEl }
        if (deptLine1.isNotBlank()) {
            canvas.drawText(deptLine1.take(22), MARGIN + contentWidth * 0.33f, y + 27f, smallCell)
        }
        if (deptLine2.isNotBlank()) {
            canvas.drawText(deptLine2.take(22), MARGIN + contentWidth * 0.33f, y + 38f, smallCell)
        }

        canvas.save()
        canvas.translate(MARGIN + contentWidth * 0.52f, y + 7f)
        textLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        hotelName: String,
        regularBody: Typeface
    ) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularBody
            textSize = 8f
            color = colorTextSecondary
        }
        canvas.drawText(
            "$hotelName • Αναφορά Απόδοσης Hotel Pulse",
            MARGIN,
            PAGE_HEIGHT - 16f,
            footerPaint
        )
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "Σελίδα $pageNumber από $totalPages",
            PAGE_WIDTH - MARGIN,
            PAGE_HEIGHT - 16f,
            footerPaint
        )
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val safeWidth = width.coerceAtLeast(60)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1.0f)
            .setIncludePad(false)
            .build()
    }
}

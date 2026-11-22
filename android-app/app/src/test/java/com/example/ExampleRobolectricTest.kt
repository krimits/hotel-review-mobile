package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.export.ExcelReportGenerator
import com.example.export.PdfReportGenerator
import com.example.export.ReportMetadata
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.ReportFilterConfig
import com.example.model.ReportPeriod
import com.example.model.SeedReviewsProvider
import com.example.model.SentimentType
import com.example.viewmodel.HotelPulseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Hotel Pulse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hotel Pulse", appName)
    }

    @Test
    fun `pdf page renderer and multi-sheet xlsx report generator verify KPIs and Greek content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nowMillis = 1795000000000L
        val sampleReviews = SeedReviewsProvider.buildRealisticSeedReviews(
            userId = "test_mgr_1",
            nowMillis = nowMillis
        )
        val snapshot = HotelAnalyticsSnapshot.fromReviews(sampleReviews)
        assertEquals(6, snapshot.totalReviews)
        assertEquals(2, snapshot.positiveCount)
        assertEquals(2, snapshot.neutralCount)
        assertEquals(2, snapshot.negativeCount)

        val metadata = ReportMetadata(
            hotelName = "Aegean Horizon Boutique Hotel",
            hotelLocation = "Σαντορίνη, Ελλάδα",
            periodLabel = "Τελευταίες 30 ημέρες",
            executiveSummary = "Εξαιρετική βαθμολογία καθαριότητας με ανάγκη βελτίωσης του Wi-Fi."
        )

        // Verify multi-page PDF Canvas rendering (Page 1 KPIs + Charts, Page 2+ Reviews Table)
        val pageBitmaps = PdfReportGenerator.renderPreviewBitmapsOnly(
            context = context,
            metadata = metadata,
            snapshot = snapshot,
            reviews = sampleReviews,
            scale = 1
        )
        assertTrue("Expected at least 2 rendered report pages", pageBitmaps.size >= 2)
        assertEquals(PdfReportGenerator.PAGE_WIDTH, pageBitmaps.first().width)
        assertEquals(PdfReportGenerator.PAGE_HEIGHT, pageBitmaps.first().height)

        // Verify 3-sheet OOXML .xlsx generation & Greek UTF-8 content
        val tempDir = File(context.cacheDir, "test_reports").apply { mkdirs() }
        val xlsxFile = File(tempDir, "test_report.xlsx")
        val excelPreview = ExcelReportGenerator.generateExcelWorkbook(xlsxFile, metadata, snapshot, sampleReviews)
        assertTrue("XLSX workbook should exist and be non-empty", xlsxFile.exists() && xlsxFile.length() > 1000)
        assertEquals(3, excelPreview.sheets.size)

        ZipFile(xlsxFile).use { zip ->
            val workbookEntry = zip.getEntry("xl/workbook.xml")
            assertTrue(workbookEntry != null)
            val workbookXml = zip.getInputStream(workbookEntry!!).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(workbookXml.contains("Συνοπτικά KPIs"))
            assertTrue(workbookXml.contains("Τάσεις Τμημάτων"))
            assertTrue(workbookXml.contains("Αναλυτικές Κριτικές"))
        }

        // Verify report filtering by date range, sentiment, and department
        val filteredNegCleanliness = HotelPulseViewModel.filterReviewsForReport(
            reviews = sampleReviews,
            config = ReportFilterConfig(
                period = ReportPeriod.LAST_7_DAYS,
                selectedSentiments = setOf(SentimentType.NEGATIVE),
                selectedDepartments = setOf(HotelDepartment.CLEANLINESS)
            ),
            nowMillis = nowMillis
        )
        assertEquals(1, filteredNegCleanliness.size)
        assertEquals("Γιώργος Δημητρίου", filteredNegCleanliness.first().guestName)
    }
}

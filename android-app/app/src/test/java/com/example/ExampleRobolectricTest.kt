package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.OperationalStateStore
import com.example.domain.HostPulseEngine
import com.example.export.ExcelReportGenerator
import com.example.export.PdfReportGenerator
import com.example.export.ReportMetadata
import com.example.model.ActionTaskStatus
import com.example.model.AlertRulesConfig
import com.example.model.CriticalAlertKind
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.HotelProperty
import com.example.model.PlanLimits
import com.example.model.PriorityWeights
import com.example.model.ReportFilterConfig
import com.example.model.ReportPeriod
import com.example.model.ReviewSource
import com.example.model.ReviewTriageCategory
import com.example.model.SeedReviewsProvider
import com.example.model.SentimentType
import com.example.viewmodel.HotelPulseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `four category triage distinguishes pending resolved hypothetical and positive with hidden issue`() {
        // 1. Real pending issue -> generates pending issue
        val pending = HostPulseEngine.analyzeReviewText(
            "Το κλιματιστικό δεν λειτουργεί όλο το βράδυ και υπήρχε μούχλα στο μπάνιο."
        )
        assertEquals(ReviewTriageCategory.REAL_PENDING, pending.triageCategory)
        assertTrue("Expected pending issues for broken AC + mold", pending.pendingIssues.size >= 2)

        // 2. Already resolved incident -> ZERO pending issues
        val resolved = HostPulseEngine.analyzeReviewText(
            "Ο κλιματισμός ήταν χαλασμένος, αλλά διορθώθηκε την ίδια μέρα από το προσωπικό."
        )
        assertEquals(ReviewTriageCategory.REAL_RESOLVED, resolved.triageCategory)
        assertTrue("Resolved incident must not produce pending actions", resolved.pendingIssues.isEmpty())

        // 3. Hypothetical wish -> ZERO pending issues
        val hypothetical = HostPulseEngine.analyzeReviewText(
            "Πεντακάθαρο δωμάτιο και ευγενικό προσωπικό. Θα προτιμούσα μεγαλύτερο ψυγείο στο δωμάτιο."
        )
        assertEquals(ReviewTriageCategory.HYPOTHETICAL, hypothetical.triageCategory)
        assertTrue("Hypothetical wish must not produce pending actions", hypothetical.pendingIssues.isEmpty())

        // 4. Overall positive review containing a real pending Wi-Fi problem -> still generates pending action!
        val positiveWithHiddenIssue = HostPulseEngine.analyzeReviewText(
            "Amazing sunset views, spotless room and super friendly staff, however the Wi-Fi was not working in our room."
        )
        assertEquals(ReviewTriageCategory.REAL_PENDING, positiveWithHiddenIssue.triageCategory)
        assertEquals(1, positiveWithHiddenIssue.pendingIssues.size)

        // 5. Greek prefix false-positive guard («ακριβώς» != «ακριβό», «πάλι» != «παλιό», «κρυμμένος» != «κρύο», «αργότερα» != «αργό»)
        val greekFalsePositiveGuard = HostPulseEngine.analyzeReviewText(
            "Όπως ακριβώς στις φωτογραφίες! Υπέροχο δωμάτιο, κρυμμένος παράδεισος, θα έρθουμε πάλι αργότερα!"
        )
        assertEquals(ReviewTriageCategory.POSITIVE_COMMENT, greekFalsePositiveGuard.triageCategory)
        assertTrue(
            "Greek praise words («ακριβώς», «πάλι», «κρυμμένος», «αργότερα») must not trigger pending issues",
            greekFalsePositiveGuard.pendingIssues.isEmpty()
        )
    }

    @Test
    fun `action recommender greedy daily plan human confirmation gate and workflow transitions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OperationalStateStore(context, "test_actions_${System.nanoTime()}")
        store.clear()

        val vm = HotelPulseViewModel(
            repository = null,
            aiService = null,
            operationalStore = store,
            currentUserId = "demo",
            isDemoMode = true
        )

        // Verify seeded Demo tasks match their actual review properties & quotes
        val seededTasks = vm.operationalState.value.assignedTasks
        val taskDemo3 = seededTasks.first { it.reviewId == "demo_3" }
        val taskDemo6 = seededTasks.first { it.reviewId == "demo_6" }
        assertEquals("p1", taskDemo3.propertyId)
        assertEquals(HotelProperty.displayName("p1"), taskDemo3.propertyName)
        assertEquals("p2", taskDemo6.propertyId)
        assertEquals(HotelProperty.displayName("p2"), taskDemo6.propertyName)

        // Verify OperationalStateStore persistence round-trip
        val reloaded = store.load()
        assertEquals(vm.operationalState.value.assignedTasks.size, reloaded.assignedTasks.size)
        assertEquals("p1", reloaded.reviewPropertyMap["demo_3"])
        assertEquals("p2", reloaded.reviewPropertyMap["demo_6"])
        assertTrue("Expected seeded inter-department note on demo_3 task", taskDemo3.notes.isNotEmpty())

        // Verify adding an inter-department note persists across store reload
        val beforeNotes = taskDemo3.notes.size
        assertTrue(
            vm.addTaskNote(
                taskId = taskDemo3.id,
                fromDepartment = "Συντήρηση",
                toDepartment = "Υποδοχή",
                text = "Ολοκληρώθηκε η τοποθέτηση ανταλλακτικού, αναμένεται τελικός έλεγχος."
            )
        )
        val reloadedAfterNote = store.load()
        val updatedTaskDemo3 = reloadedAfterNote.assignedTasks.first { it.id == taskDemo3.id }
        assertEquals(beforeNotes + 1, updatedTaskDemo3.notes.size)
        assertEquals("Συντήρηση", updatedTaskDemo3.notes.last().fromDepartment)
        assertEquals("Υποδοχή", updatedTaskDemo3.notes.last().toDepartment)

        // Verify critical alerts (overdue tasks + low ratings + threshold adjustment + dismiss/restore)
        val alertNow = System.currentTimeMillis()
        val alerts48 = HostPulseEngine.buildOperationalAlerts(
            reviews = vm.operationalState.value.demoReviews.orEmpty(),
            assignedTasks = vm.operationalState.value.assignedTasks,
            reviewPropertyMap = vm.operationalState.value.reviewPropertyMap,
            selectedPropertyId = HotelProperty.ALL_ID,
            rules = AlertRulesConfig(lowRatingThreshold = 4.5, overdueHours = 48),
            dismissedAlertIds = emptySet(),
            nowMillis = alertNow
        )
        assertTrue(alerts48.any { it.kind == CriticalAlertKind.OVERDUE_TASK })
        assertTrue(alerts48.any { it.kind == CriticalAlertKind.LOW_RATING })

        val firstAlertId = alerts48.first().id
        vm.dismissAlert(firstAlertId)
        assertTrue(firstAlertId in store.load().dismissedAlertIds)
        vm.restoreDismissedAlerts()
        assertTrue(store.load().dismissedAlertIds.isEmpty())

        val nowMillis = System.currentTimeMillis()
        val bundles = SeedReviewsProvider.buildMultiPropertySeedBundles("demo", nowMillis)
        val reviews = bundles.map { it.review }
        val propMap = bundles.associate { it.review.id to it.propertyId }

        assertEquals(10, HotelProperty.CATALOG.size)
        assertTrue("Expected at least 20 multi-property demo reviews", reviews.size >= 20)

        val recs = HostPulseEngine.buildRecommendations(
            reviews = reviews,
            reviewPropertyMap = propMap,
            selectedPropertyId = HotelProperty.ALL_ID,
            assignedTasks = emptyList(),
            checkedReviewIds = emptySet(),
            weights = PriorityWeights(severityWeight = 4, recurrenceWeight = 3),
            limits = PlanLimits(maxMinutes = 90, maxBudgetEuros = 80),
            nowMillis = nowMillis
        )
        assertTrue("Expected prioritized recommendations from pending issues", recs.isNotEmpty())

        // Greedy daily plan fits <= 3 items within time & budget
        val plan = HostPulseEngine.buildDailyPlan(
            candidates = recs,
            limits = PlanLimits(maxMinutes = 90, maxBudgetEuros = 80),
            maxItems = 3
        )
        assertTrue(plan.selected.size in 1..3)
        assertTrue(plan.totalMinutes <= 90)
        assertTrue(plan.totalBudgetEuros <= 80)

        // Human confirmation gate strictly blocks unconfirmed approval
        val target = recs.first { it.reviewId !in vm.operationalState.value.checkedReviewIds }
        assertFalse(
            "Unconfirmed review action must NOT be approved before confirmation",
            vm.approveAndAssignAction(target)
        )

        // Confirm analysis -> Approve & Assign -> Start -> Complete
        vm.confirmReviewAnalysis(target.reviewId)
        assertTrue(vm.approveAndAssignAction(target))

        val assigned = vm.operationalState.value.assignedTasks.first { it.key == target.key }
        assertEquals(ActionTaskStatus.OPEN, assigned.status)

        vm.startTask(assigned.id)
        assertEquals(
            ActionTaskStatus.IN_PROGRESS,
            vm.operationalState.value.assignedTasks.first { it.id == assigned.id }.status
        )

        vm.completeTask(assigned.id)
        assertEquals(
            ActionTaskStatus.DONE,
            vm.operationalState.value.assignedTasks.first { it.id == assigned.id }.status
        )
    }

    @Test
    fun `repeating patterns and enriched pdf plus multi-sheet xlsx report verify Greek content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nowMillis = 1795000000000L
        val bundles = SeedReviewsProvider.buildMultiPropertySeedBundles(
            userId = "test_mgr_1",
            nowMillis = nowMillis
        )
        val sampleReviews = bundles.map { it.review }
        val propMap = bundles.associate { it.review.id to it.propertyId }
        val snapshot = HotelAnalyticsSnapshot.fromReviews(sampleReviews)

        val patterns = HostPulseEngine.buildTopicPatterns(
            reviews = sampleReviews,
            reviewPropertyMap = propMap,
            selectedPropertyId = HotelProperty.ALL_ID,
            windowDays = 30,
            sourceFilter = null,
            nowMillis = nowMillis
        )
        assertTrue("Expected repeating negative patterns over 30d window", patterns.isNotEmpty())

        val bookingPatterns = HostPulseEngine.buildTopicPatterns(
            reviews = sampleReviews,
            reviewPropertyMap = propMap,
            selectedPropertyId = HotelProperty.ALL_ID,
            windowDays = 30,
            sourceFilter = ReviewSource.BOOKING,
            nowMillis = nowMillis
        )
        assertTrue("Expected Booking.com filtered patterns", bookingPatterns.isNotEmpty())

        val recs = HostPulseEngine.buildRecommendations(
            reviews = sampleReviews,
            reviewPropertyMap = propMap,
            selectedPropertyId = HotelProperty.ALL_ID,
            assignedTasks = emptyList(),
            checkedReviewIds = setOf("demo_3"),
            weights = PriorityWeights(),
            limits = PlanLimits(),
            nowMillis = nowMillis
        )
        assertTrue(recs.isNotEmpty())

        val metadata = ReportMetadata(
            hotelName = "HostPulse Portfolio",
            hotelLocation = "Ελλάδα · 10 Καταλύματα",
            periodLabel = "Τελευταίες 30 ημέρες",
            executiveSummary = "Επαναλαμβανόμενα ζητήματα Wi-Fi και κλιματισμού σε πολλαπλά καταλύματα."
        )

        // Verify multi-page PDF Canvas rendering (Page 1 KPIs + Charts, Page 2 Patterns & Actions, Page 3+ Reviews)
        val pageBitmaps = PdfReportGenerator.renderPreviewBitmapsOnly(
            context = context,
            metadata = metadata,
            snapshot = snapshot,
            reviews = sampleReviews,
            patterns = patterns,
            recommendations = recs,
            reviewPropertyMap = propMap,
            scale = 1
        )
        assertTrue("Expected at least 3 rendered report pages (KPIs + Patterns/Actions + Reviews)", pageBitmaps.size >= 3)
        assertEquals(PdfReportGenerator.PAGE_WIDTH, pageBitmaps.first().width)
        assertEquals(PdfReportGenerator.PAGE_HEIGHT, pageBitmaps.first().height)

        // Verify 3-sheet OOXML .xlsx generation with «Μοτίβα & Ενέργειες» sheet
        val tempDir = File(context.cacheDir, "test_reports").apply { mkdirs() }
        val xlsxFile = File(tempDir, "test_report_v2.xlsx")
        val excelPreview = ExcelReportGenerator.generateExcelWorkbook(
            outputFile = xlsxFile,
            metadata = metadata,
            snapshot = snapshot,
            reviews = sampleReviews,
            patterns = patterns,
            recommendations = recs,
            reviewPropertyMap = propMap,
            checkedReviewIds = setOf("demo_3")
        )
        assertTrue("XLSX workbook should exist and be non-empty", xlsxFile.exists() && xlsxFile.length() > 1500)
        assertEquals(3, excelPreview.sheets.size)

        ZipFile(xlsxFile).use { zip ->
            val workbookEntry = zip.getEntry("xl/workbook.xml")
            assertTrue(workbookEntry != null)
            val workbookXml = zip.getInputStream(workbookEntry!!).bufferedReader(Charsets.UTF_8).readText()
            assertTrue(workbookXml.contains("Συνοπτικά KPIs"))
            assertTrue(workbookXml.contains("Μοτίβα &amp; Ενέργειες"))
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
        assertTrue("Expected negative cleanliness reviews within 7 days", filteredNegCleanliness.isNotEmpty())
        assertTrue(filteredNegCleanliness.all { it.sentimentEnum == SentimentType.NEGATIVE })
        assertTrue(filteredNegCleanliness.all { HotelDepartment.CLEANLINESS in it.departmentEnums })
    }
}

package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale
import kotlin.math.roundToInt

enum class ReviewSource(val labelEl: String) {
    BOOKING("Booking.com"),
    GOOGLE("Google Reviews"),
    TRIPADVISOR("TripAdvisor"),
    DIRECT("Εσωτερική φόρμα");

    companion object {
        fun fromRaw(raw: String?): ReviewSource =
            entries.firstOrNull { it.name == raw } ?: BOOKING
    }
}

enum class ReviewLanguage(val labelEl: String) {
    EL("Ελληνικά"),
    EN("Αγγλικά");

    companion object {
        fun fromRaw(raw: String?): ReviewLanguage =
            entries.firstOrNull { it.name == raw } ?: EL
    }
}

enum class SentimentType(val labelEl: String) {
    POSITIVE("Θετικό"),
    NEUTRAL("Ουδέτερο"),
    NEGATIVE("Αρνητικό");

    companion object {
        fun fromRaw(raw: String?): SentimentType =
            entries.firstOrNull { it.name == raw } ?: NEUTRAL
    }
}

enum class TriagePriority(val labelEl: String) {
    HIGH("Υψηλή"),
    MEDIUM("Μεσαία"),
    LOW("Χαμηλή");

    companion object {
        fun fromRaw(raw: String?): TriagePriority =
            entries.firstOrNull { it.name == raw } ?: LOW
    }
}

enum class HotelDepartment(val labelEl: String, val shortLabelEl: String) {
    CLEANLINESS("Καθαριότητα (Housekeeping)", "Καθαριότητα"),
    STAFF_SERVICE("Προσωπικό & Υποδοχή", "Υποδοχή"),
    BREAKFAST_DINING("Πρωινό & Εστίαση (F&B)", "Εστίαση"),
    ROOM_COMFORT("Δωμάτιο & Συντήρηση", "Συντήρηση"),
    FACILITIES_WIFI("Wi-Fi & Παροχές", "Παροχές"),
    VALUE_FOR_MONEY("Σχέση Ποιότητας - Τιμής", "Διεύθυνση");

    companion object {
        fun fromRaw(raw: String?): HotelDepartment? =
            entries.firstOrNull { it.name == raw }
    }
}

// ---------------------------------------------------------------------------
// Multi-Property Portfolio (Mock1 / HostPulse V2 — 10 Greek Properties)
// ---------------------------------------------------------------------------

data class HotelProperty(
    val id: String,
    val name: String,
    val location: String
) {
    val displayLabel: String get() = "$name · $location"

    companion object {
        const val ALL_ID = "all"

        val CATALOG: List<HotelProperty> = listOf(
            HotelProperty("p1", "Aegean Blue Suites", "Μύκονος"),
            HotelProperty("p2", "Sunlit Lofts Athens", "Αθήνα"),
            HotelProperty("p3", "Olive Grove Villas", "Λέσβος"),
            HotelProperty("p4", "Meteora View Hotel", "Καλαμπάκα"),
            HotelProperty("p5", "Portside Rooms", "Θεσσαλονίκη"),
            HotelProperty("p6", "Crete Palm Resort", "Χανιά"),
            HotelProperty("p7", "Ionian Breeze Apts", "Κέρκυρα"),
            HotelProperty("p8", "Acropolis Steps Rooms", "Αθήνα"),
            HotelProperty("p9", "Nest Studios Rhodes", "Ρόδος"),
            HotelProperty("p10", "Pine Ridge Chalet", "Αράχωβα")
        )

        fun byId(id: String?): HotelProperty? = CATALOG.firstOrNull { it.id == id }

        fun displayName(id: String?): String = byId(id)?.name ?: CATALOG.first().name
    }
}

// ---------------------------------------------------------------------------
// 4-Category Triage, Action Recommender & Repeating Patterns Domain Models
// ---------------------------------------------------------------------------

enum class ReviewTriageCategory(val labelEl: String, val shortLabelEl: String) {
    REAL_PENDING("Εκκρεμές πρόβλημα", "Εκκρεμές"),
    REAL_RESOLVED("Ήδη λυμένο περιστατικό", "Λυμένο"),
    HYPOTHETICAL("Επιθυμία / υπόθεση", "Επιθυμία"),
    POSITIVE_COMMENT("Θετικό σχόλιο", "Θετικό"),
    INSUFFICIENT("Χρειάζεται έλεγχος", "Έλεγχος");

    companion object {
        fun fromRaw(raw: String?): ReviewTriageCategory =
            entries.firstOrNull { it.name == raw } ?: POSITIVE_COMMENT
    }
}

enum class ActionCategory(
    val labelEl: String,
    val departmentNameEl: String,
    val hotelDepartment: HotelDepartment,
    val topicAspect: TopicAspect,
    val defaultActionTitle: String,
    val effortMinutes: Int,
    val costEuros: Int
) {
    CLEAN(
        labelEl = "Καθαριότητα",
        departmentNameEl = "Καθαριότητα",
        hotelDepartment = HotelDepartment.CLEANLINESS,
        topicAspect = TopicAspect.CLEANLINESS,
        defaultActionTitle = "Έλεγξε την καθαριότητα του χώρου και των λευκών ειδών",
        effortMinutes = 35,
        costEuros = 15
    ),
    MAINTENANCE(
        labelEl = "Συντήρηση",
        departmentNameEl = "Συντήρηση",
        hotelDepartment = HotelDepartment.FACILITIES_WIFI,
        topicAspect = TopicAspect.FACILITIES,
        defaultActionTitle = "Έλεγξε και αποκατάστησε τον εξοπλισμό που αναφέρεται",
        effortMinutes = 45,
        costEuros = 40
    ),
    STAFF(
        labelEl = "Εξυπηρέτηση",
        departmentNameEl = "Υποδοχή",
        hotelDepartment = HotelDepartment.STAFF_SERVICE,
        topicAspect = TopicAspect.STAFF,
        defaultActionTitle = "Επικοινώνησε με τον επισκέπτη και έλεγξε το περιστατικό εξυπηρέτησης",
        effortMinutes = 20,
        costEuros = 0
    ),
    FOOD(
        labelEl = "Πρωινό & φαγητό",
        departmentNameEl = "Εστίαση",
        hotelDepartment = HotelDepartment.BREAKFAST_DINING,
        topicAspect = TopicAspect.FOOD,
        defaultActionTitle = "Έλεγξε την ποιότητα και τη διαθεσιμότητα του πρωινού",
        effortMinutes = 25,
        costEuros = 20
    ),
    NOISE(
        labelEl = "Θόρυβος",
        departmentNameEl = "Υποδοχή",
        hotelDepartment = HotelDepartment.ROOM_COMFORT,
        topicAspect = TopicAspect.NOISE,
        defaultActionTitle = "Διερεύνησε την πηγή θορύβου και πρότεινε λύση στον επισκέπτη",
        effortMinutes = 20,
        costEuros = 0
    ),
    ROOM(
        labelEl = "Δωμάτιο",
        departmentNameEl = "Συντήρηση",
        hotelDepartment = HotelDepartment.ROOM_COMFORT,
        topicAspect = TopicAspect.ROOM,
        defaultActionTitle = "Επιθεώρησε τον χώρο και τον εξοπλισμό του δωματίου",
        effortMinutes = 30,
        costEuros = 20
    ),
    VALUE(
        labelEl = "Τιμή / αξία",
        departmentNameEl = "Διεύθυνση",
        hotelDepartment = HotelDepartment.VALUE_FOR_MONEY,
        topicAspect = TopicAspect.VALUE,
        defaultActionTitle = "Έλεγξε τη σχέση τιμής και παρεχόμενων υπηρεσιών",
        effortMinutes = 25,
        costEuros = 0
    ),
    LOCATION(
        labelEl = "Τοποθεσία",
        departmentNameEl = "Υποδοχή",
        hotelDepartment = HotelDepartment.STAFF_SERVICE,
        topicAspect = TopicAspect.LOCATION,
        defaultActionTitle = "Βελτίωσε τις οδηγίες πρόσβασης και την περιγραφή της τοποθεσίας",
        effortMinutes = 15,
        costEuros = 0
    ),
    AMENITIES(
        labelEl = "Παροχές",
        departmentNameEl = "Συντήρηση",
        hotelDepartment = HotelDepartment.FACILITIES_WIFI,
        topicAspect = TopicAspect.FACILITIES,
        defaultActionTitle = "Έλεγξε τη διαθεσιμότητα και την κατάσταση των παροχών",
        effortMinutes = 30,
        costEuros = 25
    );

    companion object {
        fun fromRaw(raw: String?): ActionCategory? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class TopicAspect(val labelEl: String) {
    CLEANLINESS("Καθαριότητα"),
    FACILITIES("Παροχές & συντήρηση"),
    STAFF("Εξυπηρέτηση"),
    FOOD("Πρωινό & φαγητό"),
    NOISE("Θόρυβος"),
    ROOM("Δωμάτιο"),
    VALUE("Τιμή / αξία"),
    LOCATION("Τοποθεσία");

    companion object {
        fun fromRaw(raw: String?): TopicAspect? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class AspectSentimentKind(val labelEl: String) {
    POSITIVE("Θετικό"),
    NEGATIVE("Αρνητικό"),
    MIXED("Μικτό"),
    UNKNOWN("Χωρίς ένδειξη")
}

data class ReviewAspectInsight(
    val category: ActionCategory,
    val sentiment: AspectSentimentKind,
    val quotes: List<String>
)

data class ReviewIssue(
    val category: ActionCategory,
    val quote: String,
    val resolved: Boolean,
    val hypothetical: Boolean,
    val severity: Int // 3 = urgent, 2 = normal
) {
    val isPending: Boolean get() = !resolved && !hypothetical
}

data class ReviewAnalysisResult(
    val triageCategory: ReviewTriageCategory,
    val overallSentiment: SentimentType,
    val aspects: List<ReviewAspectInsight>,
    val issues: List<ReviewIssue>,
    val pendingIssues: List<ReviewIssue>,
    val signalCount: Int
)

enum class ActionTaskStatus(val labelEl: String) {
    OPEN("Ανατέθηκε"),
    IN_PROGRESS("Σε εξέλιξη"),
    DONE("Ολοκληρώθηκε");

    fun canTransitionTo(target: ActionTaskStatus): Boolean = when (this) {
        OPEN -> target == IN_PROGRESS || target == DONE
        IN_PROGRESS -> target == DONE
        DONE -> false
    }

    companion object {
        fun fromRaw(raw: String?): ActionTaskStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class ReviewWorkflowStatus(val labelEl: String) {
    AWAITING("Προς αξιολόγηση"),
    ASSIGNED("Ανατέθηκε"),
    IN_PROGRESS("Σε εξέλιξη"),
    DONE("Ολοκληρώθηκε"),
    NO_ACTION("Χωρίς ενέργεια")
}

data class PriorityWeights(
    val severityWeight: Int = 4, // 1..5
    val recurrenceWeight: Int = 3 // 1..5
)

data class PlanLimits(
    val maxMinutes: Int = 120,
    val maxBudgetEuros: Int = 150
)

data class RecommendedAction(
    val key: String, // "$reviewId:${category.name}"
    val reviewId: String,
    val propertyId: String,
    val propertyName: String,
    val guestName: String,
    val category: ActionCategory,
    val departmentNameEl: String,
    val title: String,
    val quote: String,
    val severity: Int, // 3 = urgent, 2 = normal
    val effortMinutes: Int,
    val costEuros: Int,
    val recurrence30d: Int,
    val stayDateMillis: Long,
    val deadlineLabel: String,
    val priorityScore: Double,
    val isAnalysisConfirmed: Boolean,
    val fitsSingleLimits: Boolean
)

data class DailyActionPlan(
    val selected: List<RecommendedAction>,
    val totalMinutes: Int,
    val totalBudgetEuros: Int
)

data class TaskDepartmentNote(
    val id: String,
    val fromDepartment: String,
    val toDepartment: String,
    val text: String,
    val createdAtMillis: Long
) {
    companion object {
        const val ALL_DEPARTMENTS = "Όλα τα τμήματα"
        val DEPARTMENT_OPTIONS: List<String> = listOf(
            "Καθαριότητα",
            "Συντήρηση",
            "Υποδοχή",
            "Εστίαση",
            "Διεύθυνση"
        )
    }
}

data class AlertRulesConfig(
    val lowRatingThreshold: Double = 4.5, // 1.0..10.0 scale
    val overdueHours: Int = 48            // SLA hours: 24, 48, 72
)

enum class CriticalAlertKind(val labelEl: String) {
    OVERDUE_TASK("Εκπρόθεσμη εργασία"),
    LOW_RATING("Χαμηλή βαθμολογία"),
    URGENT_UNASSIGNED("Επείγον χωρίς ανάθεση")
}

data class OperationalAlert(
    val id: String,
    val kind: CriticalAlertKind,
    val severity: Int, // 3 = critical, 2 = warning
    val propertyId: String,
    val propertyName: String,
    val departmentNameEl: String?,
    val reviewId: String?,
    val taskId: String?,
    val title: String,
    val detail: String,
    val timestampMillis: Long,
    val hoursPending: Int? = null
)

data class AssignedActionTask(
    val id: String,
    val key: String,
    val reviewId: String,
    val propertyId: String,
    val propertyName: String,
    val category: ActionCategory,
    val departmentNameEl: String,
    val title: String,
    val quote: String,
    val severity: Int,
    val effortMinutes: Int,
    val costEuros: Int,
    val deadlineLabel: String,
    val status: ActionTaskStatus,
    val createdAtMillis: Long,
    val notes: List<TaskDepartmentNote> = emptyList()
)

data class PatternEvidenceItem(
    val reviewId: String,
    val propertyId: String,
    val propertyName: String,
    val guestName: String,
    val source: ReviewSource,
    val stayDateMillis: Long,
    val quote: String,
    val resolvedIncident: Boolean
)

data class TopicPatternSignal(
    val aspect: TopicAspect,
    val totalMentions: Int,
    val negativeCount: Int,
    val negativeRatePercent: Int,
    val previousMentions: Int,
    val rateChangePoints: Double?, // percentage points vs previous equal window
    val propertyCount: Int,
    val reviewIds: List<String>,
    val evidence: List<PatternEvidenceItem>
)

// ---------------------------------------------------------------------------
// Firestore-Backed Core Models (1:1 Compatible with firestore.rules)
// ---------------------------------------------------------------------------

@Keep
@IgnoreExtraProperties
data class HotelProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val hotelName: String = "HostPulse Portfolio",
    val hotelLocation: String = "Ελλάδα · 10 Καταλύματα",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class HotelReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val guestName: String = "",
    val source: String = ReviewSource.BOOKING.name,
    val language: String = ReviewLanguage.EL.name,
    val rating: Double = 8.0,
    val stayDateMillis: Long = 0L,
    val reviewText: String = "",
    val sentiment: String = SentimentType.NEUTRAL.name,
    val sentimentScore: Long = 50L,
    val priority: String = TriagePriority.LOW.name,
    val departments: List<String> = emptyList(),
    val keyHighlights: List<String> = emptyList(),
    val summaryLine: String = "",
    val suggestedReply: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val sourceEnum: ReviewSource get() = ReviewSource.fromRaw(source)
    val languageEnum: ReviewLanguage get() = ReviewLanguage.fromRaw(language)
    val sentimentEnum: SentimentType get() = SentimentType.fromRaw(sentiment)
    val priorityEnum: TriagePriority get() = TriagePriority.fromRaw(priority)
    val departmentEnums: List<HotelDepartment>
        get() = departments.mapNotNull { HotelDepartment.fromRaw(it) }
}

enum class ReportPeriod(val labelEl: String, val daysBack: Int?) {
    LAST_7_DAYS("Τελευταίες 7 ημέρες", 7),
    LAST_14_DAYS("Τελευταίες 14 ημέρες", 14),
    LAST_30_DAYS("Τελευταίες 30 ημέρες", 30),
    LAST_60_DAYS("Τελευταίες 60 ημέρες", 60),
    LAST_90_DAYS("Τελευταίες 90 ημέρες", 90),
    ALL_TIME("Όλες οι περίοδοι", null),
    CUSTOM("Επιλεγμένο διάστημα", null)
}

data class ReportFilterConfig(
    val period: ReportPeriod = ReportPeriod.ALL_TIME,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val selectedSentiments: Set<SentimentType> = SentimentType.entries.toSet(),
    val selectedDepartments: Set<HotelDepartment> = emptySet(),
    val onlyUnresolved: Boolean = false,
    val includeAiExecutiveSummary: Boolean = true,
    val includePatternsAndActions: Boolean = true
)

data class DepartmentStat(
    val department: HotelDepartment,
    val totalMentions: Int,
    val complaintCount: Int,
    val urgentCount: Int,
    val averageRating: Double,
    val shareOfReviewsPercent: Int
)

data class HotelAnalyticsSnapshot(
    val totalReviews: Int,
    val averageRating: Double,
    val positiveCount: Int,
    val neutralCount: Int,
    val negativeCount: Int,
    val positivePercent: Int,
    val neutralPercent: Int,
    val negativePercent: Int,
    val urgentUnresolvedCount: Int,
    val resolvedCount: Int,
    val resolutionRatePercent: Int,
    val departmentStats: List<DepartmentStat>,
    val sourceCounts: Map<ReviewSource, Int>
) {
    val formattedAverageRating: String
        get() = if (totalReviews == 0) "0.0" else String.format(Locale.US, "%.1f", averageRating)

    companion object {
        fun fromReviews(reviews: List<HotelReview>): HotelAnalyticsSnapshot {
            if (reviews.isEmpty()) {
                return HotelAnalyticsSnapshot(
                    totalReviews = 0,
                    averageRating = 0.0,
                    positiveCount = 0,
                    neutralCount = 0,
                    negativeCount = 0,
                    positivePercent = 0,
                    neutralPercent = 0,
                    negativePercent = 0,
                    urgentUnresolvedCount = 0,
                    resolvedCount = 0,
                    resolutionRatePercent = 0,
                    departmentStats = HotelDepartment.entries.map {
                        DepartmentStat(it, 0, 0, 0, 0.0, 0)
                    },
                    sourceCounts = emptyMap()
                )
            }

            val total = reviews.size
            val avg = reviews.sumOf { it.rating } / total.toDouble()
            val pos = reviews.count { it.sentimentEnum == SentimentType.POSITIVE }
            val neu = reviews.count { it.sentimentEnum == SentimentType.NEUTRAL }
            val neg = reviews.count { it.sentimentEnum == SentimentType.NEGATIVE }

            val posPct = ((pos * 100.0) / total).roundToInt()
            val negPct = ((neg * 100.0) / total).roundToInt()
            val neuPct = (100 - posPct - negPct).coerceAtLeast(0)

            val urgentOpen = reviews.count {
                !it.resolved && (it.priorityEnum == TriagePriority.HIGH || it.sentimentEnum == SentimentType.NEGATIVE)
            }
            val resolved = reviews.count { it.resolved }
            val resolvedPct = ((resolved * 100.0) / total).roundToInt()

            val deptStats = HotelDepartment.entries.map { dept ->
                val matching = reviews.filter { dept.name in it.departments }
                val complaints = matching.count {
                    it.sentimentEnum == SentimentType.NEGATIVE ||
                        (it.sentimentEnum == SentimentType.NEUTRAL && it.rating < 7.5)
                }
                val urgent = matching.count { it.priorityEnum == TriagePriority.HIGH && !it.resolved }
                val deptAvg = if (matching.isEmpty()) 0.0 else matching.sumOf { it.rating } / matching.size
                val share = ((matching.size * 100.0) / total).roundToInt()
                DepartmentStat(
                    department = dept,
                    totalMentions = matching.size,
                    complaintCount = complaints,
                    urgentCount = urgent,
                    averageRating = deptAvg,
                    shareOfReviewsPercent = share
                )
            }.sortedWith(
                compareByDescending<DepartmentStat> { it.complaintCount }
                    .thenByDescending { it.totalMentions }
            )

            val sources = reviews.groupingBy { it.sourceEnum }.eachCount()

            return HotelAnalyticsSnapshot(
                totalReviews = total,
                averageRating = avg,
                positiveCount = pos,
                neutralCount = neu,
                negativeCount = neg,
                positivePercent = posPct,
                neutralPercent = neuPct,
                negativePercent = negPct,
                urgentUnresolvedCount = urgentOpen,
                resolvedCount = resolved,
                resolutionRatePercent = resolvedPct,
                departmentStats = deptStats,
                sourceCounts = sources
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Multi-Property Seed Provider (10 Greek Properties + 4-Category Triage Cases)
// ---------------------------------------------------------------------------

data class SeedReviewBundle(
    val review: HotelReview,
    val propertyId: String
)

object SeedReviewsProvider {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun buildMultiPropertySeedBundles(userId: String, nowMillis: Long): List<SeedReviewBundle> = listOf(
        // p1 — Aegean Blue Suites (Μύκονος)
        SeedReviewBundle(
            propertyId = "p1",
            review = HotelReview(
                id = "demo_1",
                userId = userId,
                guestName = "Ελένη Μ.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 9.6,
                stayDateMillis = nowMillis - 1 * DAY_MS,
                reviewText = "Φανταστικές διακοπές! Το δωμάτιο ήταν πεντακάθαρο, η θέα μαγευτική και το προσωπικό εξυπηρετικότατο. Το πρωινό πλούσιο με τοπικά προϊόντα.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 96L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.CLEANLINESS.name, HotelDepartment.STAFF_SERVICE.name, HotelDepartment.BREAKFAST_DINING.name),
                keyHighlights = listOf("Πεντακάθαρο δωμάτιο", "Μαγευτική θέα", "Πλούσιο τοπικό πρωινό"),
                summaryLine = "Θετικό σχόλιο χωρίς εκκρεμή προβλήματα.",
                suggestedReply = "Αγαπητή κα Ελένη, σας ευχαριστούμε θερμά για τα υπέροχα λόγια σας! Σας περιμένουμε ξανά στη Μύκονο.",
                resolved = true
            )
        ),
        SeedReviewBundle(
            propertyId = "p1",
            review = HotelReview(
                id = "demo_2",
                userId = userId,
                guestName = "Άννα Δ.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 8.2,
                stayDateMillis = nowMillis - 1 * DAY_MS,
                reviewText = "Ο κλιματισμός ήταν χαλασμένος, αλλά διορθώθηκε την ίδια μέρα. Το προσωπικό ήταν ευγενικό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 76L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Βλάβη A/C που λύθηκε αυθημερόν", "Ευγενικό προσωπικό"),
                summaryLine = "Ήδη λυμένο περιστατικό κλιματισμού — δεν απαιτείται νέα εργασία.",
                suggestedReply = "Αγαπητή κα Άννα, χαιρόμαστε που το ζήτημα με τον κλιματισμό αντιμετωπίστηκε άμεσα. Σας ευχαριστούμε για την κατανόηση!",
                resolved = true
            )
        ),
        SeedReviewBundle(
            propertyId = "p1",
            review = HotelReview(
                id = "demo_3",
                userId = userId,
                guestName = "Μαρία Π.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 4.2,
                stayDateMillis = nowMillis - 5 * DAY_MS,
                reviewText = "Το δωμάτιο θορυβώδες και η τιμή ακριβή για αυτά που προσφέρει. Ο κλιματισμός ήταν χαλασμένος.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 22L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name, HotelDepartment.ROOM_COMFORT.name, HotelDepartment.VALUE_FOR_MONEY.name),
                keyHighlights = listOf("Χαλασμένος κλιματισμός", "Θορυβώδες δωμάτιο", "Ακριβή τιμή"),
                summaryLine = "Εκκρεμές επείγον πρόβλημα κλιματισμού και θορύβου.",
                suggestedReply = "Αγαπητή κα Μαρία, λυπούμαστε ειλικρινά για τη βλάβη του κλιματισμού και τον θόρυβο. Η τεχνική ομάδα μας ελέγχει άμεσα το δωμάτιο.",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p1",
            review = HotelReview(
                id = "demo_4",
                userId = userId,
                guestName = "Δημήτρης Κ.",
                source = ReviewSource.DIRECT.name,
                language = ReviewLanguage.EL.name,
                rating = 8.0,
                stayDateMillis = nowMillis - 10 * DAY_MS,
                reviewText = "Ωραία διαμονή. Η πισίνα υπέροχη και το δωμάτιο άνετο. Το check-in είχε καθυστέρηση, αλλά συνολικά πολύ καλό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 78L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.STAFF_SERVICE.name, HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Θετική διαμονή με καθυστέρηση στο check-in"),
                summaryLine = "Θετική συνολική κριτική με εκκρεμές σχόλιο για καθυστέρηση check-in.",
                suggestedReply = "",
                resolved = false
            )
        ),

        // p2 — Sunlit Lofts Athens (Αθήνα)
        SeedReviewBundle(
            propertyId = "p2",
            review = HotelReview(
                id = "demo_5",
                userId = userId,
                guestName = "Νεφέλη Κ.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 8.4,
                stayDateMillis = nowMillis - 1 * DAY_MS,
                reviewText = "Θα προτιμούσα μεγαλύτερο ψυγείο. Κατά τα άλλα η διαμονή ήταν υπέροχη.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 84L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Επιθυμία για μεγαλύτερο ψυγείο", "Υπέροχη διαμονή"),
                summaryLine = "Επιθυμία/υπόθεση επισκέπτη — δεν δημιουργείται αυτόματη διορθωτική εργασία.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p2",
            review = HotelReview(
                id = "demo_6",
                userId = userId,
                guestName = "Νίκος Σ.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 2.4,
                stayDateMillis = nowMillis - 6 * DAY_MS,
                reviewText = "Απαράδεκτη καθαριότητα. Βρώμικες πετσέτες και μούχλα στο μπάνιο. Το προσωπικό δεν απαντούσε στα τηλεφωνήματα.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 12L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.CLEANLINESS.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Βρώμικες πετσέτες", "Μούχλα στο μπάνιο", "Αναπάντητα τηλέφωνα"),
                summaryLine = "Επείγον πρόβλημα καθαριότητας και ανταπόκρισης υποδοχής.",
                suggestedReply = "Αγαπητέ κ. Νίκο, ζητούμε ειλικρινή συγγνώμη. Έχουμε ήδη προχωρήσει σε βαθύ καθαρισμό και έλεγχο της βάρδιας υποδοχής.",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p2",
            review = HotelReview(
                id = "demo_7",
                userId = userId,
                guestName = "Κώστας Α.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 9.4,
                stayDateMillis = nowMillis - 2 * DAY_MS,
                reviewText = "Όπως στις φωτογραφίες! Προσεγμένος χώρος, βολική τοποθεσία δίπλα στο μετρό και φιλικότατο προσωπικό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 94L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.STAFF_SERVICE.name, HotelDepartment.ROOM_COMFORT.name),
                keyHighlights = listOf("Δίπλα στο μετρό", "Φιλικό προσωπικό"),
                summaryLine = "Εξαιρετική κριτική χωρίς προβλήματα.",
                suggestedReply = "",
                resolved = true
            )
        ),

        // p3 — Olive Grove Villas (Λέσβος)
        SeedReviewBundle(
            propertyId = "p3",
            review = HotelReview(
                id = "demo_8",
                userId = userId,
                guestName = "Πέτρος Δ.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 8.0,
                stayDateMillis = nowMillis - 8 * DAY_MS,
                reviewText = "Πολύ όμορφη βίλα με πλήρως εξοπλισμένη κουζίνα. Ωραία θέα στους ελαιώνες. Μόνο το wifi ήταν αδύναμο.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 76L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Όμορφη βίλα", "Αδύναμο Wi-Fi"),
                summaryLine = "Θετική κριτική με εκκρεμές ζήτημα αδύναμου Wi-Fi.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p3",
            review = HotelReview(
                id = "demo_9",
                userId = userId,
                guestName = "Αναστασία Β.",
                source = ReviewSource.DIRECT.name,
                language = ReviewLanguage.EL.name,
                rating = 9.8,
                stayDateMillis = nowMillis - 3 * DAY_MS,
                reviewText = "Υπέροχη βίλα! Πισίνα τέλεια, ησυχία και φανταστική θέα στο Αιγαίο. Οι ιδιοκτήτες φιλόξενοι και μας βοήθησαν σε όλα.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 98L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Τέλεια πισίνα", "Φιλόξενοι ιδιοκτήτες"),
                summaryLine = "Άριστη εμπειρία φιλοξενίας στη Λέσβο.",
                suggestedReply = "",
                resolved = true
            )
        ),

        // p4 — Meteora View Hotel (Καλαμπάκα)
        SeedReviewBundle(
            propertyId = "p4",
            review = HotelReview(
                id = "demo_10",
                userId = userId,
                guestName = "Χάρης Ν.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 4.4,
                stayDateMillis = nowMillis - 9 * DAY_MS,
                reviewText = "Το πρωινό λιγοστό και το δωμάτιο άβολο. Αργό check-in με καθυστέρηση μία ώρα.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 24L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.BREAKFAST_DINING.name, HotelDepartment.ROOM_COMFORT.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Λιγοστό πρωινό", "Άβολο δωμάτιο", "Καθυστέρηση check-in 1 ώρα"),
                summaryLine = "Εκκρεμή παράπονα σε πρωινό, δωμάτιο και χρόνο check-in.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p4",
            review = HotelReview(
                id = "demo_11",
                userId = userId,
                guestName = "Τάσης Ε.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 9.5,
                stayDateMillis = nowMillis - 4 * DAY_MS,
                reviewText = "Απίστευτη θέα στα Μετέωρα από το μπαλκόνι. Ευρύχωρο δωμάτιο, εξαιρετικό πρωινό και ευγενικό προσωπικό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 95L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.ROOM_COMFORT.name, HotelDepartment.BREAKFAST_DINING.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Θέα στα Μετέωρα", "Εξαιρετικό πρωινό"),
                summaryLine = "Άριστη βαθμολογία για θέα και φιλοξενία.",
                suggestedReply = "",
                resolved = true
            )
        ),

        // p5 — Portside Rooms (Θεσσαλονίκη)
        SeedReviewBundle(
            propertyId = "p5",
            review = HotelReview(
                id = "demo_12",
                userId = userId,
                guestName = "Ειρήνη Τ.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 2.6,
                stayDateMillis = nowMillis - 12 * DAY_MS,
                reviewText = "Αγενές προσωπικό, ακατάστατο δωμάτιο και πανάκριβο για ό,τι προσφέρει. Απογοήτευση.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 16L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.STAFF_SERVICE.name, HotelDepartment.CLEANLINESS.name, HotelDepartment.VALUE_FOR_MONEY.name),
                keyHighlights = listOf("Αγενές προσωπικό", "Ακατάστατο δωμάτιο", "Υψηλή τιμή"),
                summaryLine = "Σοβαρό παράπονο για συμπεριφορά προσωπικού και καθαριότητα.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p5",
            review = HotelReview(
                id = "demo_13",
                userId = userId,
                guestName = "Αντώνης Η.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 6.2,
                stayDateMillis = nowMillis - 16 * DAY_MS,
                reviewText = "Λογική τιμή για το κέντρο, αλλά ο δρόμος θορυβώδης τα βράδια. Το δωμάτιο καθαρό.",
                sentiment = SentimentType.NEUTRAL.name,
                sentimentScore = 58L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.ROOM_COMFORT.name, HotelDepartment.CLEANLINESS.name),
                keyHighlights = listOf("Θορυβώδης δρόμος τη νύχτα", "Καθαρό δωμάτιο"),
                summaryLine = "Εκκρεμές σχόλιο για εξωτερικό θόρυβο τα βράδια.",
                suggestedReply = "",
                resolved = false
            )
        ),

        // p6 — Crete Palm Resort (Χανιά)
        SeedReviewBundle(
            propertyId = "p6",
            review = HotelReview(
                id = "demo_14",
                userId = userId,
                guestName = "Στέλιος Υ.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 4.6,
                stayDateMillis = nowMillis - 14 * DAY_MS,
                reviewText = "Το φαγητό μέτριο και το μπαρ του ξενοδοχείου πανάκριβο. Η πισίνα θολή ένα απόγευμα.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 30L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.BREAKFAST_DINING.name, HotelDepartment.VALUE_FOR_MONEY.name, HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Μέτριο φαγητό", "Ακριβό μπαρ", "Θολή πισίνα"),
                summaryLine = "Παράπονα για εστίαση, τιμές μπαρ και καθαρότητα πισίνας.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p6",
            review = HotelReview(
                id = "demo_15",
                userId = userId,
                guestName = "Κατερίνα Ρ.",
                source = ReviewSource.DIRECT.name,
                language = ReviewLanguage.EL.name,
                rating = 8.2,
                stayDateMillis = nowMillis - 19 * DAY_MS,
                reviewText = "Υπέροχες διακοπές, αν και το μπαλκόνι μας ήταν μικρό. Πρώτη γραμμή παραλία και φανταστικό πρωινό. Το check-out ήταν αργό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 79L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.STAFF_SERVICE.name, HotelDepartment.BREAKFAST_DINING.name),
                keyHighlights = listOf("Φανταστικό πρωινό", "Αργό check-out"),
                summaryLine = "Θετική διαμονή με εκκρεμή παρατήρηση για αργό check-out.",
                suggestedReply = "",
                resolved = false
            )
        ),

        // p7 — Ionian Breeze Apts (Κέρκυρα)
        SeedReviewBundle(
            propertyId = "p7",
            review = HotelReview(
                id = "demo_16",
                userId = userId,
                guestName = "Λάμπρος Χ.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 8.2,
                stayDateMillis = nowMillis - 15 * DAY_MS,
                reviewText = "Καλή σχέση τιμής-ποιότητας και ήσυχη γειτονιά. Θα προτιμούσα μεγαλύτερο ψυγείο, αλλά όχι κάτι σοβαρό.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 82L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.VALUE_FOR_MONEY.name, HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Καλή σχέση τιμής-ποιότητας", "Επιθυμία για μεγαλύτερο ψυγείο"),
                summaryLine = "Επιθυμία/υπόθεση επισκέπτη χωρίς εκκρεμές πρόβλημα.",
                suggestedReply = "",
                resolved = true
            )
        ),

        // p8 — Acropolis Steps Rooms (Αθήνα) — Repeating WiFi / A/C / Cleanliness patterns
        SeedReviewBundle(
            propertyId = "p8",
            review = HotelReview(
                id = "demo_17",
                userId = userId,
                guestName = "Πέτρος Λ.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 7.4,
                stayDateMillis = nowMillis - 1 * DAY_MS,
                reviewText = "Το δωμάτιο ήταν πεντακάθαρο, αλλά το WiFi δεν λειτουργεί.",
                sentiment = SentimentType.NEUTRAL.name,
                sentimentScore = 54L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.CLEANLINESS.name, HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Πεντακάθαρο δωμάτιο", "Μη λειτουργικό Wi-Fi"),
                summaryLine = "Θετική καθαριότητα αλλά εκκρεμές πρόβλημα Wi-Fi.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p8",
            review = HotelReview(
                id = "demo_18",
                userId = userId,
                guestName = "Τούλα Κ.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 6.0,
                stayDateMillis = nowMillis - 7 * DAY_MS,
                reviewText = "Καλή τοποθεσία για εξερεύνηση της πόλης, αλλά το δωμάτιο μύριζε κλεισούρα. Το κλιματιστικό θορυβώδες. Η τοποθεσία όμως αποζημιώνει.",
                sentiment = SentimentType.NEUTRAL.name,
                sentimentScore = 50L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.FACILITIES_WIFI.name, HotelDepartment.ROOM_COMFORT.name),
                keyHighlights = listOf("Θορυβώδες κλιματιστικό", "Εξαιρετική τοποθεσία"),
                summaryLine = "Εκκρεμές τεχνικό πρόβλημα κλιματιστικού στο κέντρο Αθήνας.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p8",
            review = HotelReview(
                id = "demo_19",
                userId = userId,
                guestName = "Νένα Στ.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 4.5,
                stayDateMillis = nowMillis - 18 * DAY_MS,
                reviewText = "Το πρωινό επαναλαμβανόμενο κάθε μέρα και ο καθαρισμός στα κοινόχρηστα αργεί. Ακριβό για την Αθήνα.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 28L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.BREAKFAST_DINING.name, HotelDepartment.CLEANLINESS.name, HotelDepartment.VALUE_FOR_MONEY.name),
                keyHighlights = listOf("Επαναλαμβανόμενο πρωινό", "Καθυστέρηση καθαρισμού κοινόχρηστων"),
                summaryLine = "Παρατηρήσεις για πρωινό και καθαρισμό κοινόχρηστων χώρων.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p8",
            review = HotelReview(
                id = "demo_20",
                userId = userId,
                guestName = "James Thornton",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EN.name,
                rating = 5.4,
                stayDateMillis = nowMillis - 3 * DAY_MS,
                reviewText = "The room was clean and central, but the WiFi is not working and the air conditioning was broken all night.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 32L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.CLEANLINESS.name, HotelDepartment.FACILITIES_WIFI.name),
                keyHighlights = listOf("Wi-Fi εκτός λειτουργίας", "Χαλασμένος κλιματισμός"),
                summaryLine = "Επείγον επαναλαμβανόμενο πρόβλημα Wi-Fi και A/C (Αγγλική κριτική).",
                suggestedReply = "Dear Mr. Thornton, we sincerely apologize for the Wi-Fi outage and the air conditioning fault. Our technician is inspecting the unit today.",
                resolved = false
            )
        ),

        // p9 — Nest Studios Rhodes (Ρόδος)
        SeedReviewBundle(
            propertyId = "p9",
            review = HotelReview(
                id = "demo_21",
                userId = userId,
                guestName = "Μάριος Β.",
                source = ReviewSource.GOOGLE.name,
                language = ReviewLanguage.EL.name,
                rating = 4.0,
                stayDateMillis = nowMillis - 11 * DAY_MS,
                reviewText = "Σοβαρή καθυστέρηση στο check-in και το δωμάτιο δεν ήταν έτοιμο. Κρύο νερό στο ντους και ο κλιματισμός αδύναμος.",
                sentiment = SentimentType.NEGATIVE.name,
                sentimentScore = 20L,
                priority = TriagePriority.HIGH.name,
                departments = listOf(HotelDepartment.STAFF_SERVICE.name, HotelDepartment.FACILITIES_WIFI.name, HotelDepartment.ROOM_COMFORT.name),
                keyHighlights = listOf("Καθυστέρηση check-in", "Κρύο νερό στο ντους", "Αδύναμος κλιματισμός"),
                summaryLine = "Επείγοντα προβλήματα ζεστού νερού, κλιματισμού και check-in.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p9",
            review = HotelReview(
                id = "demo_22",
                userId = userId,
                guestName = "Διονύσης Α.",
                source = ReviewSource.BOOKING.name,
                language = ReviewLanguage.EL.name,
                rating = 9.6,
                stayDateMillis = nowMillis - 5 * DAY_MS,
                reviewText = "Πανέμορφα νεόδμητα studios, πεντακάθαρα με υπέροχη θέα. Οι οικοδεσπότες εξυπηρετικότατοι σε κάθε λεπτομέρεια.",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 96L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.CLEANLINESS.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Νεόδμητα studios", "Πεντακάθαρα"),
                summaryLine = "Εξαιρετική εμπειρία φιλοξενίας στη Ρόδο.",
                suggestedReply = "",
                resolved = true
            )
        ),

        // p10 — Pine Ridge Chalet (Αράχωβα)
        SeedReviewBundle(
            propertyId = "p10",
            review = HotelReview(
                id = "demo_23",
                userId = userId,
                guestName = "Ζαχαρίας Λ.",
                source = ReviewSource.DIRECT.name,
                language = ReviewLanguage.EL.name,
                rating = 6.4,
                stayDateMillis = nowMillis - 24 * DAY_MS,
                reviewText = "Όμορφο χειμερινό καταφύγιο με τζάκι, αλλά το δωμάτιο ψυχρό το πρωί και το πρωινό βασικό.",
                sentiment = SentimentType.NEUTRAL.name,
                sentimentScore = 54L,
                priority = TriagePriority.MEDIUM.name,
                departments = listOf(HotelDepartment.ROOM_COMFORT.name, HotelDepartment.BREAKFAST_DINING.name),
                keyHighlights = listOf("Ψυχρό δωμάτιο το πρωί", "Βασικό πρωινό"),
                summaryLine = "Εκκρεμή σχόλια για θέρμανση δωματίου και ποικιλία πρωινού.",
                suggestedReply = "",
                resolved = false
            )
        ),
        SeedReviewBundle(
            propertyId = "p10",
            review = HotelReview(
                id = "demo_24",
                userId = userId,
                guestName = "Ζέττα Γ.",
                source = ReviewSource.TRIPADVISOR.name,
                language = ReviewLanguage.EL.name,
                rating = 9.8,
                stayDateMillis = nowMillis - 4 * DAY_MS,
                reviewText = "Τζάκι, θέα στο βουνό, ζεστή φιλοξενία. Το πρωινό με τοπικά προϊόντα υπέροχο. Θα επιστρέψουμε σίγουρα!",
                sentiment = SentimentType.POSITIVE.name,
                sentimentScore = 98L,
                priority = TriagePriority.LOW.name,
                departments = listOf(HotelDepartment.BREAKFAST_DINING.name, HotelDepartment.STAFF_SERVICE.name),
                keyHighlights = listOf("Ζεστή φιλοξενία", "Υπέροχο τοπικό πρωινό"),
                summaryLine = "Ενθουσιώδης θετική κριτική στην Αράχωβα.",
                suggestedReply = "",
                resolved = true
            )
        )
    )

    fun buildRealisticSeedReviews(userId: String, nowMillis: Long): List<HotelReview> =
        buildMultiPropertySeedBundles(userId, nowMillis).map { it.review }
}

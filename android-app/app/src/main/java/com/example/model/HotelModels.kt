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
    DIRECT("Απευθείας");

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
    STAFF_SERVICE("Προσωπικό & Υποδοχή", "Προσωπικό"),
    BREAKFAST_DINING("Πρωινό & Εστίαση (F&B)", "Εστίαση"),
    ROOM_COMFORT("Δωμάτιο & Άνεση Ύπνου", "Δωμάτιο"),
    FACILITIES_WIFI("Wi-Fi & Εγκαταστάσεις", "Wi-Fi & Χώροι"),
    VALUE_FOR_MONEY("Σχέση Ποιότητας - Τιμής", "Αξία/Τιμή");

    companion object {
        fun fromRaw(raw: String?): HotelDepartment? =
            entries.firstOrNull { it.name == raw }
    }
}

@Keep
@IgnoreExtraProperties
data class HotelProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val hotelName: String = "Aegean Horizon Boutique Hotel",
    val hotelLocation: String = "Αθήνα & Κυκλάδες, Ελλάδα",
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
    LAST_30_DAYS("Τελευταίες 30 ημέρες", 30),
    LAST_90_DAYS("Τελευταίες 90 ημέρες", 90),
    ALL_TIME("Όλες οι περίοδοι", null),
    CUSTOM("Επιλεγμένο διάστημα", null)
}

data class ReportFilterConfig(
    val period: ReportPeriod = ReportPeriod.ALL_TIME,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val selectedSentiments: Set<SentimentType> = SentimentType.entries.toSet(),
    val selectedDepartments: Set<HotelDepartment> = emptySet(), // empty = all departments
    val onlyUnresolved: Boolean = false,
    val includeAiExecutiveSummary: Boolean = true
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

object SeedReviewsProvider {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun buildRealisticSeedReviews(userId: String, nowMillis: Long): List<HotelReview> = listOf(
        HotelReview(
            userId = userId,
            guestName = "Μαρία Κωνσταντίνου",
            source = ReviewSource.BOOKING.name,
            language = ReviewLanguage.EL.name,
            rating = 9.6,
            stayDateMillis = nowMillis - 2 * DAY_MS,
            reviewText = "Μοναδική φιλοξενία! Τα δωμάτια ήταν πεντακάθαρα, η θέα στη θάλασσα μαγευτική και ο μπουφές του πρωινού είχε εξαιρετικά τοπικά προϊόντα.",
            sentiment = SentimentType.POSITIVE.name,
            sentimentScore = 96L,
            priority = TriagePriority.LOW.name,
            departments = listOf(
                HotelDepartment.CLEANLINESS.name,
                HotelDepartment.BREAKFAST_DINING.name,
                HotelDepartment.STAFF_SERVICE.name
            ),
            keyHighlights = listOf("Πεντακάθαρα δωμάτια", "Πλούσιο τοπικό πρωινό", "Ζεστή φιλοξενία"),
            summaryLine = "Ενθουσιώδης εμπειρία με έπαινο για καθαριότητα, πρωινό και προσωπικό.",
            suggestedReply = "Αγαπητή κα Κωνσταντίνου, σας ευχαριστούμε θερμά για τα υπέροχα λόγια σας! Χαιρόμαστε ιδιαίτερα που απολαύσατε την καθαριότητα και τις τοπικές γεύσεις του πρωινού μας. Σας περιμένουμε ξανά σύντομα!",
            resolved = true
        ),
        HotelReview(
            userId = userId,
            guestName = "James Thornton",
            source = ReviewSource.TRIPADVISOR.name,
            language = ReviewLanguage.EN.name,
            rating = 5.2,
            stayDateMillis = nowMillis - 4 * DAY_MS,
            reviewText = "Great location, however the air conditioning in room 304 rattled loudly all night and the Wi-Fi dropped constantly during work calls. Front desk promised a technician who never arrived.",
            sentiment = SentimentType.NEGATIVE.name,
            sentimentScore = 22L,
            priority = TriagePriority.HIGH.name,
            departments = listOf(
                HotelDepartment.ROOM_COMFORT.name,
                HotelDepartment.FACILITIES_WIFI.name,
                HotelDepartment.STAFF_SERVICE.name
            ),
            keyHighlights = listOf("Θόρυβος κλιματιστικού (Δωμ. 304)", "Ασταθές Wi-Fi", "Καθυστέρηση τεχνικού"),
            summaryLine = "Σοβαρό παράπονο για θόρυβο A/C, ασταθές Wi-Fi και μη ανταπόκριση συντήρησης.",
            suggestedReply = "Dear Mr. Thornton, please accept our sincere apologies for the air conditioning noise in room 304, the Wi-Fi instability, and our follow-up delay. Our engineering team has already serviced the unit and upgraded the floor access point. We would love the chance to restore your trust.",
            resolved = false
        ),
        HotelReview(
            userId = userId,
            guestName = "Γιώργος Δημητρίου",
            source = ReviewSource.GOOGLE.name,
            language = ReviewLanguage.EL.name,
            rating = 4.8,
            stayDateMillis = nowMillis - 6 * DAY_MS,
            reviewText = "Κατά την άφιξη περιμέναμε 45 λεπτά για το check-in ενώ το δωμάτιο δεν είχε σκουπιστεί σωστά στο μπαλκόνι. Για την τιμή που πληρώσαμε περιμέναμε πολύ καλύτερη οργάνωση.",
            sentiment = SentimentType.NEGATIVE.name,
            sentimentScore = 26L,
            priority = TriagePriority.HIGH.name,
            departments = listOf(
                HotelDepartment.STAFF_SERVICE.name,
                HotelDepartment.CLEANLINESS.name,
                HotelDepartment.VALUE_FOR_MONEY.name
            ),
            keyHighlights = listOf("45 λεπτά αναμονή στο check-in", "Ελλιπής καθαρισμός μπαλκονιού", "Παράπονο σχέσης τιμής-ποιότητας"),
            summaryLine = "Καθυστέρηση στην υποδοχή και παραλείψεις καθαριότητας σε σχέση με το κόστος.",
            suggestedReply = "Αγαπητέ κ. Δημητρίου, λυπούμαστε ειλικρινά για την αναμονή κατά την άφιξή σας και την παράλειψη στον εξωτερικό χώρο του δωματίου. Έχουμε ήδη ενισχύσει τη βάρδια υποδοχής και τους ελέγχους του Housekeeping ώστε να ανταποκρινόμαστε στα υψηλά πρότυπά μας.",
            resolved = false
        ),
        HotelReview(
            userId = userId,
            guestName = "Sophie Laurent",
            source = ReviewSource.BOOKING.name,
            language = ReviewLanguage.EN.name,
            rating = 7.6,
            stayDateMillis = nowMillis - 11 * DAY_MS,
            reviewText = "Lovely terrace and super polite housekeeping team. Breakfast coffee machine had a 15-minute queue around 9am and the Wi-Fi by the pool was weak.",
            sentiment = SentimentType.NEUTRAL.name,
            sentimentScore = 64L,
            priority = TriagePriority.MEDIUM.name,
            departments = listOf(
                HotelDepartment.CLEANLINESS.name,
                HotelDepartment.BREAKFAST_DINING.name,
                HotelDepartment.FACILITIES_WIFI.name
            ),
            keyHighlights = listOf("Ευγενικό Housekeeping", "Αναμονή στον καφέ πρωινού", "Αδύναμο Wi-Fi στην πισίνα"),
            summaryLine = "Θετική συνολική εντύπωση με σημεία βελτίωσης στη ροή πρωινού και το Wi-Fi πισίνας.",
            suggestedReply = "Dear Sophie, thank you for sharing your thoughtful feedback and kind words about our housekeeping team! We are adding a second espresso station during peak morning hours and boosting outdoor Wi-Fi coverage by the pool.",
            resolved = false
        ),
        HotelReview(
            userId = userId,
            guestName = "Αλέξανδρος Νικολάου",
            source = ReviewSource.DIRECT.name,
            language = ReviewLanguage.EL.name,
            rating = 9.2,
            stayDateMillis = nowMillis - 18 * DAY_MS,
            reviewText = "Άψογο στρώμα ύπνου, απόλυτη ησυχία το βράδυ και εξαιρετική εξυπηρέτηση στη ρεσεψιόν που μας κανόνισε late check-out χωρίς χρέωση.",
            sentiment = SentimentType.POSITIVE.name,
            sentimentScore = 93L,
            priority = TriagePriority.LOW.name,
            departments = listOf(
                HotelDepartment.ROOM_COMFORT.name,
                HotelDepartment.STAFF_SERVICE.name,
                HotelDepartment.VALUE_FOR_MONEY.name
            ),
            keyHighlights = listOf("Άριστη ποιότητα ύπνου", "Εξυπηρετική υποδοχή", "Δωρεάν late check-out"),
            summaryLine = "Άριστη άνεση δωματίου και ευελιξία εξυπηρέτησης από την υποδοχή.",
            suggestedReply = "Αγαπητέ κ. Νικολάου, σας ευχαριστούμε πολύ για την εξαιρετική αξιολόγηση! Είναι χαρά μας που ξεκουραστήκατε πλήρως και που η ομάδα της υποδοχής διευκόλυνε την αναχώρησή σας.",
            resolved = true
        ),
        HotelReview(
            userId = userId,
            guestName = "Κατερίνα Βλάχου",
            source = ReviewSource.GOOGLE.name,
            language = ReviewLanguage.EL.name,
            rating = 6.2,
            stayDateMillis = nowMillis - 25 * DAY_MS,
            reviewText = "Το δωμάτιο ήταν όμορφο αλλά στο πρωινό τελείωναν γρήγορα τα ζεστά εδέσματα μετά τις 09:30 και η ηχομόνωση προς τον διάδρομο χρειάζεται βελτίωση.",
            sentiment = SentimentType.NEUTRAL.name,
            sentimentScore = 48L,
            priority = TriagePriority.MEDIUM.name,
            departments = listOf(
                HotelDepartment.BREAKFAST_DINING.name,
                HotelDepartment.ROOM_COMFORT.name
            ),
            keyHighlights = listOf("Έλλειψη ζεστών πιάτων μετά τις 09:30", "Μέτρια ηχομόνωση διαδρόμου"),
            summaryLine = "Παρατηρήσεις για αναπλήρωση μπουφέ πρωινού και ηχομόνωση δωματίων.",
            suggestedReply = "Αγαπητή κα Βλάχου, σας ευχαριστούμε για τις εύστοχες παρατηρήσεις σας. Έχουμε ήδη προσαρμόσει το πρόγραμμα αναπλήρωσης του μπουφέ μέχρι το τέλος του ωραρίου πρωινού και ελέγχουμε τα λάστιχα ηχομόνωσης στις πόρτες του ορόφου.",
            resolved = false
        )
    )
}

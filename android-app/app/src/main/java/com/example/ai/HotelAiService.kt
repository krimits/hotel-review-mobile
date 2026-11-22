package com.example.ai

import android.util.Log
import com.example.model.HotelAnalyticsSnapshot
import com.example.model.HotelDepartment
import com.example.model.HotelReview
import com.example.model.ReviewLanguage
import com.example.model.SentimentType
import com.example.model.TriagePriority
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val AI_TAG = "HotelAiService"

data class AiTriageResult(
    val language: ReviewLanguage,
    val sentiment: SentimentType,
    val sentimentScore: Long,
    val priority: TriagePriority,
    val departments: List<HotelDepartment>,
    val keyHighlights: List<String>,
    val summaryLine: String,
    val suggestedReply: String
)

class HotelAiService {

    private val triageSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "language" to Schema.enumeration(
                    values = listOf("EL", "EN"),
                    description = "Primary language of the review: EL for Greek, EN for English or other."
                ),
                "sentiment" to Schema.enumeration(
                    values = listOf("POSITIVE", "NEUTRAL", "NEGATIVE"),
                    description = "Overall sentiment classification of the hotel review."
                ),
                "sentimentScore" to Schema.integer(
                    description = "Sentiment positivity score between 0 (very negative) and 100 (very positive)."
                ),
                "priority" to Schema.enumeration(
                    values = listOf("HIGH", "MEDIUM", "LOW"),
                    description = "Operational urgency: HIGH for unresolved complaints/low ratings, MEDIUM for mixed feedback, LOW for praise."
                ),
                "departments" to Schema.array(
                    items = Schema.enumeration(
                        values = listOf(
                            "CLEANLINESS",
                            "STAFF_SERVICE",
                            "BREAKFAST_DINING",
                            "ROOM_COMFORT",
                            "FACILITIES_WIFI",
                            "VALUE_FOR_MONEY"
                        )
                    ),
                    description = "Hotel departments referenced in the review (up to 6)."
                ),
                "keyHighlights" to Schema.array(
                    items = Schema.string(description = "Concise point in Greek (max 6 words)"),
                    description = "2 to 4 concise bullet takeaways in Greek for the hotel manager."
                ),
                "summaryLine" to Schema.string(
                    description = "One-sentence executive summary in Greek describing the guest's experience and any issue."
                ),
                "suggestedReply" to Schema.string(
                    description = "Warm, professional hotel management reply written in the SAME language as the guest's review (Greek if EL, English if EN)."
                )
            )
        )
    }

    private val triageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = triageSchema
            }
        )
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun analyzeReview(
        guestName: String,
        rating: Double,
        reviewText: String,
        hotelName: String
    ): Result<AiTriageResult> {
        return try {
            val prompt = """
                Είσαι εξειδικευμένος αναλυτής ποιότητας ξενοδοχείου για το "$hotelName".
                Ανάλυσε την παρακάτω κριτική πελάτη:
                - Όνομα πελάτη: $guestName
                - Βαθμολογία: $rating / 10
                - Κείμενο κριτικής: "$reviewText"

                Οδηγίες:
                1. Εντόπισε τη γλώσσα (EL ή EN), το συναίσθημα (POSITIVE, NEUTRAL, NEGATIVE), το σκορ (0-100) και την προτεραιότητα (HIGH, MEDIUM, LOW).
                2. Αντιστοίχισε τα σχετικά τμήματα (CLEANLINESS, STAFF_SERVICE, BREAKFAST_DINING, ROOM_COMFORT, FACILITIES_WIFI, VALUE_FOR_MONEY).
                3. Γράψε τα keyHighlights και το summaryLine στα Ελληνικά για τον υπεύθυνο του ξενοδοχείου.
                4. Γράψε το suggestedReply στη γλώσσα που έγραψε ο πελάτης (Ελληνικά αν η κριτική είναι στα ελληνικά, Αγγλικά αν είναι στα αγγλικά).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                triageModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Το μοντέλο AI επέστρεψε κενή απάντηση."))

            val json = JSONObject(rawJson)
            val lang = ReviewLanguage.fromRaw(json.optString("language", "EL"))
            val sentiment = SentimentType.fromRaw(json.optString("sentiment", "NEUTRAL"))
            val score = json.optLong("sentimentScore", 50L).coerceIn(0L, 100L)
            val priority = TriagePriority.fromRaw(json.optString("priority", "MEDIUM"))

            val deptsArray = json.optJSONArray("departments")
            val depts = buildList {
                if (deptsArray != null) {
                    for (i in 0 until deptsArray.length()) {
                        HotelDepartment.fromRaw(deptsArray.optString(i))?.let { add(it) }
                    }
                }
            }.distinct().take(6)

            val highlightsArray = json.optJSONArray("keyHighlights")
            val highlights = buildList {
                if (highlightsArray != null) {
                    for (i in 0 until highlightsArray.length()) {
                        val item = highlightsArray.optString(i).trim()
                        if (item.isNotEmpty()) add(item.take(160))
                    }
                }
            }.take(8)

            val summaryLine = json.optString("summaryLine", "").trim().take(500)
            val suggestedReply = json.optString("suggestedReply", "").trim().take(3000)

            Result.success(
                AiTriageResult(
                    language = lang,
                    sentiment = sentiment,
                    sentimentScore = score,
                    priority = priority,
                    departments = depts,
                    keyHighlights = highlights,
                    summaryLine = summaryLine,
                    suggestedReply = suggestedReply
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "AI review triage timed out", e)
            Result.failure(IllegalStateException("Η ανάλυση AI ξεπέρασε το χρονικό όριο (60s). Δοκιμάστε ξανά."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "AI review triage failed", e)
            Result.failure(e)
        }
    }

    suspend fun regenerateGuestReply(
        review: HotelReview,
        hotelName: String,
        formalTone: Boolean
    ): Result<String> {
        return try {
            val toneDesc = if (formalTone) {
                "αυστηρά επίσημο, θεσμικό και διευθυντικό ύφος"
            } else {
                "ζεστό, φιλόξενο και ανθρώπινο boutique ύφος"
            }
            val targetLang = if (review.languageEnum == ReviewLanguage.EN) "English" else "Greek (Ελληνικά)"
            val prompt = """
                Γράψε μια απάντηση εκ μέρους της διεύθυνσης του ξενοδοχείου "$hotelName" προς τον/την πελάτη ${review.guestName}
                σε $toneDesc, αποκλειστικά στη γλώσσα: $targetLang.
                Βαθμολογία πελάτη: ${review.rating}/10.
                Κριτική: "${review.reviewText}"
                Επίστρεψε ΜΟΝΟ το τελικό κείμενο της απάντησης (4-6 προτάσεις), χωρίς εισαγωγικά ή σχόλια.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                textModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text.take(3000))
            } else {
                Result.failure(IllegalStateException("Κενή απάντηση από το μοντέλο AI."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Reply regeneration timed out", e)
            Result.failure(IllegalStateException("Η δημιουργία απάντησης ξεπέρασε το χρονικό όριο."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Reply regeneration failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateExecutiveSummaryForReport(
        hotelName: String,
        periodLabel: String,
        snapshot: HotelAnalyticsSnapshot,
        reviews: List<HotelReview>
    ): Result<String> {
        if (reviews.isEmpty()) {
            return Result.success("Δεν υπάρχουν κριτικές για την επιλεγμένη περίοδο.")
        }
        return try {
            val topComplaints = snapshot.departmentStats
                .filter { it.complaintCount > 0 }
                .take(3)
                .joinToString(", ") { "${it.department.shortLabelEl} (${it.complaintCount} παράπονα)" }
                .ifBlank { "Κανένα επαναλαμβανόμενο παράπονο" }

            val sampleNotes = reviews.take(8).joinToString("\n") {
                "- [${it.sentimentEnum.labelEl} | ${it.rating}/10] ${it.summaryLine.ifBlank { it.reviewText.take(120) }}"
            }

            val prompt = """
                Σύνταξε μια περιεκτική Επιτελική Σύνοψη Απόδοσης (4-5 προτάσεις στα Ελληνικά, χωρίς markdown σύμβολα * ή #)
                για τη διοίκηση και την ομάδα του ξενοδοχείου "$hotelName" (Περίοδος: $periodLabel).
                Στοιχεία περιόδου:
                - Σύνολο κριτικών: ${snapshot.totalReviews}, Μέση βαθμολογία: ${snapshot.formattedAverageRating}/10
                - Θετικές: ${snapshot.positivePercent}%, Ουδέτερες: ${snapshot.neutralPercent}%, Αρνητικές: ${snapshot.negativePercent}%
                - Εκκρεμή επείγοντα ζητήματα: ${snapshot.urgentUnresolvedCount}
                - Τμήματα με παράπονα: $topComplaints
                Ενδεικτικές κριτικές:
                $sampleNotes

                Ανάδειξε τα δυνατά σημεία της ομάδας και δώσε 2 συγκεκριμένες προτεραιότητες δράσης για τα τμήματα.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                textModel.generateContent(prompt)
            }
            val summary = response.text?.replace("*", "")?.replace("#", "")?.trim()
            if (!summary.isNullOrBlank()) {
                Result.success(summary.take(900))
            } else {
                Result.failure(IllegalStateException("Το AI επέστρεψε κενή σύνοψη."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Executive summary timed out", e)
            Result.failure(IllegalStateException("Η δημιουργία επιτελικής σύνοψης ξεπέρασε το χρονικό όριο."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Executive summary failed", e)
            Result.failure(e)
        }
    }
}

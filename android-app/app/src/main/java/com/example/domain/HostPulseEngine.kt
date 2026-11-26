package com.example.domain

import com.example.model.ActionCategory
import com.example.model.ActionTaskStatus
import com.example.model.AlertRulesConfig
import com.example.model.AspectSentimentKind
import com.example.model.AssignedActionTask
import com.example.model.CriticalAlertKind
import com.example.model.DailyActionPlan
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.OperationalAlert
import com.example.model.PatternEvidenceItem
import com.example.model.PlanLimits
import com.example.model.PriorityWeights
import com.example.model.RecommendedAction
import com.example.model.ReviewAnalysisResult
import com.example.model.ReviewAspectInsight
import com.example.model.ReviewIssue
import com.example.model.ReviewLanguage
import com.example.model.ReviewSource
import com.example.model.ReviewTriageCategory
import com.example.model.ReviewWorkflowStatus
import com.example.model.SentimentType
import com.example.model.TopicAspect
import com.example.model.TopicPatternSignal
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic Greek + English hotel review analysis, 4-category triage,
 * Action Recommender (Severity x 30-day Recurrence + Recency), greedy daily plan
 * (up to 3 actions fitting time & budget), and multi-window repeating pattern detector.
 * Faithful Kotlin port of the HostPulse Mock1/V2 specification.
 */
object HostPulseEngine {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val HOUR_MS = 60L * 60L * 1000L

    private val DIACRITICS_REGEX = Regex("[\\u0300-\\u036f]")
    private val CONTRACTION_REGEX = Regex(
        "\\b(?:wasn't|isn't|aren't|weren't|doesn't|didn't|don't|can't|won't|couldn't|wouldn't|shouldn't|haven't|hasn't|hadn't)\\b"
    )
    private val TOKEN_REGEX = Regex("[a-zα-ω]+")
    private val SENTENCE_REGEX = Regex("[^.!?;\\n]+[.!?;]?")
    private val CLAUSE_SPLIT_REGEX = Regex(",|\\s+(?:αλλά|αλλα|όμως|ομως|και|but|however|and)\\s+", RegexOption.IGNORE_CASE)

    // Guard against Greek prefix false positives (e.g. «πάλι» != «παλιό», «ακριβώς» != «ακριβό»)
    private val EXACT_LEXICON_EXCLUSIONS = setOf("παλι", "ακριβωσ")
    private val PREFIX_LEXICON_EXCLUSIONS = listOf("αργοτερ", "αργι", "αργολιδ", "κρυμμεν", "κρυφ")

    private fun isExcludedFromStem(word: String, stem: String): Boolean {
        if (word in EXACT_LEXICON_EXCLUSIONS && (stem == "παλι" || stem == "ακριβ")) return true
        if ((stem == "αργ" || stem == "κρυ") && PREFIX_LEXICON_EXCLUSIONS.any { word.startsWith(it) }) return true
        return false
    }

    private val HYPOTHETICAL_REGEX = Regex(
        "(?:θα (?:ηθελα|προτιμουσα|μπορουσε)|would (?:like|prefer)|if .* (?:break|fail)|αν (?:χαλασει|σπασει)|υποθετικ)"
    )
    private val RESOLVED_REGEX = Regex(
        "(?:διορθωθηκε|επισκευαστηκε|αποκατασταθηκε|λυθηκε|το εφτιαξαν|resolved|was fixed|was repaired)"
    )
    private val NOT_RESOLVED_REGEX = Regex(
        "(?:δεν|not|χωρισ).{0,18}(?:διορθω|επισκευ|λυθη|fixed|resolved)"
    )
    private val BACKREF_ISSUE_REGEX = Regex("(?:το προβλημα|το θεμα|the issue|the problem)")
    private val NON_FUNCTIONAL_REGEX = Regex(
        "(?:δεν λειτουργ|δεν δουλευ|not working|does not work|no hot water|χωρισ ζεστο νερο|κρυο νερο)"
    )
    private val URGENT_REGEX = Regex(
        "(?:μουχλ|μοχλ|filthy|mold|ζεστο νερο|κρυο νερο|hot water|κλιματισ|air condition|aircon|διαρρο|leak|wifi|wi-fi|δεν λειτουργ|not working)"
    )

    private val CATEGORY_KEYWORDS: Map<ActionCategory, List<String>> = mapOf(
        ActionCategory.CLEAN to listOf(
            "πεντακαθ", "καθαρ", "βρωμ", "μουχλ", "μοχλ", "πετσετ", "σεντον", "σκον", "ακαταστατ",
            "clean", "dirty", "filthy", "mold", "towel"
        ),
        ActionCategory.MAINTENANCE to listOf(
            "κλιματισ", "χαλασ", "σπασ", "ψυγει", "θερμαν", "διαρρο", "ζεστο νερο", "κρυο νερο",
            "wifi", "wi-fi", "air condition", "aircon", "broken", "hot water", "heating", "leak", "not working"
        ),
        ActionCategory.STAFF to listOf(
            "προσωπικ", "ρεσεψιον", "υποδοχ", "αγεν", "ευγεν", "φιλικ", "φιλοξεν", "οικοδεσποτ",
            "ιδιοκτητ", "check-in", "check in", "check-out", "check out", "τηλεφων",
            "staff", "reception", "host", "rude", "service"
        ),
        ActionCategory.FOOD to listOf(
            "πρωιν", "φαγητ", "μπουφε", "μπαρ", "εστιατ", "breakfast", "food", "buffet", "restaurant"
        ),
        ActionCategory.NOISE to listOf(
            "θορυβ", "ησυχ", "ηχο", "φασαρι", "noise", "noisy", "quiet", "loud"
        ),
        ActionCategory.ROOM to listOf(
            "δωματι", "κρεβατ", "μπανι", "ντουσ", "χωρο", "στρωμα", "ανετ", "μπαλκον", "κλεισουρ",
            "room", "bed", "bathroom", "shower", "mattress"
        ),
        ActionCategory.VALUE to listOf(
            "τιμ", "κοστ", "ακριβ", "πανακριβ", "οικονομικ", "αξια", "αξιζ",
            "price", "expensive", "value", "overpriced"
        ),
        ActionCategory.LOCATION to listOf(
            "τοποθεσ", "κεντρ", "παραλι", "μετρο", "σταθμ", "μακρια", "γειτον",
            "location", "beach", "station", "far away"
        ),
        ActionCategory.AMENITIES to listOf(
            "πισιν", "παρκιν", "parking", "κουζιν", "σαουνα", "γυμναστ", "pool", "kitchen", "facilit"
        )
    )

    private val POSITIVE_LEXICON: List<Pair<String, Double>> = listOf(
        "πεντακαθ" to 3.0, "φανταστικ" to 3.0, "υπεροχ" to 3.0, "τελει" to 3.0,
        "εξαιρετικ" to 3.0, "μαγευτικ" to 3.0, "αψογ" to 3.0, "καταπληκτ" to 3.0,
        "πανεμορφ" to 3.0, "απιστευτ" to 2.5, "καθαρ" to 2.0, "ανετ" to 2.0,
        "ευγεν" to 2.0, "εξυπηρετικ" to 2.0, "φιλικ" to 2.0, "φιλοξεν" to 2.0,
        "ομορφ" to 2.0, "ωραι" to 2.0, "βολικ" to 2.0, "ευρυχωρ" to 2.0,
        "προσεγμεν" to 2.0, "ησυχ" to 2.0, "πλουσι" to 1.5, "νοστιμ" to 1.5,
        "φρεσκ" to 1.5, "λογικ" to 1.0, "εξοπλισμεν" to 1.5, "καλ" to 1.3,
        "ζεστ" to 1.0, "γρηγορ" to 1.5, "οκ" to 0.5,
        "excellent" to 3.0, "perfect" to 3.0, "spotless" to 3.0, "clean" to 2.0,
        "comfortable" to 2.0, "helpful" to 2.0, "friendly" to 2.0, "great" to 2.5,
        "amazing" to 3.0, "quiet" to 2.0, "good" to 1.5, "delicious" to 2.0
    )

    private val NEGATIVE_LEXICON: List<Pair<String, Double>> = listOf(
        "απαραδεκτ" to -3.0, "βρωμ" to -3.0, "μουχλ" to -3.0, "μοχλ" to -3.0,
        "αγεν" to -2.5, "αθλι" to -3.0, "χαλασ" to -2.5, "σπασ" to -2.5,
        "δυσλειτουργ" to -2.5, "θορυβ" to -2.0, "ακαταστατ" to -2.5, "καθυστερ" to -2.0,
        "πανακριβ" to -2.5, "ακριβ" to -1.5, "μετρι" to -1.0, "αδυναμ" to -1.5,
        "αβολ" to -2.0, "απογοητευ" to -2.5, "παλι" to -1.0, "κρυ" to -1.5,
        "ψυχρ" to -1.5, "αργ" to -1.5, "μικρ" to -1.0, "βασικ" to -1.0,
        "λιγοστ" to -1.0, "λιτ" to -1.0, "θολ" to -1.5, "σκοτειν" to -1.5,
        "περιορισμεν" to -1.0, "ελειπ" to -1.5, "ενοχλ" to -2.0, "επαναλαμβανομεν" to -1.0,
        "κουραζ" to -1.0, "κακ" to -2.0, "κλεισουρ" to -1.8,
        "dirty" to -3.0, "filthy" to -3.0, "mold" to -3.0, "broken" to -2.5,
        "rude" to -2.5, "terrible" to -3.0, "awful" to -3.0, "noisy" to -2.0,
        "noise" to -2.0, "expensive" to -1.5, "overpriced" to -2.0, "cold" to -1.5,
        "slow" to -1.5, "bad" to -2.0, "poor" to -2.0
    )

    private val NEGATORS = setOf("δεν", "οχι", "μη", "not", "no", "never", "χωρισ")
    private val INTENSIFIERS = mapOf(
        "πολυ" to 1.4, "υπερβολικα" to 1.6, "τελειωσ" to 1.5,
        "λιγο" to 0.7, "very" to 1.4, "extremely" to 1.6
    )

    private data class ClauseAnalysis(
        val quote: String,
        val categories: List<ActionCategory>,
        val hitValues: List<Double>,
        val hypothetical: Boolean,
        var resolved: Boolean
    )

    fun norm(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val noAccents = DIACRITICS_REGEX.replace(decomposed, "")
            .replace('ς', 'σ')
            .replace('’', '\'')
        return CONTRACTION_REGEX.replace(noAccents, " not ")
    }

    fun accentInsensitiveContains(haystack: String, query: String): Boolean {
        val q = norm(query).trim()
        if (q.isEmpty()) return true
        return norm(haystack).contains(q)
    }

    fun detectLanguage(text: String): ReviewLanguage {
        return if (Regex("[α-ωάέήίόύώ]", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            ReviewLanguage.EL
        } else {
            ReviewLanguage.EN
        }
    }

    private fun findCategories(clauseText: String): List<ActionCategory> {
        val normalized = norm(clauseText)
        val words = TOKEN_REGEX.findAll(normalized).map { it.value }.toList()
        return ActionCategory.entries.filter { cat ->
            val keys = CATEGORY_KEYWORDS[cat].orEmpty()
            keys.any { k ->
                if (k.contains(' ') || k.contains('-')) {
                    normalized.contains(k)
                } else {
                    words.any { w -> w.startsWith(k) && !isExcludedFromStem(w, k) }
                }
            }
        }
    }

    private fun scoreClause(clauseText: String): List<Double> {
        val normalized = norm(clauseText)
        val words = TOKEN_REGEX.findAll(normalized).map { it.value }.toList()
        val values = mutableListOf<Double>()

        for (i in words.indices) {
            val word = words[i]
            val entry = NEGATIVE_LEXICON.firstOrNull { (stem, _) ->
                word.startsWith(stem) && !isExcludedFromStem(word, stem)
            }
                ?: POSITIVE_LEXICON.firstOrNull { (stem, _) -> word.startsWith(stem) }
                ?: continue
            val prevWindow = words.subList(max(0, i - 3), i)
            val negated = prevWindow.any { it in NEGATORS }
            val intensity = prevWindow.mapNotNull { INTENSIFIERS[it] }.lastOrNull() ?: 1.0
            values.add(entry.second * intensity * if (negated) -0.9 else 1.0)
        }
        if (NON_FUNCTIONAL_REGEX.containsMatchIn(normalized)) {
            values.add(-2.5)
        }
        if (normalized.contains("δεν απαντουσ") || normalized.contains("δεν ηταν ετοιμ")) {
            values.add(-2.2)
        }
        return values
    }

    fun analyzeReviewText(text: String): ReviewAnalysisResult {
        val input = text.trim().take(5000)
        val sentences = SENTENCE_REGEX.findAll(input).map { it.value }.toList()
        val clauses = mutableListOf<ClauseAnalysis>()

        for (sentence in sentences) {
            val sentenceStartIdx = clauses.size
            val chunks = sentence.split(CLAUSE_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (quote in chunks) {
                val normalized = norm(quote)
                val cats = findCategories(quote)
                val hits = scoreClause(quote)
                val hypothetical = HYPOTHETICAL_REGEX.containsMatchIn(normalized)
                val resolved = RESOLVED_REGEX.containsMatchIn(normalized) &&
                    !NOT_RESOLVED_REGEX.containsMatchIn(normalized)

                val clause = ClauseAnalysis(
                    quote = quote,
                    categories = cats,
                    hitValues = hits,
                    hypothetical = hypothetical,
                    resolved = resolved
                )

                if (resolved) {
                    val onlyStaffAttribution = hits.none { it < 0.0 } && cats.all { it == ActionCategory.STAFF }
                    for (j in sentenceStartIdx until clauses.size) {
                        if (cats.isEmpty() || onlyStaffAttribution || clauses[j].categories.any { it in cats }) {
                            clauses[j].resolved = true
                        }
                    }
                    if ((cats.isEmpty() || onlyStaffAttribution) && sentenceStartIdx == clauses.size && sentenceStartIdx > 0 &&
                        BACKREF_ISSUE_REGEX.containsMatchIn(normalized)
                    ) {
                        clauses[sentenceStartIdx - 1].resolved = true
                    }
                }
                clauses.add(clause)
            }
        }

        val allHits = clauses.flatMap { it.hitValues }
        val avg = if (allHits.isEmpty()) null else allHits.average()
        val overallSentiment = when {
            avg == null -> SentimentType.NEUTRAL
            avg >= 0.4 -> SentimentType.POSITIVE
            avg <= -0.3 -> SentimentType.NEGATIVE
            else -> SentimentType.NEUTRAL
        }

        val aspects = ActionCategory.entries.mapNotNull { cat ->
            val related = clauses.filter { cat in it.categories }
            if (related.isEmpty()) return@mapNotNull null
            val vals = related.flatMap { it.hitValues }
            val hasPos = vals.any { it > 0.0 }
            val hasNeg = vals.any { it < 0.0 }
            val kind = when {
                hasPos && hasNeg -> AspectSentimentKind.MIXED
                hasNeg -> AspectSentimentKind.NEGATIVE
                hasPos -> AspectSentimentKind.POSITIVE
                else -> AspectSentimentKind.UNKNOWN
            }
            ReviewAspectInsight(
                category = cat,
                sentiment = kind,
                quotes = related.map { it.quote }
            )
        }

        val issues = mutableListOf<ReviewIssue>()
        for (clause in clauses) {
            if (!clause.hitValues.any { it < 0.0 }) continue
            var cats = clause.categories
            if (ActionCategory.MAINTENANCE in cats) {
                cats = cats.filter { it != ActionCategory.ROOM }
            }
            if (ActionCategory.CLEAN in cats) {
                cats = cats.filter { it != ActionCategory.ROOM }
            }
            if (cats.isEmpty()) {
                cats = listOf(ActionCategory.STAFF)
            }
            for (cat in cats) {
                if (issues.any {
                        it.category == cat &&
                            it.resolved == clause.resolved &&
                            it.hypothetical == clause.hypothetical
                    }
                ) continue
                val isUrgent = URGENT_REGEX.containsMatchIn(norm(clause.quote))
                issues.add(
                    ReviewIssue(
                        category = cat,
                        quote = clause.quote,
                        resolved = clause.resolved,
                        hypothetical = clause.hypothetical,
                        severity = if (isUrgent) 3 else 2
                    )
                )
            }
        }

        val pending = issues.filter { it.isPending }
        val triageCategory = when {
            pending.isNotEmpty() -> ReviewTriageCategory.REAL_PENDING
            issues.any { it.resolved } -> ReviewTriageCategory.REAL_RESOLVED
            clauses.any { it.hypothetical } -> ReviewTriageCategory.HYPOTHETICAL
            allHits.isEmpty() -> ReviewTriageCategory.INSUFFICIENT
            else -> ReviewTriageCategory.POSITIVE_COMMENT
        }

        return ReviewAnalysisResult(
            triageCategory = triageCategory,
            overallSentiment = overallSentiment,
            aspects = aspects,
            issues = issues,
            pendingIssues = pending,
            signalCount = allHits.size
        )
    }

    fun actionKey(reviewId: String, category: ActionCategory): String = "$reviewId:${category.name}"

    fun reviewWorkflowStatus(
        review: HotelReview,
        assignedTasks: List<AssignedActionTask>,
        analysis: ReviewAnalysisResult = analyzeReviewText(review.reviewText)
    ): ReviewWorkflowStatus {
        val pending = analysis.pendingIssues
        if (pending.isEmpty()) return ReviewWorkflowStatus.NO_ACTION
        val matchingTasks = pending.map { issue ->
            val key = actionKey(review.id, issue.category)
            assignedTasks.firstOrNull { it.key == key }
        }
        return when {
            matchingTasks.all { it != null && it.status == ActionTaskStatus.DONE } -> ReviewWorkflowStatus.DONE
            matchingTasks.any { it != null && it.status == ActionTaskStatus.IN_PROGRESS } -> ReviewWorkflowStatus.IN_PROGRESS
            matchingTasks.any { it != null && it.status == ActionTaskStatus.OPEN } -> ReviewWorkflowStatus.ASSIGNED
            else -> ReviewWorkflowStatus.AWAITING
        }
    }

    fun smartActionTitle(category: ActionCategory, quote: String): String {
        val normalized = norm(quote)
        if (category == ActionCategory.MAINTENANCE) {
            if (Regex("(?:κλιματισ|air condition|aircon)").containsMatchIn(normalized)) {
                return "Έλεγξε τη λειτουργία του κλιματισμού και ενημέρωσε τον επισκέπτη"
            }
            if (Regex("(?:wifi|wi-fi)").containsMatchIn(normalized)) {
                return "Έλεγξε τη σύνδεση Wi-Fi και επιβεβαίωσε ότι αποκαταστάθηκε"
            }
            if (Regex("(?:ζεστο νερο|κρυο νερο|hot water)").containsMatchIn(normalized)) {
                return "Έλεγξε την παροχή ζεστού νερού και αποκατάστησε τη βλάβη"
            }
        }
        return category.defaultActionTitle
    }

    fun buildRecommendations(
        reviews: List<HotelReview>,
        reviewPropertyMap: Map<String, String>,
        selectedPropertyId: String,
        assignedTasks: List<AssignedActionTask>,
        checkedReviewIds: Set<String>,
        weights: PriorityWeights,
        limits: PlanLimits,
        nowMillis: Long
    ): List<RecommendedAction> {
        val scopedReviews = reviews.filter { r ->
            selectedPropertyId == HotelProperty.ALL_ID ||
                (reviewPropertyMap[r.id] ?: "p1") == selectedPropertyId
        }
        val analysisCache = scopedReviews.associate { it.id to analyzeReviewText(it.reviewText) }
        val assignedKeys = assignedTasks.map { it.key }.toSet()
        val candidates = mutableListOf<RecommendedAction>()

        for (review in scopedReviews) {
            val analysis = analysisCache[review.id] ?: continue
            val propId = reviewPropertyMap[review.id] ?: "p1"
            val propName = HotelProperty.displayName(propId)
            val daysAgo = max(0L, (nowMillis - review.stayDateMillis) / DAY_MS)

            for (issue in analysis.pendingIssues) {
                val key = actionKey(review.id, issue.category)
                if (key in assignedKeys) continue

                val recurrence30d = scopedReviews.count { other ->
                    val otherProp = reviewPropertyMap[other.id] ?: "p1"
                    val otherDaysAgo = (nowMillis - other.stayDateMillis) / DAY_MS
                    otherProp == propId &&
                        otherDaysAgo in 0..30 &&
                        (analysisCache[other.id]?.pendingIssues?.any { it.category == issue.category } == true)
                }.coerceAtLeast(1)

                val recencyBonus = max(0.0, 5.0 - daysAgo / 7.0)
                val rawScore = issue.severity * weights.severityWeight +
                    min(recurrence30d, 5) * weights.recurrenceWeight +
                    recencyBonus
                val score = (rawScore * 10.0).roundToInt() / 10.0

                candidates.add(
                    RecommendedAction(
                        key = key,
                        reviewId = review.id,
                        propertyId = propId,
                        propertyName = propName,
                        guestName = review.guestName,
                        category = issue.category,
                        departmentNameEl = issue.category.departmentNameEl,
                        title = smartActionTitle(issue.category, issue.quote),
                        quote = issue.quote,
                        severity = issue.severity,
                        effortMinutes = issue.category.effortMinutes,
                        costEuros = issue.category.costEuros,
                        recurrence30d = recurrence30d,
                        stayDateMillis = review.stayDateMillis,
                        deadlineLabel = if (issue.severity == 3) "Σήμερα" else "Εντός 48 ωρών",
                        priorityScore = score,
                        isAnalysisConfirmed = review.id in checkedReviewIds,
                        fitsSingleLimits = issue.category.effortMinutes <= limits.maxMinutes &&
                            issue.category.costEuros <= limits.maxBudgetEuros
                    )
                )
            }
        }

        return candidates.sortedWith(
            compareByDescending<RecommendedAction> { it.priorityScore }
                .thenByDescending { it.stayDateMillis }
                .thenBy { it.key }
        )
    }

    fun buildDailyPlan(
        candidates: List<RecommendedAction>,
        limits: PlanLimits,
        maxItems: Int = 3
    ): DailyActionPlan {
        var usedMinutes = 0
        var usedBudget = 0
        val selected = mutableListOf<RecommendedAction>()

        for (candidate in candidates) {
            if (selected.size < maxItems &&
                usedMinutes + candidate.effortMinutes <= limits.maxMinutes &&
                usedBudget + candidate.costEuros <= limits.maxBudgetEuros
            ) {
                selected.add(candidate)
                usedMinutes += candidate.effortMinutes
                usedBudget += candidate.costEuros
            }
        }
        return DailyActionPlan(
            selected = selected,
            totalMinutes = usedMinutes,
            totalBudgetEuros = usedBudget
        )
    }

    fun buildTopicPatterns(
        reviews: List<HotelReview>,
        reviewPropertyMap: Map<String, String>,
        selectedPropertyId: String,
        windowDays: Int,
        sourceFilter: ReviewSource?,
        nowMillis: Long
    ): List<TopicPatternSignal> {
        val safeWindow = if (windowDays in listOf(7, 14, 30, 60)) windowDays else 30
        val scoped = reviews.filter { r ->
            val propMatches = selectedPropertyId == HotelProperty.ALL_ID ||
                (reviewPropertyMap[r.id] ?: "p1") == selectedPropertyId
            val srcMatches = sourceFilter == null || r.sourceEnum == sourceFilter
            propMatches && srcMatches
        }

        val currentWindow = scoped.filter { r ->
            val diffDays = (nowMillis - r.stayDateMillis) / DAY_MS
            diffDays in 0 until safeWindow
        }
        val previousWindow = scoped.filter { r ->
            val diffDays = (nowMillis - r.stayDateMillis) / DAY_MS
            diffDays in safeWindow until (2 * safeWindow)
        }

        val analysisMap = scoped.associate { it.id to analyzeReviewText(it.reviewText) }

        fun aspectSets(review: HotelReview): Pair<Set<TopicAspect>, Set<TopicAspect>> {
            val res = analysisMap[review.id] ?: return emptySet<TopicAspect>() to emptySet()
            val negative = res.issues
                .filter { !it.hypothetical }
                .map { it.category.topicAspect }
                .toSet()
            val mentioned = (res.aspects.map { it.category.topicAspect } + negative).toSet()
            return mentioned to negative
        }

        val aspectCache = scoped.associate { it.id to aspectSets(it) }

        return TopicAspect.entries.mapNotNull { aspect ->
            val currentMentioned = currentWindow.filter { aspect in (aspectCache[it.id]?.first ?: emptySet()) }
            val currentNegative = currentMentioned.filter { aspect in (aspectCache[it.id]?.second ?: emptySet()) }
            if (currentNegative.isEmpty()) return@mapNotNull null

            val prevMentioned = previousWindow.filter { aspect in (aspectCache[it.id]?.first ?: emptySet()) }
            val prevNegative = prevMentioned.filter { aspect in (aspectCache[it.id]?.second ?: emptySet()) }

            val currentRate = currentNegative.size.toDouble() / currentMentioned.size.toDouble()
            val prevRate = if (prevMentioned.isEmpty()) null else prevNegative.size.toDouble() / prevMentioned.size.toDouble()
            val rateChange = prevRate?.let { ((currentRate - it) * 1000.0).roundToInt() / 10.0 }

            val properties = currentNegative.map { reviewPropertyMap[it.id] ?: "p1" }.toSet()
            val evidence = currentNegative.flatMap { rev ->
                val propId = reviewPropertyMap[rev.id] ?: "p1"
                val propName = HotelProperty.displayName(propId)
                (analysisMap[rev.id]?.issues ?: emptyList())
                    .filter { !it.hypothetical && it.category.topicAspect == aspect }
                    .map { issue ->
                        PatternEvidenceItem(
                            reviewId = rev.id,
                            propertyId = propId,
                            propertyName = propName,
                            guestName = rev.guestName,
                            source = rev.sourceEnum,
                            stayDateMillis = rev.stayDateMillis,
                            quote = issue.quote,
                            resolvedIncident = issue.resolved
                        )
                    }
            }

            TopicPatternSignal(
                aspect = aspect,
                totalMentions = currentMentioned.size,
                negativeCount = currentNegative.size,
                negativeRatePercent = (currentRate * 100.0).roundToInt(),
                previousMentions = prevMentioned.size,
                rateChangePoints = rateChange,
                propertyCount = properties.size,
                reviewIds = currentNegative.map { it.id },
                evidence = evidence
            )
        }.sortedWith(
            compareByDescending<TopicPatternSignal> { it.negativeCount }
                .thenByDescending { it.propertyCount }
                .thenBy { it.aspect.name }
        )
    }

    fun taskPendingHours(task: AssignedActionTask, nowMillis: Long): Int {
        if (task.createdAtMillis <= 0L) return 0
        return max(0L, (nowMillis - task.createdAtMillis) / HOUR_MS).toInt()
    }

    fun isTaskOverdue(
        task: AssignedActionTask,
        rules: AlertRulesConfig,
        nowMillis: Long
    ): Boolean {
        if (task.status == ActionTaskStatus.DONE) return false
        val safeHours = rules.overdueHours.coerceIn(12, 168)
        return taskPendingHours(task, nowMillis) >= safeHours
    }

    fun buildOperationalAlerts(
        reviews: List<HotelReview>,
        assignedTasks: List<AssignedActionTask>,
        reviewPropertyMap: Map<String, String>,
        selectedPropertyId: String,
        rules: AlertRulesConfig,
        dismissedAlertIds: Set<String>,
        nowMillis: Long
    ): List<OperationalAlert> {
        val alerts = mutableListOf<OperationalAlert>()
        val safeLowRating = rules.lowRatingThreshold.coerceIn(1.0, 9.0)
        val safeOverdueHours = rules.overdueHours.coerceIn(12, 168)

        // 1. Overdue assigned tasks (SLA exceeded)
        assignedTasks
            .filter { selectedPropertyId == HotelProperty.ALL_ID || it.propertyId == selectedPropertyId }
            .forEach { task ->
                if (!isTaskOverdue(task, rules, nowMillis)) return@forEach
                val alertId = "overdue:${task.id}"
                if (alertId in dismissedAlertIds) return@forEach
                val hours = taskPendingHours(task, nowMillis)
                alerts.add(
                    OperationalAlert(
                        id = alertId,
                        kind = CriticalAlertKind.OVERDUE_TASK,
                        severity = 3,
                        propertyId = task.propertyId,
                        propertyName = task.propertyName,
                        departmentNameEl = task.departmentNameEl,
                        reviewId = task.reviewId,
                        taskId = task.id,
                        title = "Εκπρόθεσμη εργασία (${hours}ω σε εκκρεμότητα)",
                        detail = "${task.departmentNameEl} • ${task.title} (όριο SLA: ${safeOverdueHours}ω)",
                        timestampMillis = task.createdAtMillis,
                        hoursPending = hours
                    )
                )
            }

        // 2. Critical low ratings + 3. Urgent unassigned incidents
        val scopedReviews = reviews.filter { r ->
            selectedPropertyId == HotelProperty.ALL_ID ||
                (reviewPropertyMap[r.id] ?: "p1") == selectedPropertyId
        }
        val assignedKeys = assignedTasks.map { it.key }.toSet()

        for (review in scopedReviews) {
            if (review.resolved) continue
            val propId = reviewPropertyMap[review.id] ?: "p1"
            val propName = HotelProperty.displayName(propId)
            val analysis = analyzeReviewText(review.reviewText)

            if (review.rating <= safeLowRating) {
                val alertId = "low_rating:${review.id}"
                if (alertId !in dismissedAlertIds) {
                    val excerpt = review.reviewText.trim().take(110) +
                        if (review.reviewText.trim().length > 110) "…" else ""
                    val ratingStr = String.format(java.util.Locale.US, "%.1f", review.rating)
                    alerts.add(
                        OperationalAlert(
                            id = alertId,
                            kind = CriticalAlertKind.LOW_RATING,
                            severity = if (review.rating <= 3.5) 3 else 2,
                            propertyId = propId,
                            propertyName = propName,
                            departmentNameEl = analysis.pendingIssues.firstOrNull()?.category?.departmentNameEl
                                ?: review.departmentEnums.firstOrNull()?.shortLabelEl,
                            reviewId = review.id,
                            taskId = null,
                            title = "Κρίσιμη χαμηλή βαθμολογία ($ratingStr/10)",
                            detail = "${review.guestName} • «$excerpt»",
                            timestampMillis = review.stayDateMillis
                        )
                    )
                }
                continue
            }

            val urgentUnassigned = analysis.pendingIssues.firstOrNull { issue ->
                issue.severity == 3 && actionKey(review.id, issue.category) !in assignedKeys
            }
            if (urgentUnassigned != null) {
                val alertId = "urgent:${review.id}"
                if (alertId !in dismissedAlertIds) {
                    alerts.add(
                        OperationalAlert(
                            id = alertId,
                            kind = CriticalAlertKind.URGENT_UNASSIGNED,
                            severity = 3,
                            propertyId = propId,
                            propertyName = propName,
                            departmentNameEl = urgentUnassigned.category.departmentNameEl,
                            reviewId = review.id,
                            taskId = null,
                            title = "Επείγον περιστατικό χωρίς ανάθεση (${urgentUnassigned.category.departmentNameEl})",
                            detail = "${review.guestName} • «${urgentUnassigned.quote}»",
                            timestampMillis = review.stayDateMillis
                        )
                    )
                }
            }
        }

        val kindOrder = mapOf(
            CriticalAlertKind.OVERDUE_TASK to 0,
            CriticalAlertKind.LOW_RATING to 1,
            CriticalAlertKind.URGENT_UNASSIGNED to 2
        )
        return alerts.sortedWith(
            compareBy<OperationalAlert> { kindOrder[it.kind] ?: 9 }
                .thenByDescending { it.severity }
                .thenByDescending { it.timestampMillis }
                .thenBy { it.id }
        )
    }

    fun draftTemplateReply(review: HotelReview, analysis: ReviewAnalysisResult = analyzeReviewText(review.reviewText)): String {
        return if (review.languageEnum == ReviewLanguage.EN) {
            when {
                analysis.pendingIssues.isNotEmpty() ->
                    "Dear ${review.guestName}, thank you for sharing your feedback. We sincerely apologize for the issue you experienced and our team is inspecting it right away to ensure a seamless stay next time."
                analysis.triageCategory == ReviewTriageCategory.REAL_RESOLVED ->
                    "Dear ${review.guestName}, we are glad our team resolved the issue promptly during your stay. Thank you for your patience and kind feedback!"
                else ->
                    "Dear ${review.guestName}, thank you so much for your review! Your feedback helps our team keep delivering memorable Greek hospitality."
            }
        } else {
            when {
                analysis.pendingIssues.isNotEmpty() ->
                    "Αγαπητέ/ή ${review.guestName}, σας ευχαριστούμε που μοιραστήκατε την εμπειρία σας. Λυπούμαστε για το ζήτημα που περιγράφετε — το αρμόδιο τμήμα μας έχει ήδη ενημερωθεί για την άμεση αντιμετώπισή του."
                analysis.triageCategory == ReviewTriageCategory.REAL_RESOLVED ->
                    "Αγαπητέ/ή ${review.guestName}, χαιρόμαστε που το ζήτημα αντιμετωπίστηκε άμεσα κατά τη διαμονή σας. Σας ευχαριστούμε για την υπομονή και τα θετικά σας σχόλια!"
                else ->
                    "Αγαπητέ/ή ${review.guestName}, σας ευχαριστούμε θερμά για την αξιολόγησή σας! Τα σχόλιά σας βοηθούν την ομάδα μας να βελτιώνει διαρκώς τη φιλοξενία μας."
            }
        }
    }
}

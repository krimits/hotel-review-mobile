package com.example.data

import android.content.Context
import com.example.model.ActionCategory
import com.example.model.ActionTaskStatus
import com.example.model.AlertRulesConfig
import com.example.model.AssignedActionTask
import com.example.model.HotelProperty
import com.example.model.HotelReview
import com.example.model.PlanLimits
import com.example.model.PriorityWeights
import com.example.model.TaskDepartmentNote
import org.json.JSONArray
import org.json.JSONObject

data class OperationalSnapshot(
    val reviewPropertyMap: Map<String, String> = emptyMap(),
    val checkedReviewIds: Set<String> = emptySet(),
    val assignedTasks: List<AssignedActionTask> = emptyList(),
    val weights: PriorityWeights = PriorityWeights(),
    val limits: PlanLimits = PlanLimits(),
    val alertRules: AlertRulesConfig = AlertRulesConfig(),
    val dismissedAlertIds: Set<String> = emptySet(),
    val demoReviews: List<HotelReview>? = null
)

/**
 * Persists operational workflow state (property assignments, mandatory human
 * analysis confirmations, assigned tasks & status transitions, priority weights,
 * daily plan limits, and local Demo Mode reviews) in private SharedPreferences,
 * keeping Cloud Firestore rules 100% intact.
 */
class OperationalStateStore(
    context: Context,
    scopeKey: String
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "hotel_pulse_ops_$scopeKey",
        Context.MODE_PRIVATE
    )

    fun load(): OperationalSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return OperationalSnapshot()
        return runCatching { decodeSnapshot(raw) }.getOrElse { OperationalSnapshot() }
    }

    fun save(snapshot: OperationalSnapshot) {
        val encoded = encodeSnapshot(snapshot)
        prefs.edit().putString(KEY_SNAPSHOT, encoded).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private fun encodeSnapshot(s: OperationalSnapshot): String {
        val root = JSONObject()

        val propObj = JSONObject()
        s.reviewPropertyMap.forEach { (reviewId, propId) -> propObj.put(reviewId, propId) }
        root.put("reviewPropertyMap", propObj)

        val checkedArr = JSONArray()
        s.checkedReviewIds.forEach { checkedArr.put(it) }
        root.put("checkedReviewIds", checkedArr)

        val tasksArr = JSONArray()
        s.assignedTasks.forEach { t ->
            tasksArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("key", t.key)
                    put("reviewId", t.reviewId)
                    put("propertyId", t.propertyId)
                    put("propertyName", t.propertyName)
                    put("category", t.category.name)
                    put("departmentNameEl", t.departmentNameEl)
                    put("title", t.title)
                    put("quote", t.quote)
                    put("severity", t.severity)
                    put("effortMinutes", t.effortMinutes)
                    put("costEuros", t.costEuros)
                    put("deadlineLabel", t.deadlineLabel)
                    put("status", t.status.name)
                    put("createdAtMillis", t.createdAtMillis)
                    val notesArr = JSONArray()
                    t.notes.forEach { n ->
                        notesArr.put(
                            JSONObject().apply {
                                put("id", n.id)
                                put("fromDepartment", n.fromDepartment)
                                put("toDepartment", n.toDepartment)
                                put("text", n.text)
                                put("createdAtMillis", n.createdAtMillis)
                            }
                        )
                    }
                    put("notes", notesArr)
                }
            )
        }
        root.put("assignedTasks", tasksArr)

        root.put("severityWeight", s.weights.severityWeight)
        root.put("recurrenceWeight", s.weights.recurrenceWeight)
        root.put("maxMinutes", s.limits.maxMinutes)
        root.put("maxBudgetEuros", s.limits.maxBudgetEuros)
        root.put("alertLowRatingThreshold", s.alertRules.lowRatingThreshold)
        root.put("alertOverdueHours", s.alertRules.overdueHours)
        val dismissedArr = JSONArray()
        s.dismissedAlertIds.forEach { dismissedArr.put(it) }
        root.put("dismissedAlertIds", dismissedArr)

        if (s.demoReviews != null) {
            val revArr = JSONArray()
            s.demoReviews.forEach { r ->
                revArr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("userId", r.userId)
                        put("guestName", r.guestName)
                        put("source", r.source)
                        put("language", r.language)
                        put("rating", r.rating)
                        put("stayDateMillis", r.stayDateMillis)
                        put("reviewText", r.reviewText)
                        put("sentiment", r.sentiment)
                        put("sentimentScore", r.sentimentScore)
                        put("priority", r.priority)
                        put("departments", JSONArray(r.departments))
                        put("keyHighlights", JSONArray(r.keyHighlights))
                        put("summaryLine", r.summaryLine)
                        put("suggestedReply", r.suggestedReply)
                        put("resolved", r.resolved)
                    }
                )
            }
            root.put("demoReviews", revArr)
        }

        return root.toString()
    }

    private fun decodeSnapshot(raw: String): OperationalSnapshot {
        val root = JSONObject(raw)

        val propMap = mutableMapOf<String, String>()
        root.optJSONObject("reviewPropertyMap")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "p1")
                if (HotelProperty.byId(v) != null) propMap[k] = v
            }
        }

        val checked = mutableSetOf<String>()
        root.optJSONArray("checkedReviewIds")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) checked.add(id)
            }
        }

        val tasks = mutableListOf<AssignedActionTask>()
        root.optJSONArray("assignedTasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val cat = ActionCategory.fromRaw(obj.optString("category")) ?: continue
                val parsedNotes = buildList {
                    val notesArr = obj.optJSONArray("notes")
                    if (notesArr != null) {
                        for (j in 0 until notesArr.length()) {
                            val nObj = notesArr.optJSONObject(j) ?: continue
                            val noteText = nObj.optString("text").trim()
                            if (noteText.isNotEmpty()) {
                                add(
                                    TaskDepartmentNote(
                                        id = nObj.optString("id", "note_$j"),
                                        fromDepartment = nObj.optString("fromDepartment", cat.departmentNameEl),
                                        toDepartment = nObj.optString("toDepartment", "Υποδοχή"),
                                        text = noteText.take(500),
                                        createdAtMillis = nObj.optLong("createdAtMillis", System.currentTimeMillis())
                                    )
                                )
                            }
                        }
                    }
                }
                tasks.add(
                    AssignedActionTask(
                        id = obj.optString("id"),
                        key = obj.optString("key"),
                        reviewId = obj.optString("reviewId"),
                        propertyId = obj.optString("propertyId", "p1"),
                        propertyName = obj.optString("propertyName", HotelProperty.displayName("p1")),
                        category = cat,
                        departmentNameEl = obj.optString("departmentNameEl", cat.departmentNameEl),
                        title = obj.optString("title", cat.defaultActionTitle),
                        quote = obj.optString("quote"),
                        severity = obj.optInt("severity", 2),
                        effortMinutes = obj.optInt("effortMinutes", cat.effortMinutes),
                        costEuros = obj.optInt("costEuros", cat.costEuros),
                        deadlineLabel = obj.optString("deadlineLabel", "Εντός 48 ωρών"),
                        status = ActionTaskStatus.fromRaw(obj.optString("status")),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        notes = parsedNotes
                    )
                )
            }
        }

        val demoReviews: List<HotelReview>? = if (root.has("demoReviews") && !root.isNull("demoReviews")) {
            val arr = root.optJSONArray("demoReviews")
            buildList<HotelReview> {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val depts = buildList<String> {
                            val da = o.optJSONArray("departments")
                            if (da != null) for (j in 0 until da.length()) add(da.optString(j))
                        }
                        val highlights = buildList<String> {
                            val ha = o.optJSONArray("keyHighlights")
                            if (ha != null) for (j in 0 until ha.length()) add(ha.optString(j))
                        }
                        add(
                            HotelReview(
                                id = o.optString("id"),
                                userId = o.optString("userId", "demo"),
                                guestName = o.optString("guestName"),
                                source = o.optString("source"),
                                language = o.optString("language"),
                                rating = o.optDouble("rating", 8.0),
                                stayDateMillis = o.optLong("stayDateMillis", System.currentTimeMillis()),
                                reviewText = o.optString("reviewText"),
                                sentiment = o.optString("sentiment"),
                                sentimentScore = o.optLong("sentimentScore", 50L),
                                priority = o.optString("priority"),
                                departments = depts,
                                keyHighlights = highlights,
                                summaryLine = o.optString("summaryLine"),
                                suggestedReply = o.optString("suggestedReply"),
                                resolved = o.optBoolean("resolved", false)
                            )
                        )
                    }
                }
            }
        } else {
            null
        }

        val dismissedAlerts = mutableSetOf<String>()
        root.optJSONArray("dismissedAlertIds")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) dismissedAlerts.add(id)
            }
        }

        return OperationalSnapshot(
            reviewPropertyMap = propMap,
            checkedReviewIds = checked,
            assignedTasks = tasks,
            weights = PriorityWeights(
                severityWeight = root.optInt("severityWeight", 4).coerceIn(1, 5),
                recurrenceWeight = root.optInt("recurrenceWeight", 3).coerceIn(1, 5)
            ),
            limits = PlanLimits(
                maxMinutes = root.optInt("maxMinutes", 120).coerceIn(15, 480),
                maxBudgetEuros = root.optInt("maxBudgetEuros", 150).coerceIn(0, 1000)
            ),
            alertRules = AlertRulesConfig(
                lowRatingThreshold = root.optDouble("alertLowRatingThreshold", 4.5).coerceIn(1.0, 9.0),
                overdueHours = root.optInt("alertOverdueHours", 48).coerceIn(12, 168)
            ),
            dismissedAlertIds = dismissedAlerts,
            demoReviews = demoReviews
        )
    }

    companion object {
        private const val KEY_SNAPSHOT = "ops_snapshot_v2"
    }
}

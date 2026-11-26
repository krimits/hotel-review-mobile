package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.HotelDepartment
import com.example.model.HotelProfile
import com.example.model.HotelReview
import com.example.model.ReviewLanguage
import com.example.model.ReviewSource
import com.example.model.SeedReviewsProvider
import com.example.model.SentimentType
import com.example.model.TriagePriority
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HotelRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchingUser(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Απαιτείται σύνδεση με Google πριν την πρόσβαση στο Cloud.")
        check(uid == expectedUserId) { "Αναντιστοιχία συνδεδεμένου χρήστη." }
        return uid
    }

    fun observeHotelProfile(userId: String): Flow<HotelProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        HotelProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeReviews(userId: String): Flow<List<HotelReview>> {
        val path = "users/$userId/reviews"
        return db.collection("users").document(userId).collection("reviews")
            .orderBy("stayDateMillis", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    HotelReview::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun saveHotelProfile(
        userId: String,
        hotelName: String,
        hotelLocation: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireMatchingUser(userId)
            val cleanName = hotelName.trim().take(100).ifBlank { "Hotel Pulse Suites" }
            val cleanLocation = hotelLocation.trim().take(100)
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()

            if (!existingSnap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "hotelName" to cleanName,
                    "hotelLocation" to cleanLocation,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "hotelName" to cleanName,
                    "hotelLocation" to cleanLocation,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addReview(userId: String, review: HotelReview): Result<String> {
        var path = "users/$userId/reviews"
        return try {
            val uid = requireMatchingUser(userId)
            val docRef = db.collection("users").document(uid).collection("reviews").document()
            path = docRef.path

            val payload = buildReviewCreatePayload(uid, review)
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateReviewResolutionAndReply(
        userId: String,
        reviewId: String,
        resolved: Boolean,
        suggestedReply: String
    ): Result<Unit> {
        val path = "users/$userId/reviews/$reviewId"
        return try {
            val uid = requireMatchingUser(userId)
            require(reviewId.isNotBlank()) { "Το αναγνωριστικό κριτικής είναι κενό." }
            val docRef = db.collection("users").document(uid)
                .collection("reviews").document(reviewId)

            docRef.update(
                mapOf(
                    "resolved" to resolved,
                    "suggestedReply" to suggestedReply.trim().take(3000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteReview(userId: String, reviewId: String): Result<Unit> {
        val path = "users/$userId/reviews/$reviewId"
        return try {
            val uid = requireMatchingUser(userId)
            require(reviewId.isNotBlank()) { "Το αναγνωριστικό κριτικής είναι κενό." }
            db.collection("users").document(uid)
                .collection("reviews").document(reviewId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedSampleReviews(userId: String): Result<Int> {
        return seedMultiPropertySampleReviews(userId).map { it.size }
    }

    suspend fun seedMultiPropertySampleReviews(userId: String): Result<Map<String, String>> {
        val path = "users/$userId/reviews"
        return try {
            val uid = requireMatchingUser(userId)
            val bundles = SeedReviewsProvider.buildMultiPropertySeedBundles(
                userId = uid,
                nowMillis = System.currentTimeMillis()
            )
            val batch = db.batch()
            val assignedPropMap = mutableMapOf<String, String>()
            bundles.forEach { bundle ->
                val docRef = db.collection("users").document(uid).collection("reviews").document()
                batch.set(docRef, buildReviewCreatePayload(uid, bundle.review))
                assignedPropMap[docRef.id] = bundle.propertyId
            }
            batch.commit().await()
            Result.success(assignedPropMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun buildReviewCreatePayload(uid: String, review: HotelReview): Map<String, Any> {
        val validSource = ReviewSource.fromRaw(review.source).name
        val validLanguage = ReviewLanguage.fromRaw(review.language).name
        val validSentiment = SentimentType.fromRaw(review.sentiment).name
        val validPriority = TriagePriority.fromRaw(review.priority).name
        val validDepartments = review.departments
            .mapNotNull { HotelDepartment.fromRaw(it)?.name }
            .distinct()
            .take(6)
        val validHighlights = review.keyHighlights
            .map { it.trim().take(160) }
            .filter { it.isNotEmpty() }
            .take(8)

        // Never include "id" in Map payloads because @DocumentId is used on HotelReview
        return mapOf(
            "userId" to uid,
            "guestName" to review.guestName.trim().take(100).ifBlank { "Επισκέπτης" },
            "source" to validSource,
            "language" to validLanguage,
            "rating" to review.rating.coerceIn(1.0, 10.0),
            "stayDateMillis" to review.stayDateMillis.coerceAtLeast(1L),
            "reviewText" to review.reviewText.trim().take(4000).ifBlank { "—" },
            "sentiment" to validSentiment,
            "sentimentScore" to review.sentimentScore.coerceIn(0L, 100L),
            "priority" to validPriority,
            "departments" to validDepartments,
            "keyHighlights" to validHighlights,
            "summaryLine" to review.summaryLine.trim().take(500),
            "suggestedReply" to review.suggestedReply.trim().take(3000),
            "resolved" to review.resolved,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

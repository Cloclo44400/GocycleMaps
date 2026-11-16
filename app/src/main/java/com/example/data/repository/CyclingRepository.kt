package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BikeRide
import com.example.data.model.CosmeticDefaults
import com.example.data.model.CyclingPointsCalculator
import com.example.data.model.LeaderboardCategory
import com.example.data.model.LeaderboardEntry
import com.example.data.model.SportType
import com.example.data.model.UserProfile
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CyclingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("L'utilisateur doit être connecté avec Google pour accéder à Firestore.")

    suspend fun ensureUserProfile(displayNameFallback: String = "Sportif"): UserProfile {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val leaderboardRef = db.collection("leaderboard").document(uid)
        return try {
            val snapshot = userRef.get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ) ?: UserProfile(id = uid, userId = uid, displayName = displayNameFallback)

                val lbSnap = leaderboardRef.get().await()
                if (!lbSnap.exists()) {
                    leaderboardRef.set(
                        mapOf(
                            "userId" to uid,
                            "displayName" to profile.displayName.take(60).ifEmpty { "Sportif" },
                            "totalPointsEarned" to profile.totalPointsEarned,
                            "totalDistanceMeters" to profile.totalDistanceMeters,
                            "totalRides" to profile.totalRides,
                            "cyclingPointsEarned" to profile.cyclingPointsEarned,
                            "cyclingDistanceMeters" to profile.cyclingDistanceMeters,
                            "cyclingRides" to profile.cyclingRides,
                            "runningPointsEarned" to profile.runningPointsEarned,
                            "runningDistanceMeters" to profile.runningDistanceMeters,
                            "runningRides" to profile.runningRides,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
                profile
            } else {
                val cleanName = displayNameFallback.trim().take(30).ifEmpty { "Sportif" }
                val userPayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "pointsBalance" to 0L,
                    "totalPointsEarned" to 0L,
                    "totalDistanceMeters" to 0L,
                    "totalRides" to 0L,
                    "cyclingPointsEarned" to 0L,
                    "cyclingDistanceMeters" to 0L,
                    "cyclingRides" to 0L,
                    "runningPointsEarned" to 0L,
                    "runningDistanceMeters" to 0L,
                    "runningRides" to 0L,
                    "unlockedCosmetics" to CosmeticDefaults.INITIAL_UNLOCKED_IDS,
                    "equippedBikeId" to CosmeticDefaults.BIKE_MINT,
                    "equippedHelmetId" to CosmeticDefaults.HELMET_CLASSIC,
                    "equippedTrailId" to CosmeticDefaults.TRAIL_EMERALD,
                    "equippedBadgeId" to CosmeticDefaults.BADGE_FREEWHEEL,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                val leaderboardPayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "totalPointsEarned" to 0L,
                    "totalDistanceMeters" to 0L,
                    "totalRides" to 0L,
                    "cyclingPointsEarned" to 0L,
                    "cyclingDistanceMeters" to 0L,
                    "cyclingRides" to 0L,
                    "runningPointsEarned" to 0L,
                    "runningDistanceMeters" to 0L,
                    "runningRides" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                val batch = db.batch()
                batch.set(userRef, userPayload)
                batch.set(leaderboardRef, leaderboardPayload)
                batch.commit().await()

                userRef.get().await()
                    .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid, displayName = cleanName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
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
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeRides(userId: String): Flow<List<BikeRide>> = flow {
        val colRef = db.collection("users").document(userId).collection("rides")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BikeRide::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeLeaderboard(
        category: LeaderboardCategory = LeaderboardCategory.ALL,
        limit: Long = 50L,
    ): Flow<List<LeaderboardEntry>> = flow {
        val query = db.collection("leaderboard")
            .orderBy(category.orderByField, Query.Direction.DESCENDING)
            .limit(limit.coerceIn(1L, 50L))
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    val raw = snapshot.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                    when (category) {
                        LeaderboardCategory.ALL -> raw
                        LeaderboardCategory.CYCLING -> raw.filter {
                            it.cyclingRides > 0L || it.cyclingPointsEarned > 0L || it.cyclingDistanceMeters > 0L
                        }
                        LeaderboardCategory.RUNNING -> raw.filter {
                            it.runningRides > 0L || it.runningPointsEarned > 0L || it.runningDistanceMeters > 0L
                        }
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "leaderboard")
                    }
                    throw error
                }
        )
    }

    suspend fun recordCompletedRide(
        distanceMeters: Long,
        durationSeconds: Long,
        avgSpeedKmh: Double,
        trailCosmeticId: String,
        startLabel: String = "Balade à vélo",
        sportType: SportType = SportType.CYCLING,
    ): Long {
        val uid = requireUserId()
        require(distanceMeters > 0L) { "La distance doit être supérieure à 0 m." }
        val pointsEarned = CyclingPointsCalculator.calculatePointsForDistanceMeters(distanceMeters, sportType)
        val userRef = db.collection("users").document(uid)
        val leaderboardRef = db.collection("leaderboard").document(uid)
        val rideId = UUID.randomUUID().toString()
        val rideRef = userRef.collection("rides").document(rideId)

        return try {
            db.runTransaction { transaction ->
                val profileSnap = transaction.get(userRef)
                val currentBalance = profileSnap.getLong("pointsBalance") ?: 0L
                val currentEarned = profileSnap.getLong("totalPointsEarned") ?: 0L
                val currentDist = profileSnap.getLong("totalDistanceMeters") ?: 0L
                val currentRides = profileSnap.getLong("totalRides") ?: 0L

                val currentCyclingPts = profileSnap.getLong("cyclingPointsEarned") ?: 0L
                val currentCyclingDist = profileSnap.getLong("cyclingDistanceMeters") ?: 0L
                val currentCyclingRides = profileSnap.getLong("cyclingRides") ?: 0L

                val currentRunningPts = profileSnap.getLong("runningPointsEarned") ?: 0L
                val currentRunningDist = profileSnap.getLong("runningDistanceMeters") ?: 0L
                val currentRunningRides = profileSnap.getLong("runningRides") ?: 0L

                val displayName = profileSnap.getString("displayName") ?: "Sportif"

                val newEarned = currentEarned + pointsEarned
                val newDist = currentDist + distanceMeters
                val newRides = currentRides + 1L

                val newCyclingPts = if (sportType == SportType.CYCLING) currentCyclingPts + pointsEarned else currentCyclingPts
                val newCyclingDist = if (sportType == SportType.CYCLING) currentCyclingDist + distanceMeters else currentCyclingDist
                val newCyclingRides = if (sportType == SportType.CYCLING) currentCyclingRides + 1L else currentCyclingRides

                val newRunningPts = if (sportType == SportType.RUNNING) currentRunningPts + pointsEarned else currentRunningPts
                val newRunningDist = if (sportType == SportType.RUNNING) currentRunningDist + distanceMeters else currentRunningDist
                val newRunningRides = if (sportType == SportType.RUNNING) currentRunningRides + 1L else currentRunningRides

                transaction.update(
                    userRef,
                    mapOf(
                        "pointsBalance" to (currentBalance + pointsEarned),
                        "totalPointsEarned" to newEarned,
                        "totalDistanceMeters" to newDist,
                        "totalRides" to newRides,
                        "cyclingPointsEarned" to newCyclingPts,
                        "cyclingDistanceMeters" to newCyclingDist,
                        "cyclingRides" to newCyclingRides,
                        "runningPointsEarned" to newRunningPts,
                        "runningDistanceMeters" to newRunningDist,
                        "runningRides" to newRunningRides,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )

                transaction.set(
                    leaderboardRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "totalPointsEarned" to newEarned,
                        "totalDistanceMeters" to newDist,
                        "totalRides" to newRides,
                        "cyclingPointsEarned" to newCyclingPts,
                        "cyclingDistanceMeters" to newCyclingDist,
                        "cyclingRides" to newCyclingRides,
                        "runningPointsEarned" to newRunningPts,
                        "runningDistanceMeters" to newRunningDist,
                        "runningRides" to newRunningRides,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )

                val defaultLabel = if (sportType == SportType.RUNNING) "Course à pied" else "Balade à vélo"
                val ridePayload = mapOf(
                    "userId" to uid,
                    "sportType" to sportType.id,
                    "distanceMeters" to distanceMeters,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "pointsEarned" to pointsEarned,
                    "avgSpeedKmh" to avgSpeedKmh.coerceIn(0.0, 150.0),
                    "trailCosmeticId" to trailCosmeticId,
                    "startLabel" to startLabel.trim().take(100).ifEmpty { defaultLabel },
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                transaction.set(rideRef, ridePayload)
            }.await()
            pointsEarned
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, rideRef.path)
            throw e
        }
    }

    suspend fun updateDisplayName(newDisplayName: String) {
        val uid = requireUserId()
        val cleanName = newDisplayName.trim()
        require(cleanName.length in 2..30) {
            "Le pseudo doit contenir entre 2 et 30 caractères."
        }
        val userRef = db.collection("users").document(uid)
        val leaderboardRef = db.collection("leaderboard").document(uid)

        try {
            db.runTransaction { transaction ->
                val userSnap = transaction.get(userRef)
                val earned = userSnap.getLong("totalPointsEarned") ?: 0L
                val dist = userSnap.getLong("totalDistanceMeters") ?: 0L
                val rides = userSnap.getLong("totalRides") ?: 0L
                val cyclingPts = userSnap.getLong("cyclingPointsEarned") ?: 0L
                val cyclingDist = userSnap.getLong("cyclingDistanceMeters") ?: 0L
                val cyclingRides = userSnap.getLong("cyclingRides") ?: 0L
                val runningPts = userSnap.getLong("runningPointsEarned") ?: 0L
                val runningDist = userSnap.getLong("runningDistanceMeters") ?: 0L
                val runningRides = userSnap.getLong("runningRides") ?: 0L

                transaction.update(
                    userRef,
                    mapOf(
                        "displayName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )

                transaction.set(
                    leaderboardRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "totalPointsEarned" to earned,
                        "totalDistanceMeters" to dist,
                        "totalRides" to rides,
                        "cyclingPointsEarned" to cyclingPts,
                        "cyclingDistanceMeters" to cyclingDist,
                        "cyclingRides" to cyclingRides,
                        "runningPointsEarned" to runningPts,
                        "runningDistanceMeters" to runningDist,
                        "runningRides" to runningRides,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun deleteRide(rideId: String) {
        val uid = requireUserId()
        val rideRef = db.collection("users").document(uid).collection("rides").document(rideId)
        try {
            rideRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, rideRef.path)
            throw e
        }
    }
}

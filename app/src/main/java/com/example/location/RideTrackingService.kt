package com.example.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.formatDuration
import com.example.ui.viewmodel.RideSessionState
import com.example.ui.viewmodel.RideStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class RideTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val coordinator = RideSessionCoordinator.getInstance(applicationContext)

        when (intent?.action) {
            ACTION_STOP -> {
                stopTrackingService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> coordinator.pauseRide()
            ACTION_RESUME -> coordinator.resumeRide()
        }

        if (!RideLocationTracker.hasLocationPermission(this)) {
            stopTrackingService()
            return START_NOT_STICKY
        }

        val currentState = coordinator.rideSession.value
        val currentMode = coordinator.powerMode.value
        if (currentState.status != RideStatus.RIDING && currentState.status != RideStatus.PAUSED) {
            stopTrackingService()
            return START_NOT_STICKY
        }

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(currentState, currentMode),
                foregroundType,
            )
        } catch (_: SecurityException) {
            stopTrackingService()
            return START_NOT_STICKY
        }

        coordinator.ensureLocationUpdatesActive(restart = false)
        syncWakeLock(currentState.status, currentMode)
        startObservingSession(coordinator)

        return START_STICKY
    }

    private fun startObservingSession(coordinator: RideSessionCoordinator) {
        if (observerJob?.isActive == true) return
        observerJob = serviceScope.launch {
            combine(coordinator.rideSession, coordinator.powerMode) { session, mode ->
                session to mode
            }.collect { (session, mode) ->
                if (session.status == RideStatus.IDLE || session.status == RideStatus.SAVING) {
                    stopTrackingService()
                    return@collect
                }
                syncWakeLock(session.status, mode)
                updateNotification(session, mode)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun syncWakeLock(status: RideStatus, mode: PowerMode) {
        val shouldHoldWakeLock = status == RideStatus.RIDING && mode == PowerMode.STANDARD
        if (shouldHoldWakeLock) {
            if (wakeLock == null) {
                val powerManager = getSystemService(PowerManager::class.java)
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKELOCK_TAG,
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(MAX_WAKELOCK_DURATION_MS)
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun updateNotification(session: RideSessionState, mode: PowerMode) {
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(session, mode))
        } catch (_: SecurityException) {
            // Foreground service remains active even if notification display permission was revoked.
        }
    }

    private fun buildNotification(session: RideSessionState, mode: PowerMode): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sportLabel = session.selectedSport.labelFr
        val distanceText = String.format(Locale.FRANCE, "%.2f km", session.distanceMeters / 1000.0)
        val durationText = formatDuration(session.elapsedSeconds)
        val pointsText = "+${session.livePointsEarned} pts"

        val title = when (session.status) {
            RideStatus.PAUSED -> "$sportLabel en pause • $distanceText"
            else -> "$sportLabel en cours • $distanceText ($pointsText)"
        }
        val modeSuffix = if (mode == PowerMode.ECO) "Mode Éco" else "Mode Standard"
        val body = "$durationText • $modeSuffix"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)

        if (session.status == RideStatus.RIDING) {
            val pauseIntent = Intent(this, RideTrackingService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(R.string.ride_pause), pausePending)
        } else if (session.status == RideStatus.PAUSED) {
            val resumeIntent = Intent(this, RideTrackingService::class.java).apply { action = ACTION_RESUME }
            val resumePending = PendingIntent.getService(
                this,
                2,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(R.string.ride_resume), resumePending)
        }

        return builder.build()
    }

    private fun stopTrackingService() {
        observerJob?.cancel()
        observerJob = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ride_tracking"
        const val NOTIFICATION_ID = 4202
        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_RIDE"
        const val ACTION_PAUSE = "com.example.action.PAUSE_RIDE"
        const val ACTION_RESUME = "com.example.action.RESUME_RIDE"
        const val ACTION_STOP = "com.example.action.STOP_RIDE"
        private const val WAKELOCK_TAG = "GoCycleMaps:RideTrackingWakeLock"
        private const val MAX_WAKELOCK_DURATION_MS = 6L * 60L * 60L * 1000L

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Suivi GPS des sorties",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Maintient le suivi de votre position GPS lorsque le téléphone est en veille."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun startOrUpdate(context: Context) {
            if (!RideLocationTracker.hasLocationPermission(context)) return
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}

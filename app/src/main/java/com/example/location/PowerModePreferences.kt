package com.example.location

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerMode(
    val id: String,
    val labelFr: String,
    val shortSummaryFr: String,
    val updateIntervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val minUpdateDistanceMeters: Float,
) {
    ECO(
        id = "eco",
        labelFr = "Mode Éco",
        shortSummaryFr = "Avec limitation de la batterie • GPS équilibré (5 s / 8 m)",
        updateIntervalMillis = 5000L,
        minUpdateIntervalMillis = 3000L,
        minUpdateDistanceMeters = 8f,
    ),
    STANDARD(
        id = "standard",
        labelFr = "Mode Standard",
        shortSummaryFr = "Aucune restriction de batterie • GPS haute précision (2 s / 2 m)",
        updateIntervalMillis = 2000L,
        minUpdateIntervalMillis = 1000L,
        minUpdateDistanceMeters = 2f,
    );

    companion object {
        fun fromId(id: String?): PowerMode = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

class PowerModePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _powerMode = MutableStateFlow(readPowerMode())
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()

    private fun readPowerMode(): PowerMode =
        PowerMode.fromId(prefs.getString(KEY_POWER_MODE, PowerMode.STANDARD.id))

    fun setPowerMode(mode: PowerMode) {
        prefs.edit { putString(KEY_POWER_MODE, mode.id) }
        _powerMode.value = mode
    }

    private companion object {
        const val PREFS_NAME = "gocyclemaps_power_prefs"
        const val KEY_POWER_MODE = "power_mode"
    }
}

object BatteryOptimizationHelper {
    fun isUnrestrictedBattery(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppBatterySettings(context: Context) {
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(appDetailsIntent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}

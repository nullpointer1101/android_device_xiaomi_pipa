/*
 * Copyright (C) 2026 nullpointer1101
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.pipacore.R
import org.pipacore.ui.components.*
import org.pipacore.ui.theme.*

open class StylusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipacoreTheme {
                StylusScreen(onBackPressed = { finish() })
            }
        }
    }
}

object StylusSettingsUtils {
    private const val TAG = "StylusSettingsUtils"

    const val PREF_STYLUS_MODE = "stylus_mode_key"
    const val PREF_FORCE_RECOGNIZE = "force_recognize_stylus_key"
    const val PREF_REFRESH_RATE = "stylus_refresh_rate_key"
    const val PREF_CHARGING_NOTIF = "stylus_charging_notification_key"

    const val RATE_DYNAMIC = "dynamic"
    const val RATE_60 = "60"
    const val RATE_120 = "120"

    private const val PERIPHERAL_PKG = "org.lineageos.xiaomiperipheralmanager"
    private const val STYLUS_PROP = "persist.vendor.parts.pen"
    const val ACTION_STYLUS_CHANGED = "org.lineageos.xiaomiperipheralmanager.STYLUS_MODE_CHANGED"

    fun getSharedPrefs(context: Context): SharedPreferences {
        return try {
            val peripheralContext = context.createPackageContext(
                PERIPHERAL_PKG,
                Context.CONTEXT_IGNORE_SECURITY
            )
            PreferenceManager.getDefaultSharedPreferences(peripheralContext)
        } catch (e: Exception) {
            PreferenceManager.getDefaultSharedPreferences(context)
        }
    }

    fun isStylusModeEnabled(context: Context): Boolean {
        val prop = SystemProperties.get(STYLUS_PROP, "2")
        return prop == "18" || getSharedPrefs(context).getBoolean(PREF_STYLUS_MODE, false)
    }

    fun setStylusModeEnabled(context: Context, enabled: Boolean) {
        val propVal = if (enabled) "18" else "2"
        SystemProperties.set(STYLUS_PROP, propVal)

        try {
            getSharedPrefs(context).edit().putBoolean(PREF_STYLUS_MODE, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for stylus mode", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_STYLUS_MODE, enabled).apply()

        val rate = getRefreshRate(context)
        if (enabled) {
            applyRefreshRate(context, rate)
        } else {
            restoreDefaultRefreshRate(context)
        }

        notifyTileService(context)
    }

    fun isForceRecognizeEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean(PREF_FORCE_RECOGNIZE, false)
    }

    fun setForceRecognizeEnabled(context: Context, enabled: Boolean) {
        try {
            getSharedPrefs(context).edit().putBoolean(PREF_FORCE_RECOGNIZE, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for force recognize", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_FORCE_RECOGNIZE, enabled).apply()

        notifyTileService(context)
    }

    fun getRefreshRate(context: Context): String {
        return getSharedPrefs(context).getString(PREF_REFRESH_RATE, RATE_DYNAMIC) ?: RATE_DYNAMIC
    }

    fun setRefreshRate(context: Context, rate: String) {
        try {
            getSharedPrefs(context).edit().putString(PREF_REFRESH_RATE, rate).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for refresh rate", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_REFRESH_RATE, rate).apply()

        if (isStylusModeEnabled(context)) {
            applyRefreshRate(context, rate)
        }

        notifyTileService(context)
    }

    fun applyRefreshRate(context: Context, rate: String) {
        try {
            val resolver = context.contentResolver
            when (rate) {
                RATE_60 -> {
                    Settings.System.putFloat(resolver, "min_refresh_rate", 60f)
                    Settings.System.putFloat(resolver, "peak_refresh_rate", 60f)
                    Settings.System.putFloat(resolver, "user_refresh_rate", 60f)
                }
                RATE_120 -> {
                    Settings.System.putFloat(resolver, "min_refresh_rate", 120f)
                    Settings.System.putFloat(resolver, "peak_refresh_rate", 120f)
                    Settings.System.putFloat(resolver, "user_refresh_rate", 120f)
                }
                RATE_DYNAMIC -> {
                    Settings.System.putFloat(resolver, "min_refresh_rate", 60f)
                    Settings.System.putFloat(resolver, "peak_refresh_rate", 120f)
                    Settings.System.putFloat(resolver, "user_refresh_rate", 120f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply refresh rate: $rate", e)
        }
    }

    fun restoreDefaultRefreshRate(context: Context) {
        try {
            val resolver = context.contentResolver
            Settings.System.putFloat(resolver, "min_refresh_rate", 60f)
            Settings.System.putFloat(resolver, "peak_refresh_rate", 144f)
            Settings.System.putFloat(resolver, "user_refresh_rate", 144f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore default refresh rate", e)
        }
    }

    fun isChargingNotifEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean(PREF_CHARGING_NOTIF, false)
    }

    fun setChargingNotifEnabled(context: Context, enabled: Boolean) {
        try {
            getSharedPrefs(context).edit().putBoolean(PREF_CHARGING_NOTIF, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for charging notif", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_CHARGING_NOTIF, enabled).apply()

        val serviceIntent = Intent().apply {
            component = ComponentName(PERIPHERAL_PKG, "$PERIPHERAL_PKG.PenChargingService")
        }
        try {
            if (enabled) {
                context.startService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle PenChargingService", e)
        }
    }

    fun notifyTileService(context: Context) {
        try {
            val intent = Intent(ACTION_STYLUS_CHANGED).apply {
                setPackage(PERIPHERAL_PKG)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send stylus mode broadcast", e)
        }
        try {
            TileService.requestListeningState(
                context,
                ComponentName(PERIPHERAL_PKG, "$PERIPHERAL_PKG.StylusTileService")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request tile listening state", e)
        }
    }
}

@Composable
fun StylusScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var stylusModeEnabled by remember {
        mutableStateOf(StylusSettingsUtils.isStylusModeEnabled(context))
    }
    var forceRecognizeEnabled by remember {
        mutableStateOf(StylusSettingsUtils.isForceRecognizeEnabled(context))
    }
    var selectedRefreshRate by remember {
        mutableStateOf(StylusSettingsUtils.getRefreshRate(context))
    }
    var chargingNotifEnabled by remember {
        mutableStateOf(StylusSettingsUtils.isChargingNotifEnabled(context))
    }

    // Reactively listen to SharedPreferences changes from tile or external triggers
    DisposableEffect(context) {
        val prefs = StylusSettingsUtils.getSharedPrefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                StylusSettingsUtils.PREF_STYLUS_MODE -> {
                    stylusModeEnabled = StylusSettingsUtils.isStylusModeEnabled(context)
                }
                StylusSettingsUtils.PREF_FORCE_RECOGNIZE -> {
                    forceRecognizeEnabled = StylusSettingsUtils.isForceRecognizeEnabled(context)
                }
                StylusSettingsUtils.PREF_REFRESH_RATE -> {
                    selectedRefreshRate = StylusSettingsUtils.getRefreshRate(context)
                }
                StylusSettingsUtils.PREF_CHARGING_NOTIF -> {
                    chargingNotifEnabled = StylusSettingsUtils.isChargingNotifEnabled(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    TabletContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            PipacoreTopHeader(
                title = stringResource(R.string.stylus_title),
                subtitle = stringResource(R.string.stylus_summary),
                onBackPressed = onBackPressed
            )

            // Section 1: Hardware Mode
            SectionHeader(title = "Hardware Mode")

            SettingsSwitchCard(
                title = stringResource(R.string.stylus_switch_title),
                summary = stringResource(R.string.stylus_switch_summary),
                iconRes = R.drawable.ic_stylus,
                checked = stylusModeEnabled,
                enabled = true,
                onCheckedChange = { newState ->
                    StylusSettingsUtils.setStylusModeEnabled(context, newState)
                    stylusModeEnabled = newState
                }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(SquircleCardShape),
                shape = SquircleCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.force_stylus_switch_title),
                    summary = stringResource(R.string.force_stylus_switch_summary),
                    checked = forceRecognizeEnabled,
                    enabled = stylusModeEnabled,
                    onCheckedChange = { newState ->
                        StylusSettingsUtils.setForceRecognizeEnabled(context, newState)
                        forceRecognizeEnabled = newState
                    }
                )
            }

            // Section 2: Display Refresh Rate
            SectionHeader(title = stringResource(R.string.stylus_refresh_rate_category))

            ModernSelectorCard(
                title = stringResource(R.string.refresh_rate_dynamic),
                summary = stringResource(R.string.refresh_rate_dynamic_summary),
                isEnabled = true,
                isSelected = selectedRefreshRate == StylusSettingsUtils.RATE_DYNAMIC,
                onClick = {
                    selectedRefreshRate = StylusSettingsUtils.RATE_DYNAMIC
                    StylusSettingsUtils.setRefreshRate(context, StylusSettingsUtils.RATE_DYNAMIC)
                }
            )

            ModernSelectorCard(
                title = stringResource(R.string.refresh_rate_60hz),
                summary = stringResource(R.string.refresh_rate_60hz_summary),
                isEnabled = true,
                isSelected = selectedRefreshRate == StylusSettingsUtils.RATE_60,
                onClick = {
                    selectedRefreshRate = StylusSettingsUtils.RATE_60
                    StylusSettingsUtils.setRefreshRate(context, StylusSettingsUtils.RATE_60)
                }
            )

            ModernSelectorCard(
                title = stringResource(R.string.refresh_rate_120hz),
                summary = stringResource(R.string.refresh_rate_120hz_summary),
                isEnabled = true,
                isSelected = selectedRefreshRate == StylusSettingsUtils.RATE_120,
                onClick = {
                    selectedRefreshRate = StylusSettingsUtils.RATE_120
                    StylusSettingsUtils.setRefreshRate(context, StylusSettingsUtils.RATE_120)
                }
            )

            // Section 3: Battery & Charging
            SectionHeader(title = "Battery & Charging")

            SettingsSwitchCard(
                title = stringResource(R.string.stylus_charging_notification_title),
                summary = stringResource(R.string.stylus_charging_notification_summary),
                iconRes = R.drawable.ic_bolt,
                checked = chargingNotifEnabled,
                enabled = true,
                onCheckedChange = { newState ->
                    StylusSettingsUtils.setChargingNotifEnabled(context, newState)
                    chargingNotifEnabled = newState
                }
            )

            // Section 4: Information & Notes
            WarningBanner(message = stringResource(R.string.stylus_info_footer))

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

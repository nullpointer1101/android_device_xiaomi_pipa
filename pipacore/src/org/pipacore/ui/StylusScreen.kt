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
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.SystemProperties
import android.service.quicksettings.TileService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    const val PREF_RESTORE_ON_DOCK = "stylus_restore_on_dock_key"

    const val RATE_DYNAMIC = "dynamic"
    const val RATE_60 = "60"
    const val RATE_120 = "120"

    private const val PERIPHERAL_PKG = "org.lineageos.xiaomiperipheralmanager"
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
        return getSharedPrefs(context).getBoolean(PREF_STYLUS_MODE, false)
    }

    /**
     * Writes the stylus mode preference and notifies peripheralmanager.
     * Does NOT directly set system properties or refresh rates — PenUtils
     * in peripheralmanager is the sole authority on hardware state.
     */
    fun setStylusModeEnabled(context: Context, enabled: Boolean) {
        try {
            getSharedPrefs(context).edit().putBoolean(PREF_STYLUS_MODE, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for stylus mode", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_STYLUS_MODE, enabled).apply()

        notifyPeripheralManager(context)
    }

    fun isForceRecognizeEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean(PREF_FORCE_RECOGNIZE, false)
    }

    /**
     * Writes the force recognize preference and notifies peripheralmanager.
     */
    fun setForceRecognizeEnabled(context: Context, enabled: Boolean) {
        try {
            getSharedPrefs(context).edit().putBoolean(PREF_FORCE_RECOGNIZE, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for force recognize", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_FORCE_RECOGNIZE, enabled).apply()

        notifyPeripheralManager(context)
    }

    fun getRefreshRate(context: Context): String {
        return getSharedPrefs(context).getString(PREF_REFRESH_RATE, RATE_DYNAMIC) ?: RATE_DYNAMIC
    }

    /**
     * Writes the refresh rate preference and notifies peripheralmanager.
     */
    fun setRefreshRate(context: Context, rate: String) {
        try {
            getSharedPrefs(context).edit().putString(PREF_REFRESH_RATE, rate).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for refresh rate", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREF_REFRESH_RATE, rate).apply()

        notifyPeripheralManager(context)
    }

    fun isRestoreOnDockEnabled(context: Context): Boolean {
        return getSharedPrefs(context).getBoolean(PREF_RESTORE_ON_DOCK, true)
    }

    fun setRestoreOnDockEnabled(context: Context, enabled: Boolean) {
        try {
            getSharedPrefs(context).edit().putBoolean(PREF_RESTORE_ON_DOCK, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for restore on dock", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_RESTORE_ON_DOCK, enabled).apply()

        notifyPeripheralManager(context)
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

        notifyPeripheralManager(context)
    }

    fun notifyPeripheralManager(context: Context) {
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
        try {
            val serviceIntent = Intent().apply {
                component = ComponentName(PERIPHERAL_PKG, "$PERIPHERAL_PKG.PenChargingService")
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ping PenChargingService", e)
        }
    }

    fun isPenConnected(context: Context): Boolean {
        return try {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return false
            inputManager.inputDeviceIds.any { id ->
                val device = inputManager.getInputDevice(id)
                device != null && device.vendorId == 6421 && device.productId == 19841
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isPenHardwareActive(): Boolean {
        return try {
            SystemProperties.get("persist.vendor.parts.pen", "2") == "18"
        } catch (e: Exception) {
            false
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
    var restoreOnDockEnabled by remember {
        mutableStateOf(StylusSettingsUtils.isRestoreOnDockEnabled(context))
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
                StylusSettingsUtils.PREF_RESTORE_ON_DOCK -> {
                    restoreOnDockEnabled = StylusSettingsUtils.isRestoreOnDockEnabled(context)
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

            // Section 1: Master Hardware Mode Switch
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

            // Options only active when Stylus Mode is ON
            if (stylusModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Pen Type / Mode (Option B: Selector Cards)
                SectionHeader(title = stringResource(R.string.stylus_mode_type_category))

                ModernSelectorCard(
                    title = stringResource(R.string.stylus_mode_official_title),
                    summary = stringResource(R.string.stylus_mode_official_summary),
                    iconRes = R.drawable.ic_stylus,
                    isEnabled = true,
                    isSelected = !forceRecognizeEnabled,
                    onClick = {
                        StylusSettingsUtils.setForceRecognizeEnabled(context, false)
                        forceRecognizeEnabled = false
                    }
                )

                // Sub-switch: Restore on Dock (Only for Official Smart Pen)
                if (!forceRecognizeEnabled) {
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
                            title = stringResource(R.string.restore_on_dock_title),
                            summary = stringResource(R.string.restore_on_dock_summary),
                            checked = restoreOnDockEnabled,
                            enabled = true,
                            onCheckedChange = { newState ->
                                StylusSettingsUtils.setRestoreOnDockEnabled(context, newState)
                                restoreOnDockEnabled = newState
                            }
                        )
                    }
                }

                ModernSelectorCard(
                    title = stringResource(R.string.stylus_mode_force_title),
                    summary = stringResource(R.string.stylus_mode_force_summary),
                    iconRes = R.drawable.ic_bolt,
                    isEnabled = true,
                    isSelected = forceRecognizeEnabled,
                    onClick = {
                        StylusSettingsUtils.setForceRecognizeEnabled(context, true)
                        forceRecognizeEnabled = true
                    }
                )

                // Section 3: Display Refresh Rate
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

                // Section 4: Battery & Charging
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
            }

            // Section 5: Live Status Card
            SectionHeader(title = stringResource(R.string.stylus_status_category))
            StylusStatusCard(
                stylusModeEnabled = stylusModeEnabled,
                forceRecognizeEnabled = forceRecognizeEnabled,
                refreshRate = selectedRefreshRate,
                context = context
            )

            // Section 6: Information & Notes
            WarningBanner(message = stringResource(R.string.stylus_info_footer))

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StylusStatusCard(
    stylusModeEnabled: Boolean,
    forceRecognizeEnabled: Boolean,
    refreshRate: String,
    context: Context
) {
    val isPenConnected = remember(stylusModeEnabled, forceRecognizeEnabled) {
        StylusSettingsUtils.isPenConnected(context)
    }
    val isPenHardwareActive = remember(stylusModeEnabled, forceRecognizeEnabled) {
        StylusSettingsUtils.isPenHardwareActive()
    }

    val rateLabel = when (refreshRate) {
        StylusSettingsUtils.RATE_60 -> "60Hz"
        StylusSettingsUtils.RATE_120 -> "120Hz"
        else -> "Dynamic (60-120Hz)"
    }

    val title: String
    val summary: String
    val iconRes: Int
    val tintColor: Color

    if (!stylusModeEnabled) {
        title = stringResource(R.string.stylus_status_disabled_title)
        summary = stringResource(R.string.stylus_status_disabled_desc)
        iconRes = R.drawable.ic_stylus
        tintColor = MaterialTheme.colorScheme.onSurfaceVariant
    } else if (forceRecognizeEnabled) {
        title = stringResource(R.string.stylus_status_force_title)
        summary = stringResource(R.string.stylus_status_force_desc, rateLabel)
        iconRes = R.drawable.ic_bolt
        tintColor = MaterialTheme.colorScheme.primary
    } else if (!isPenConnected) {
        title = stringResource(R.string.stylus_status_disconnected_title)
        summary = stringResource(R.string.stylus_status_disconnected_desc)
        iconRes = R.drawable.ic_stylus
        tintColor = MaterialTheme.colorScheme.onSurfaceVariant
    } else if (isPenHardwareActive) {
        title = stringResource(R.string.stylus_status_active_title)
        summary = stringResource(R.string.stylus_status_active_desc, rateLabel)
        iconRes = R.drawable.ic_stylus
        tintColor = MaterialTheme.colorScheme.primary
    } else {
        title = stringResource(R.string.stylus_status_docked_title)
        summary = stringResource(R.string.stylus_status_docked_desc)
        iconRes = R.drawable.ic_bolt
        tintColor = MaterialTheme.colorScheme.secondary
    }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(SquircleIconShape)
                    .background(tintColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = tintColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

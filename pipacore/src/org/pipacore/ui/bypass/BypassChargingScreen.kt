/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore.ui.bypass

import android.content.ComponentName
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.pipacore.BypassChargingTileService
import org.pipacore.BypassChargingUtils
import org.pipacore.R
import org.pipacore.ui.components.*
import org.pipacore.ui.theme.*

@Composable
fun BypassChargingScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isSupported = remember { BypassChargingUtils.isSupported() }
    var isEnabled by remember { mutableStateOf(BypassChargingUtils.getCurrentHardwareState()) }

    // Synchronize reactively with QS Tile & external preference changes
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == BypassChargingUtils.KEY_BYPASS_CHARGING) {
                isEnabled = BypassChargingUtils.getCurrentHardwareState()
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
                title = stringResource(R.string.bypass_charging_title),
                subtitle = stringResource(R.string.bypass_charging_summary),
                onBackPressed = onBackPressed
            )

            SectionHeader(title = "Power Delivery")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleCardShape),
                shape = SquircleCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsSwitchRow(
                    title = stringResource(R.string.bypass_charging_title),
                    summary = if (!isSupported) stringResource(R.string.bypass_charging_not_supported)
                              else if (isEnabled) stringResource(R.string.bypass_charging_status_on)
                              else stringResource(R.string.bypass_charging_status_off),
                    checked = isEnabled,
                    enabled = isSupported,
                    onCheckedChange = { newState ->
                        BypassChargingUtils.setEnabled(newState)
                        isEnabled = newState

                        // Persist preference and notify QS Tile
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        prefs.edit().putBoolean(BypassChargingUtils.KEY_BYPASS_CHARGING, newState).apply()
                        TileService.requestListeningState(
                            context,
                            ComponentName(context, BypassChargingTileService::class.java)
                        )
                    }
                )
            }

            SectionHeader(title = "Information")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleCardShape),
                shape = SquircleCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.bypass_charging_footer),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    ),
                    modifier = Modifier.padding(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

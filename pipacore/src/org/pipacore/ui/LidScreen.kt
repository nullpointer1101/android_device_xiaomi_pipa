/*
 * Copyright (C) 2026 nullpointer1101
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

open class LidActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipacoreTheme {
                LidScreen(onBackPressed = { finish() })
            }
        }
    }
}

object LidSettingsUtils {
    private const val TAG = "LidSettingsUtils"

    const val PREF_LID_BEHAVIOR = "lid_switch_key"
    const val PREF_LID_WAKE = "lid_wake_key"

    fun isLidBehaviorEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "lid_behavior", 1) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error reading lid_behavior", e)
            true
        }
    }

    fun setLidBehaviorEnabled(context: Context, enabled: Boolean) {
        try {
            Settings.Global.putInt(context.contentResolver, "lid_behavior", if (enabled) 1 else 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing lid_behavior", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_LID_BEHAVIOR, enabled).apply()
    }

    fun isLidWakeEnabled(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, "lid_wake", 1) == 1
        } catch (e: Exception) {
            Log.e(TAG, "Error reading lid_wake", e)
            true
        }
    }

    fun setLidWakeEnabled(context: Context, enabled: Boolean) {
        try {
            Settings.System.putInt(context.contentResolver, "lid_wake", if (enabled) 1 else 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing lid_wake", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_LID_WAKE, enabled).apply()
    }
}

@Composable
fun LidScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var lidBehaviorEnabled by remember {
        mutableStateOf(LidSettingsUtils.isLidBehaviorEnabled(context))
    }
    var lidWakeEnabled by remember {
        mutableStateOf(LidSettingsUtils.isLidWakeEnabled(context))
    }

    TabletContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            PipacoreTopHeader(
                title = stringResource(R.string.lid_title),
                subtitle = stringResource(R.string.lid_summary),
                onBackPressed = onBackPressed
            )

            // Section 1: Folio & Sensor Controls
            SectionHeader(title = "Smart Cover Controls")

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
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.lid_switch_title),
                        summary = stringResource(R.string.lid_switch_summary),
                        checked = lidBehaviorEnabled,
                        enabled = true,
                        onCheckedChange = { newState ->
                            LidSettingsUtils.setLidBehaviorEnabled(context, newState)
                            lidBehaviorEnabled = newState
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.lid_wake_title),
                        summary = stringResource(R.string.lid_wake_summary),
                        checked = lidWakeEnabled,
                        enabled = lidBehaviorEnabled,
                        onCheckedChange = { newState ->
                            LidSettingsUtils.setLidWakeEnabled(context, newState)
                            lidWakeEnabled = newState
                        }
                    )
                }
            }

            // Section 2: Informative card
            SectionHeader(title = "Information")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(SquircleCardShape),
                shape = SquircleCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.lid_info_footer),
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

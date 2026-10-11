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
import android.os.SystemProperties
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

open class KeyboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipacoreTheme {
                KeyboardScreen(onBackPressed = { finish() })
            }
        }
    }
}

object KeyboardSettingsUtils {
    private const val TAG = "KeyboardSettingsUtils"

    const val PREF_KEYBOARD_MODE = "keyboard_mode_key"
    private const val KEYBOARD_PROP = "persist.vendor.parts.keyboard"
    private const val PERIPHERAL_PKG = "org.lineageos.xiaomiperipheralmanager"

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

    fun isKeyboardMonitoringEnabled(context: Context): Boolean {
        val prop = SystemProperties.get(KEYBOARD_PROP, "0")
        return prop == "1" || getSharedPrefs(context).getBoolean(PREF_KEYBOARD_MODE, false)
    }

    fun setKeyboardMonitoringEnabled(context: Context, enabled: Boolean) {
        val propVal = if (enabled) "1" else "0"
        SystemProperties.set(KEYBOARD_PROP, propVal)

        try {
            getSharedPrefs(context).edit().putBoolean(PREF_KEYBOARD_MODE, enabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write peripheral prefs for keyboard", e)
        }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean(PREF_KEYBOARD_MODE, enabled).apply()
    }
}

@Composable
fun KeyboardScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var keyboardMonitoringEnabled by remember {
        mutableStateOf(KeyboardSettingsUtils.isKeyboardMonitoringEnabled(context))
    }

    DisposableEffect(context) {
        val prefs = KeyboardSettingsUtils.getSharedPrefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KeyboardSettingsUtils.PREF_KEYBOARD_MODE) {
                keyboardMonitoringEnabled = KeyboardSettingsUtils.isKeyboardMonitoringEnabled(context)
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
                title = stringResource(R.string.keyboard_title),
                subtitle = stringResource(R.string.keyboard_summary),
                onBackPressed = onBackPressed
            )

            // Section 1: Dock & Hardware Service
            SectionHeader(title = "Hardware Service")

            SettingsSwitchCard(
                title = stringResource(R.string.keyboard_switch_title),
                summary = stringResource(R.string.keyboard_switch_summary),
                iconRes = R.drawable.ic_keyboard,
                checked = keyboardMonitoringEnabled,
                enabled = true,
                onCheckedChange = { newState ->
                    KeyboardSettingsUtils.setKeyboardMonitoringEnabled(context, newState)
                    keyboardMonitoringEnabled = newState
                }
            )

            // Section 2: Informative card describing magnetic pogo-pin connection
            SectionHeader(title = "Connection Details")

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
                    text = stringResource(R.string.keyboard_info_footer),
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

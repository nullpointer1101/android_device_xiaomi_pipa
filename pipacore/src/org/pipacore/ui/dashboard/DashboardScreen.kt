/*
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pipacore.*
import org.pipacore.ui.components.*

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    TabletContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            PipacoreTopHeader(
                title = stringResource(R.string.dashboard_title),
                subtitle = stringResource(R.string.dashboard_subtitle)
            )

            SectionHeader(title = "Accessories")

            // Xiaomi Smart Pen
            SettingsNavCard(
                title = stringResource(R.string.stylus_title),
                summary = stringResource(R.string.stylus_summary),
                iconRes = R.drawable.ic_stylus,
                onClick = {
                    context.startActivity(Intent(context, StylusActivity::class.java))
                }
            )

            // Xiaomi Keyboard
            SettingsNavCard(
                title = stringResource(R.string.keyboard_title),
                summary = stringResource(R.string.keyboard_summary),
                iconRes = R.drawable.ic_keyboard,
                onClick = {
                    context.startActivity(Intent(context, KeyboardActivity::class.java))
                }
            )

            // Smart Cover (Lid)
            SettingsNavCard(
                title = stringResource(R.string.lid_title),
                summary = stringResource(R.string.lid_summary),
                iconRes = R.drawable.ic_lid,
                onClick = {
                    context.startActivity(Intent(context, LidActivity::class.java))
                }
            )

            SectionHeader(title = "Hardware Tuning")

            // Core Control
            SettingsNavCard(
                title = stringResource(R.string.core_control_title),
                summary = stringResource(R.string.core_control_summary),
                iconRes = R.drawable.ic_cpu,
                onClick = {
                    context.startActivity(Intent(context, CoreControlActivity::class.java))
                }
            )

            // Thermal Profiles
            SettingsNavCard(
                title = stringResource(R.string.thermal_profile_title),
                summary = stringResource(R.string.thermal_profile_summary),
                iconRes = R.drawable.ic_thermal_snowflake,
                onClick = {
                    context.startActivity(Intent(context, ThermalProfileActivity::class.java))
                }
            )

            SectionHeader(title = "Power & Battery")

            // Bypass Charging
            SettingsNavCard(
                title = stringResource(R.string.bypass_charging_title),
                summary = stringResource(R.string.bypass_charging_summary),
                iconRes = R.drawable.ic_bolt,
                onClick = {
                    context.startActivity(Intent(context, BypassChargingActivity::class.java))
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

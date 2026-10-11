/*
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED == action ||
            "android.intent.action.QUICKBOOT_POWERON" == action) {
            Log.i(TAG, "Boot completed, restoring PipaCore settings...")
            CoreControlUtils.restoreCoreStates(context)
            ThermalManager.restoreProfile(context)
        }
    }

    companion object {
        private const val TAG = "PipaCoreBootReceiver"
    }
}

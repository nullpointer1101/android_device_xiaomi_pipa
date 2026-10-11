/*
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager

object CoreControlUtils {

    private const val TAG = "CoreControlUtils"

    const val PREF_KEY_PREFIX = "core_online_"

    // Snapdragon 870 (1-3-4 architecture)
    val EFFICIENCY_CORES = listOf(0, 1, 2, 3)
    val PERFORMANCE_CORES = listOf(4, 5, 6)
    val PRIME_CORES = listOf(7)
    val ALL_CORES = listOf(0, 1, 2, 3, 4, 5, 6, 7)

    // Safety locked cores (must ALWAYS remain ON)
    val LOCKED_CORES = setOf(0, 1, 4)

    private fun getCpuOnlinePath(core: Int): String {
        return "/sys/devices/system/cpu/cpu$core/online"
    }

    /**
     * Checks if a core is safety locked to 'Always Active' (ON)
     * CPU0 (Boot Core / Kernel forced), CPU1 (Efficiency min guard), CPU4 (Performance min guard)
     */
    fun isCoreLocked(core: Int): Boolean {
        return LOCKED_CORES.contains(core)
    }

    /**
     * Checks if a CPU core is currently online
     */
    fun isCoreOnline(core: Int): Boolean {
        if (core == 0) return true // CPU0 is always online by kernel architecture
        val path = getCpuOnlinePath(core)
        if (!FileUtils.fileExists(path)) {
            return true // Default to online if node is absent
        }
        val value = FileUtils.readOneLine(path)
        return value?.trim() == "1"
    }

    /**
     * Safely sets a CPU core online or offline
     * Enforces all safety constraints before writing
     */
    fun setCoreOnline(core: Int, online: Boolean): Boolean {
        if (core == 0) {
            Log.w(TAG, "CPU0 cannot be toggled by hardware design")
            return false
        }

        // Safety Rule 2 & 3: Refuse to disable locked cores (CPU1, CPU4)
        if (isCoreLocked(core) && !online) {
            Log.w(TAG, "Refusing to disable safety-locked core: CPU$core")
            return false
        }

        // Safety Rule 1: Validate cluster bounds
        if (!online) {
            val currentOnline = ALL_CORES.filter { if (it == core) false else isCoreOnline(it) }
            if (currentOnline.isEmpty()) {
                Log.e(TAG, "Safety violation: Cannot disable all cores")
                return false
            }
        }

        val path = getCpuOnlinePath(core)
        val value = if (online) "1" else "0"
        val success = FileUtils.writeLine(path, value)
        if (!success) {
            Log.e(TAG, "Failed to write $value to $path")
            return false
        }
        return true
    }

    /**
     * Saves user's core preference
     */
    fun saveCoreState(context: Context, core: Int, online: Boolean) {
        if (isCoreLocked(core)) return // Do not persist changes for locked cores
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("$PREF_KEY_PREFIX$core", online).apply()
    }

    /**
     * Restores core states on boot while strictly honoring safety locks
     */
    fun restoreCoreStates(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        for (core in ALL_CORES) {
            if (isCoreLocked(core)) {
                // Ensure locked cores are online
                if (core != 0) {
                    setCoreOnline(core, true)
                }
                continue
            }
            val shouldBeOnline = prefs.getBoolean("$PREF_KEY_PREFIX$core", true)
            setCoreOnline(core, shouldBeOnline)
        }
        Log.i(TAG, "Restored CPU core states successfully")
    }
}

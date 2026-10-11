/*
 * Copyright (C) 2026 MufasaXz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pipacore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.util.Log;
import androidx.preference.PreferenceManager;

public final class ThermalManager {

    private static final String TAG = "ThermalManager";

    public static final String PREF_KEY_ENABLED = "pipacore_thermal_enabled";
    public static final String PREF_KEY_PROFILE = "pipacore_thermal_profile";
    public static final String PROP_PIPACORE_PROFILE = "sys.pipacore.profile";
    public static final String SCONFIG_PATH = "/sys/class/thermal/thermal_message/sconfig";

    // 3 Strict Modes
    public static final int PROFILE_BALANCED = 0;
    public static final int PROFILE_BATTERY = 1;
    public static final int PROFILE_PERFORMANCE = 10;

    private ThermalManager() {}

    /**
     * Check if thermal profile management is enabled (Default is OFF / false)
     */
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_KEY_ENABLED, false);
    }

    /**
     * Enable or disable custom thermal profile management
     */
    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_KEY_ENABLED, enabled).apply();

        if (enabled) {
            applyProfile(getProfile(context));
        } else {
            // Revert back to default system thermal mode (0)
            applyProfile(PROFILE_BALANCED);
        }
    }

    /**
     * Get current active profile from SharedPreferences with system property fallback
     */
    public static int getProfile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int defaultVal = PROFILE_BALANCED;
        try {
            String prop = SystemProperties.get(PROP_PIPACORE_PROFILE, "0");
            defaultVal = Integer.parseInt(prop.trim());
        } catch (Exception ignored) {}
        return prefs.getInt(PREF_KEY_PROFILE, defaultVal);
    }

    /**
     * Set active thermal profile across SharedPreferences, sconfig sysfs node, and system property
     */
    public static void setProfile(Context context, int profile) {
        // 1. Update shared SharedPreferences value
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putInt(PREF_KEY_PROFILE, profile).apply();

        // 2. Apply backend hardware & system property changes if master toggle is enabled
        if (isEnabled(context)) {
            applyProfile(profile);
        }
    }

    /**
     * Apply profile to hardware sconfig node and system property
     */
    public static void applyProfile(int profile) {
        final String profileVal = String.valueOf(profile);

        // a) Write integer directly to sconfig thermal node
        writeSconfig(profileVal);

        // b) Set custom system property to trigger backend init script bridge
        try {
            SystemProperties.set(PROP_PIPACORE_PROFILE, profileVal);
            Log.i(TAG, "Applied profile " + profileVal + " -> " + PROP_PIPACORE_PROFILE + "=" + profileVal);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set SystemProperties: " + PROP_PIPACORE_PROFILE + "=" + profileVal, e);
        }
    }

    private static void writeSconfig(String value) {
        if (FileUtils.fileExists(SCONFIG_PATH)) {
            FileUtils.writeLine(SCONFIG_PATH, value);
        }
    }

    /**
     * Restores saved profile on device boot
     */
    public static void restoreProfile(Context context) {
        if (isEnabled(context)) {
            int currentProfile = getProfile(context);
            applyProfile(currentProfile);
        } else {
            applyProfile(PROFILE_BALANCED);
        }
    }
}

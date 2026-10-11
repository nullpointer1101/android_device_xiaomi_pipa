/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for lid configuration
 * Allows users to manually enable/disable the smart cover
 */
public class LidSettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "XiaomiLidSettings";
    private static final String LID_KEY = "lid_switch_key";

    private SharedPreferences mLidPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.lid_settings);

            mLidPreference = PreferenceManager.getDefaultSharedPreferences(getContext());
            SwitchPreference switchPreference = (SwitchPreference) findPreference(LID_KEY);

            if (switchPreference != null) {
                switchPreference.setChecked(mLidPreference.getBoolean(LID_KEY, true));
                switchPreference.setEnabled(true);
            } else {
                logError("Could not find lid switch preference");
            }
            
            logInfo("Lid settings fragment created");
        } catch (Exception e) {
            logError("Error creating lid settings: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mLidPreference.registerOnSharedPreferenceChangeListener(this);
        } catch (Exception e) {
            logError("Error in onResume: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            mLidPreference.unregisterOnSharedPreferenceChangeListener(this);
        } catch (Exception e) {
            logError("Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreference, String key) {
        if (LID_KEY.equals(key)) {
            try {
                boolean newStatus = mLidPreference.getBoolean(key, true);
                logInfo("Lid preference changed to: " + newStatus);
                Settings.Global.putInt(getActivity().getContentResolver(),
                                      "lid_behavior", newStatus ? 1 : 0);
            } catch (Exception e) {
                logError("Error handling preference change: " + e.getMessage());
            }
        }
    }
    
    private void logInfo(String message) {
        Log.i(TAG, getTimestamp() + message);
    }
    
    private void logError(String message) {
        Log.e(TAG, getTimestamp() + message);
    }
    
    private String getTimestamp() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}

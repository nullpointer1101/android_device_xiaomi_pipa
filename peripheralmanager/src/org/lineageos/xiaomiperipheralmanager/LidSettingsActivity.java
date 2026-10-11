/*
 * Copyright (C) 2023-2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.os.Bundle;
import android.util.Log;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Settings activity for smart cover/lid configuration
 * Hosts the LidSettingsFragment for user configuration
 */
public class LidSettingsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG = "XiaomiLidSettings";
    private static final String TAG_LID = "lid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.i(TAG, "Opening lid settings");
        
        getSupportFragmentManager().beginTransaction().replace(
            com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                new LidSettingsFragment(), TAG_LID).commit();
    }
}

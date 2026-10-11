/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Trampoline activity for tile preferences that redirects to PipaCore's StylusActivity.
 */
public class StylusSettingsActivity extends Activity {

    private static final String TAG = "XiaomiStylusRedirect";
    private static final ComponentName PIPACORE_STYLUS =
            new ComponentName("org.pipacore", "org.pipacore.StylusActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Redirecting tile preferences to PipaCore StylusActivity");
        Intent intent = new Intent();
        intent.setComponent(PIPACORE_STYLUS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch PipaCore StylusActivity", e);
        }
        finish();
    }
}

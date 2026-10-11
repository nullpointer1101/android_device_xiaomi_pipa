/*
 * Copyright (C) 2023-2026 The LineageOS Project
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
 * Trampoline activity that redirects to PipaCore's LidActivity.
 */
public class LidSettingsActivity extends Activity {

    private static final String TAG = "XiaomiLidRedirect";
    private static final ComponentName PIPACORE_LID =
            new ComponentName("org.pipacore", "org.pipacore.LidActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Redirecting to PipaCore LidActivity");
        Intent intent = new Intent();
        intent.setComponent(PIPACORE_LID);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch PipaCore LidActivity", e);
        }
        finish();
    }
}

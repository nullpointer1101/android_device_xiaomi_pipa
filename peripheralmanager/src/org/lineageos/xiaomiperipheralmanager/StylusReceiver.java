/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Manifest receiver that listens for STYLUS_MODE_CHANGED broadcasts.
 * Synchronizes hardware state and PenChargingService when settings change.
 */
public class StylusReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiStylusReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Stylus settings change broadcast received");
        if (!PenUtils.isSetup()) {
            PenUtils.setup(context);
        } else {
            PenUtils.onSettingsChanged();
        }

        if (PenChargingService.shouldServiceRun(context)) {
            PenChargingService.start(context);
        } else {
            PenChargingService.stop(context);
        }
    }
}

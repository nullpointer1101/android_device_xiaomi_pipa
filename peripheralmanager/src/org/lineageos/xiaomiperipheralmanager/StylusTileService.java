/*
 * Copyright (C) 2025-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

/**
 * Quick Settings tile for Stylus Mode.
 */
public class StylusTileService extends TileService {

    private static final String TAG = "XiaomiStylusTile";

    static final String ACTION_STYLUS_CHANGED =
            "org.lineageos.xiaomiperipheralmanager.STYLUS_MODE_CHANGED";

    private static final ComponentName PIPACORE_STYLUS_COMPONENT =
            new ComponentName("org.pipacore", "org.pipacore.StylusActivity");

    private static final String STYLUS_MODE_KEY         = "stylus_mode_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean isStylusActive() {
        return "18".equals(SystemProperties.get("persist.vendor.parts.pen", "2"))
                || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(STYLUS_MODE_KEY, false);
    }

    /**
     * Reads prefs/props at execution time — never at scheduling time — to avoid stale state.
     */
    private final Runnable mDelayedUpdateRunnable = () -> {
        boolean current = isStylusActive();
        Log.d(TAG, "Delayed retry — stylus=" + current);
        updateTile(current);
    };

    // -----------------------------------------------------------------------
    // Sync receiver — updates tile while QS panel is open
    // -----------------------------------------------------------------------

    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_STYLUS_CHANGED.equals(intent.getAction())) return;
            boolean active = isStylusActive();
            Log.d(TAG, "Broadcast received — stylus=" + active);
            updateTile(active);
        }
    };

    // -----------------------------------------------------------------------
    // TileService lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onStartListening() {
        super.onStartListening();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mSettingsReceiver, new IntentFilter(ACTION_STYLUS_CHANGED));
        try {
            registerReceiver(mSettingsReceiver, new IntentFilter(ACTION_STYLUS_CHANGED), Context.RECEIVER_EXPORTED);
        } catch (Exception ignored) {}

        boolean active = isStylusActive();
        Log.d(TAG, "onStartListening — stylus=" + active
                + ", tile=" + (getQsTile() != null ? "ok" : "null"));

        updateTile(active);

        // 350 ms retry for the cold-boot binder window where getQsTile() is
        // transiently null. Lambda reads current prefs at execution time.
        mHandler.removeCallbacks(mDelayedUpdateRunnable);
        mHandler.postDelayed(mDelayedUpdateRunnable, 350);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mHandler.removeCallbacks(mDelayedUpdateRunnable);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSettingsReceiver);
        try {
            unregisterReceiver(mSettingsReceiver);
        } catch (Exception ignored) {}
    }

    /**
     * Launches PipaCore's StylusActivity for tile preferences.
     */
    public void launchStylusSettings() {
        Intent intent = new Intent();
        intent.setComponent(PIPACORE_STYLUS_COMPONENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }

    // -----------------------------------------------------------------------
    // User interaction
    // -----------------------------------------------------------------------

    @Override
    public void onClick() {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "onClick — tile is null");
            return;
        }

        final boolean newState = (tile.getState() != Tile.STATE_ACTIVE);
        Log.d(TAG, "onClick — newState=" + newState);

        // Immediate visual feedback before any I/O.
        updateTile(newState);

        final String refreshRate = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(STYLUS_REFRESH_RATE_KEY, "dynamic");

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(STYLUS_MODE_KEY, newState)
                .apply();

        PenUtils.onStylusModeChanged(newState);
        if (newState) PenUtils.setRefreshRateMode(refreshRate);

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_STYLUS_CHANGED));
    }

    // -----------------------------------------------------------------------
    // Tile rendering
    // -----------------------------------------------------------------------

    /**
     * Applies state, icon, and refresh-rate subtitle to the tile.
     * Reads refresh rate from prefs at call time so the subtitle stays correct
     * after a refresh-rate change even without a full tile rebind.
     */
    private void updateTile(boolean active) {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "updateTile — tile is null");
            return;
        }

        final String rate = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(STYLUS_REFRESH_RATE_KEY, "dynamic");

        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this,
                active ? R.drawable.ic_stylus_tile
                       : R.drawable.ic_stylus_tile_off));
        tile.setSubtitle(rateLabel(rate));
        tile.updateTile();
    }

    private static String rateLabel(String rate) {
        switch (rate) {
            case "60":  return "60 Hz";
            case "120": return "120 Hz";
            default:    return "Dynamic";
        }
    }
}

/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for stylus/pen configuration.
 */
public class StylusSettingsFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "XiaomiStylusSettings";

    private static final String STYLUS_MODE_KEY         = "stylus_mode_key";
    private static final String FORCE_RECOGNIZE_KEY     = "force_recognize_stylus_key";
    private static final String FOOTER_KEY              = "footer_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private static final String RATE_DYNAMIC = "dynamic";
    private static final String RATE_60      = "60";
    private static final String RATE_120     = "120";

    private SharedPreferences    mPrefs;
    private MainSwitchPreference mStylusModePref;
    private SwitchPreference     mForceRecognizePref;
    private ListPreference       mRefreshRatePref;
    private SwitchPreferenceCompat mNotifPref;
    private FooterPreference     mFooterPref;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Per-key re-entry guard
    private String mHandlingKey = null;

    // -----------------------------------------------------------------------
    // Tile sync receiver — keeps fragment in sync when QS tile is toggled
    // -----------------------------------------------------------------------

    private final BroadcastReceiver mTileChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logDebug("Tile-change broadcast — refreshing UI");
            mHandler.post(() -> refreshUI());
        }
    };

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.stylus_settings);

        mPrefs              = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mStylusModePref     = findPreference(STYLUS_MODE_KEY);
        mForceRecognizePref = findPreference(FORCE_RECOGNIZE_KEY);
        mRefreshRatePref    = findPreference(STYLUS_REFRESH_RATE_KEY);
        mNotifPref          = findPreference(PenChargingService.PREF_STYLUS_CHARGING_NOTIF);
        mFooterPref         = findPreference(FOOTER_KEY);

        if (mNotifPref != null) {
            mNotifPref.setChecked(
                    mPrefs.getBoolean(PenChargingService.PREF_STYLUS_CHARGING_NOTIF, false));
        }

        refreshUI();
        logInfo("Stylus settings created");
    }

    @Override
    public void onResume() {
        super.onResume();
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                mTileChangeReceiver,
                new IntentFilter(StylusTileService.ACTION_STYLUS_CHANGED));
        refreshUI();
        logInfo("Stylus settings resumed");
    }

    @Override
    public void onPause() {
        super.onPause();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        try {
            LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(mTileChangeReceiver);
        } catch (IllegalArgumentException ignored) { /* not registered */ }
        logInfo("Stylus settings paused");
    }

    // -----------------------------------------------------------------------
    // SharedPreferences callback
    // -----------------------------------------------------------------------

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key != null && key.equals(mHandlingKey)) {
            logDebug("Skipping guarded callback for key: " + key);
            return;
        }

        mHandlingKey = key;
        try {
            if (STYLUS_MODE_KEY.equals(key)) {
                boolean enabled = prefs.getBoolean(key, false);
                logInfo("Stylus mode → " + enabled);
                PenUtils.onStylusModeChanged(enabled);
                if (PenChargingService.shouldServiceRun(requireContext())) {
                    PenChargingService.start(requireContext());
                } else {
                    PenChargingService.stop(requireContext());
                }
                notifyTile();

            } else if (FORCE_RECOGNIZE_KEY.equals(key)) {
                boolean enabled = prefs.getBoolean(key, false);
                logInfo("Force recognize → " + enabled);
                PenUtils.onForceRecognizeChanged(enabled);
                if (PenChargingService.shouldServiceRun(requireContext())) {
                    PenChargingService.start(requireContext());
                } else {
                    PenChargingService.stop(requireContext());
                }
                notifyTile();

            } else if ("stylus_restore_on_dock_key".equals(key)) {
                boolean enabled = prefs.getBoolean(key, true);
                logInfo("Restore on dock → " + enabled);
                PenUtils.onRestoreOnDockChanged(enabled);

            } else if (STYLUS_REFRESH_RATE_KEY.equals(key)) {
                String rate = prefs.getString(key, RATE_DYNAMIC);
                logInfo("Refresh rate → " + rate);
                PenUtils.setRefreshRateMode(rate);
                notifyTile();

            } else if (PenChargingService.PREF_STYLUS_CHARGING_NOTIF.equals(key)) {
                boolean enabled = prefs.getBoolean(key, false);
                logInfo("Stylus charging notification → " + enabled);
                if (PenChargingService.shouldServiceRun(requireContext())) {
                    PenChargingService.start(requireContext());
                } else {
                    PenChargingService.stop(requireContext());
                }
            }

            refreshUI();

        } finally {
            mHandler.post(() -> {
                if (key != null && key.equals(mHandlingKey)) mHandlingKey = null;
            });
        }
    }

    // -----------------------------------------------------------------------
    // Tile notification — dual channel
    // -----------------------------------------------------------------------

    /**
     * LocalBroadcast: instant update if QS panel is currently open.
     * requestListeningState(): forces onStartListening() if QS is closed so the tile
     * re-reads prefs and updates icon/subtitle without the user needing to open QS.
     */
    private void notifyTile() {
        LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(new Intent(StylusTileService.ACTION_STYLUS_CHANGED));
        TileService.requestListeningState(requireContext(),
                new ComponentName(requireContext(), StylusTileService.class));
    }

    // -----------------------------------------------------------------------
    // UI refresh
    // -----------------------------------------------------------------------

    private void refreshUI() {
        final boolean stylusMode  = mPrefs.getBoolean(STYLUS_MODE_KEY, false);
        final boolean forceRecog  = mPrefs.getBoolean(FORCE_RECOGNIZE_KEY, false);
        final String  refreshRate = mPrefs.getString(STYLUS_REFRESH_RATE_KEY, RATE_DYNAMIC);
        final boolean notifEnabled = mPrefs.getBoolean(
                PenChargingService.PREF_STYLUS_CHARGING_NOTIF, false);

        if (mStylusModePref != null && mStylusModePref.isChecked() != stylusMode) {
            mStylusModePref.setChecked(stylusMode);
        }
        if (mForceRecognizePref != null && mForceRecognizePref.isChecked() != forceRecog) {
            mForceRecognizePref.setChecked(forceRecog);
        }
        if (mNotifPref != null && mNotifPref.isChecked() != notifEnabled) {
            mNotifPref.setChecked(notifEnabled);
        }
        if (mRefreshRatePref != null) {
            // Guard setValue(): calling it unconditionally triggers notifyChanged() →
            // RecyclerView item-change animation even when the value hasn't changed.
            String current = mRefreshRatePref.getValue();
            if (current == null || !current.equals(refreshRate)) {
                mRefreshRatePref.setValue(refreshRate);
            }
        }

        updateFooter(stylusMode, forceRecog, refreshRate);
    }

    private void updateFooter(boolean stylusMode, boolean forceRecog, String refreshRate) {
        if (mFooterPref == null) return;

        final boolean penActive    = PenUtils.isPenModeEnabled();
        final boolean penConnected = PenUtils.isPenConnected();

        final String statusText;
        if (!stylusMode) {
            statusText = getString(R.string.stylus_status_inactive) + " (Off)";
        } else if (penConnected) {
            statusText = getString(R.string.stylus_status_active) + " (Pen Connected)";
        } else if (forceRecog) {
            statusText = getString(R.string.stylus_status_active) + " (Force Mode)";
        } else {
            statusText = getString(R.string.stylus_status_inactive) + " (No Pen)";
        }

        mFooterPref.setTitle(
                getString(R.string.stylus_more_info)
                        + "\n\n"
                        + getString(R.string.stylus_footer_status, statusText)
                        + "\n"
                        + getString(R.string.stylus_footer_refresh_rate,
                                getRateLabel(refreshRate)
                                + " (" + (penActive ? "Applied" : "Saved") + ")"));
    }

    private String getRateLabel(String value) {
        switch (value) {
            case RATE_60:  return getString(R.string.refresh_rate_60hz);
            case RATE_120: return getString(R.string.refresh_rate_120hz);
            default:       return getString(R.string.refresh_rate_dynamic);
        }
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------

    private void logDebug(String msg) { Log.d(TAG, ts() + msg); }
    private void logInfo(String msg)  { Log.i(TAG, ts() + msg); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}

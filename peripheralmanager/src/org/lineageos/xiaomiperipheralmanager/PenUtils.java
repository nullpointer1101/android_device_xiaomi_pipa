/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.InputDevice;

import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for Xiaomi pen/stylus operations.
 *
 * Single source of truth for pen hardware state. PipaCore UI and tile service
 * should only write preferences and call the on*Changed callbacks here — they
 * must never set system properties or refresh rates directly.
 *
 * State machine:
 *   OFF          — both toggles off, zero overhead
 *   FORCE_ACTIVE — force mode on, unconditionally pen=18 + forced RR
 *   SMART_IDLE   — stylus on, pen not BT-connected, default RR
 *   SMART_ACTIVE — stylus on, pen BT-connected and not docked, forced RR
 *   SMART_DOCKED — stylus on, pen BT-connected and docked, default RR
 */
public class PenUtils {

    private static final String TAG   = "XiaomiPenUtils";
    private static final boolean DEBUG = true;

    // Xiaomi pen hardware identifiers (Bluetooth HID profile)
    private static final int PEN_VENDOR_ID  = 6421;
    private static final int PEN_PRODUCT_ID = 19841;

    // Preference keys
    private static final String STYLUS_MODE_KEY         = "stylus_mode_key";
    private static final String FORCE_STYLUS_KEY        = "force_recognize_stylus_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";
    private static final String RESTORE_ON_DOCK_KEY     = "stylus_restore_on_dock_key";

    // Static singletons
    private static InputManager    mInputManager;
    private static SharedPreferences mPreferences;
    private static RefreshUtils    mRefreshUtils;
    private static Handler         mHandler;
    private static Context         mContext;

    // State
    private static boolean mPenModeEnabled    = false;
    private static boolean mIsPenConnected    = false;
    private static boolean mIsDocked          = false;
    private static boolean mListenerRegistered = false;
    private static boolean mIsSetup           = false;
    private static String  mCurrentRefreshMode = "dynamic";

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /** @return true if setup() has been called and state is valid. */
    public static boolean isSetup() {
        return mIsSetup;
    }

    /**
     * Initialize all resources. Called from BootCompletedReceiver.
     */
    public static void setup(Context context) {
        mContext      = context;
        mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        mPreferences  = PreferenceManager.getDefaultSharedPreferences(context);
        mRefreshUtils = new RefreshUtils(context);
        mHandler      = new Handler(Looper.getMainLooper());
        mIsSetup      = true;

        mCurrentRefreshMode = mPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");

        boolean stylusEnabled  = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceRecognize = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);

        logInfo("Setup — stylus=" + stylusEnabled + ", force=" + forceRecognize
                + ", rate=" + mCurrentRefreshMode);

        // Query initial dock charging state
        PenChargingManager.PenChargingStatus initialStatus = PenChargingManager.getStatus(context);
        mIsDocked = (initialStatus != null && initialStatus.isConnected);

        // Register the input device listener only for smart stylus mode.
        // Force mode does not need detection — it unconditionally activates.
        // Users without a stylus benefit from zero idle overhead.
        updateListenerState();

        refreshPenMode();
    }

    // -----------------------------------------------------------------------
    // InputDeviceListener management
    // -----------------------------------------------------------------------

    /** Register the listener (idempotent). */
    private static void registerInputDeviceListener() {
        if (mListenerRegistered || mInputManager == null) return;
        mInputManager.registerInputDeviceListener(mInputDeviceListener, mHandler);
        mListenerRegistered = true;
        logInfo("InputDeviceListener registered");
    }

    /** Unregister the listener (idempotent). */
    private static void unregisterInputDeviceListener() {
        if (!mListenerRegistered || mInputManager == null) return;
        mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
        mListenerRegistered = false;
        logInfo("InputDeviceListener unregistered");
    }

    /**
     * Register/unregister the listener based on the combined state.
     *
     * InputDeviceListener is only needed for smart stylus mode (stylus ON,
     * force OFF) to detect when the Xiaomi pen's BT HID profile appears.
     * Force mode unconditionally activates, so no detection is needed.
     * When both are off, no listeners run — zero overhead.
     */
    private static void updateListenerState() {
        if (mPreferences == null) return;
        boolean stylusOn = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceOn  = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        if (stylusOn && !forceOn) {
            registerInputDeviceListener();
        } else {
            unregisterInputDeviceListener();
        }
    }

    // -----------------------------------------------------------------------
    // Pen mode control
    // -----------------------------------------------------------------------

    public static void enablePenMode() {
        if (mPenModeEnabled) {
            logDebug("Pen mode already enabled");
            return;
        }
        logInfo("Enabling pen mode");
        mPenModeEnabled = true;
        SystemProperties.set("persist.vendor.parts.pen", "18");

        // Always apply refresh rate when enabling pen mode — covers
        // both force mode and smart mode.
        if (mRefreshUtils != null) {
            applyRefreshRateMode(mCurrentRefreshMode);
        }
    }

    public static void disablePenMode() {
        if (!mPenModeEnabled) {
            logDebug("Pen mode already disabled");
            return;
        }
        logInfo("Disabling pen mode");
        mPenModeEnabled = false;
        SystemProperties.set("persist.vendor.parts.pen", "2");
        if (mRefreshUtils != null) {
            mRefreshUtils.setDefaultRefreshRate();
        }
    }

    /**
     * Core state machine. Evaluates all inputs and decides whether pen mode
     * should be active.
     *
     * Priority:
     *   1. Force mode ON → always enable (unconditional)
     *   2. Stylus mode ON + pen BT detected + (not docked OR !restoreOnDock) → enable
     *   3. Everything else → disable
     */
    private static void refreshPenMode() {
        if (mPreferences == null || mInputManager == null) {
            logError("refreshPenMode called before setup");
            return;
        }
        boolean forceRecognize    = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean restoreOnDock     = mPreferences.getBoolean(RESTORE_ON_DOCK_KEY, true);

        // Scan for the Xiaomi pen's Bluetooth HID input device
        boolean penDetected = false;
        for (int id : mInputManager.getInputDeviceIds()) {
            if (isDeviceXiaomiPen(id)) {
                penDetected = true;
                logDebug("Xiaomi pen detected (device ID: " + id + ")");
                break;
            }
        }
        mIsPenConnected = penDetected;

        // Evaluate the state machine (Master switch controls both modes)
        boolean shouldEnablePen;
        String reason;

        if (!stylusModeEnabled) {
            // Master switch is OFF: unconditionally disable pen mode & restore 144Hz
            shouldEnablePen = false;
            reason = "stylus mode disabled";
        } else if (forceRecognize) {
            // Force mode: unconditionally enable, no Bluetooth or dock checks needed
            shouldEnablePen = true;
            reason = "force mode";
        } else if (penDetected) {
            // Smart mode: pen is BT-connected
            if (mIsDocked && restoreOnDock) {
                // Pen is on the dock and user wants 144Hz while docked
                shouldEnablePen = false;
                reason = "smart mode, docked (restoring default RR)";
            } else {
                // Pen is in hand or user doesn't want dock restore
                shouldEnablePen = true;
                reason = "smart mode, pen active";
            }
        } else {
            // Smart mode but pen not BT-connected — idle
            shouldEnablePen = false;
            reason = "smart mode, pen not connected";
        }

        logInfo("refreshPenMode — stylus=" + stylusModeEnabled
                + ", pen=" + penDetected + ", force=" + forceRecognize
                + ", docked=" + mIsDocked + ", restoreOnDock=" + restoreOnDock
                + " → enable=" + shouldEnablePen + " (" + reason + ")");

        if (shouldEnablePen) {
            enablePenMode();
        } else {
            disablePenMode();
        }
    }

    private static void applyRefreshRateMode(String mode) {
        if (mRefreshUtils == null) {
            logError("RefreshUtils not initialized");
            return;
        }
        mCurrentRefreshMode = mode;
        switch (mode) {
            case "60":
                mRefreshUtils.setFixedRefreshRate(60f);
                logInfo("Applied fixed 60Hz");
                break;
            case "120":
                mRefreshUtils.setFixedRefreshRate(120f);
                logInfo("Applied fixed 120Hz");
                break;
            case "dynamic":
            default:
                mRefreshUtils.setPenRefreshRate();
                logInfo("Applied dynamic 60-120Hz");
                break;
        }
    }

    public static void setRefreshRateMode(String mode) {
        logInfo("Refresh rate mode → " + mode);
        mCurrentRefreshMode = mode;
        // Re-apply immediately if pen mode is currently active
        if (mPenModeEnabled && mRefreshUtils != null) {
            applyRefreshRateMode(mode);
        }
    }

    // -----------------------------------------------------------------------
    // Settings-change callbacks (called from tile service and settings fragment)
    // -----------------------------------------------------------------------

    public static void onStylusModeChanged(boolean enabled) {
        logInfo("Stylus mode setting → " + enabled);
        if (!mIsSetup) {
            logError("onStylusModeChanged called before setup — ignoring");
            return;
        }
        updateListenerState();
        refreshPenMode();
    }

    public static void onForceRecognizeChanged(boolean enabled) {
        logInfo("Force recognize setting → " + enabled);
        if (!mIsSetup) {
            logError("onForceRecognizeChanged called before setup — ignoring");
            return;
        }
        updateListenerState();
        // Force mode is independent — always re-evaluate
        refreshPenMode();
    }

    public static void onRestoreOnDockChanged(boolean enabled) {
        logInfo("Restore on dock setting → " + enabled);
        if (!mIsSetup) {
            logError("onRestoreOnDockChanged called before setup — ignoring");
            return;
        }
        refreshPenMode();
    }

    /**
     * General settings-change notification triggered by broadcast from PipaCore.
     */
    public static void onSettingsChanged() {
        logInfo("Settings changed broadcast processed");
        if (!mIsSetup || mPreferences == null) return;
        mCurrentRefreshMode = mPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
        updateListenerState();
        refreshPenMode();
    }

    /**
     * Called by PenChargingService when the pen's dock/charging state changes.
     * When the pen is placed on the magnetic charging dock, we can optionally
     * restore the default refresh rate (144Hz) since the user isn't drawing.
     */
    public static void onDockStateChanged(boolean isDocked) {
        if (mIsDocked == isDocked) return; // no change
        logInfo("Dock state → " + (isDocked ? "docked" : "undocked"));
        mIsDocked = isDocked;
        if (!mIsSetup) {
            logError("onDockStateChanged called before setup — ignoring");
            return;
        }
        refreshPenMode();
    }

    // -----------------------------------------------------------------------
    // InputDeviceListener
    // -----------------------------------------------------------------------

    private static final InputDeviceListener mInputDeviceListener = new InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int id) {
            logDebug("Device added: " + id);
            if (isDeviceXiaomiPen(id)) {
                logInfo("Xiaomi pen connected");
                mHandler.postDelayed(() -> refreshPenMode(), 100);
            }
        }

        @Override
        public void onInputDeviceRemoved(int id) {
            logDebug("Device removed: " + id);
            // Device already gone — re-scan remaining devices after a brief delay.
            mHandler.postDelayed(() -> refreshPenMode(), 100);
        }

        @Override
        public void onInputDeviceChanged(int id) {
            logDebug("Device changed: " + id);
            if (isDeviceXiaomiPen(id)) {
                logInfo("Xiaomi pen changed");
                mHandler.postDelayed(() -> refreshPenMode(), 100);
            }
        }
    };

    // -----------------------------------------------------------------------
    // Device identification
    // -----------------------------------------------------------------------

    private static boolean isDeviceXiaomiPen(int id) {
        try {
            InputDevice device = mInputManager.getInputDevice(id);
            if (device == null) return false;
            boolean isPen = device.getVendorId() == PEN_VENDOR_ID
                    && device.getProductId() == PEN_PRODUCT_ID;
            if (isPen) logDebug("Found Xiaomi pen: " + device.getName());
            return isPen;
        } catch (Exception e) {
            logError("Error checking device: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Public state accessors
    // -----------------------------------------------------------------------

    public static boolean isPenConnected()       { return mIsPenConnected; }
    public static boolean isPenModeEnabled()     { return mPenModeEnabled; }
    public static boolean isDocked()             { return mIsDocked; }
    public static String  getCurrentRefreshMode(){ return mCurrentRefreshMode; }

    /**
     * Returns a human-readable status string for the current state.
     */
    public static String getStatusText() {
        if (mPreferences == null) return "Not initialized";
        boolean forceOn  = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        boolean stylusOn = mPreferences.getBoolean(STYLUS_MODE_KEY, false);

        if (!stylusOn) {
            return "Off";
        } else if (forceOn && mPenModeEnabled) {
            return "Force Active";
        } else if (mPenModeEnabled) {
            return "Active (Pen In Hand)";
        } else if (mIsPenConnected && mIsDocked) {
            return "Docked (Charging)";
        } else if (!mIsPenConnected) {
            return "Waiting (No Pen)";
        } else {
            return "Idle";
        }
    }

    /** Release all resources. Resets the setup flag so isSetup() returns false. */
    public static void cleanup() {
        unregisterInputDeviceListener();
        if (mRefreshUtils != null) mRefreshUtils.cleanup();
        mIsSetup = false;
        logInfo("PenUtils cleaned up");
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------

    private static void logDebug(String message) {
        if (DEBUG) Log.d(TAG, ts() + message);
    }
    private static void logInfo(String message) {
        Log.i(TAG, ts() + message);
    }
    private static void logError(String message) {
        Log.e(TAG, ts() + message);
    }
    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}

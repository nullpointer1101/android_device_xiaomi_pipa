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
 */
public class PenUtils {

    private static final String TAG   = "XiaomiPenUtils";
    private static final boolean DEBUG = true;

    // Xiaomi pen hardware identifiers
    private static final int PEN_VENDOR_ID  = 6421;
    private static final int PEN_PRODUCT_ID = 19841;

    // Preference keys
    private static final String STYLUS_MODE_KEY         = "stylus_mode_key";
    private static final String FORCE_STYLUS_KEY        = "force_recognize_stylus_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    // Static singletons
    private static InputManager    mInputManager;
    private static SharedPreferences mPreferences;
    private static RefreshUtils    mRefreshUtils;
    private static Handler         mHandler;
    private static Context         mContext;

    // State
    private static boolean mPenModeEnabled    = false;
    private static boolean mIsPenConnected    = false;
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

        // Register the input device listener only when a stylus feature is active.
        // Devices without a stylus benefit from zero idle overhead when both are off.
        if (stylusEnabled || forceRecognize) {
            registerInputDeviceListener();
        } else {
            logInfo("Stylus features disabled — InputDeviceListener not registered");
        }

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
     * Register/unregister the listener based on the combined state of
     * Stylus Mode and Force Recognize. Called whenever either setting changes.
     */
    private static void updateListenerState() {
        if (mPreferences == null) return;
        boolean stylusOn = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean forceOn  = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        if (stylusOn || forceOn) {
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

        boolean stylusModeEnabled = mPreferences != null
                && mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        if (mRefreshUtils != null && stylusModeEnabled) {
            applyRefreshRateMode(mCurrentRefreshMode);
        } else {
            logInfo("Pen hardware enabled without refresh constraints (Stylus Mode off)");
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

    private static void refreshPenMode() {
        if (mPreferences == null || mInputManager == null) {
            logError("refreshPenMode called before setup");
            return;
        }
        boolean forceRecognize    = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        boolean stylusModeEnabled = mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        boolean penDetected       = false;

        for (int id : mInputManager.getInputDeviceIds()) {
            if (isDeviceXiaomiPen(id)) {
                penDetected = true;
                logDebug("Xiaomi pen detected (device ID: " + id + ")");
                break;
            }
        }

        mIsPenConnected = penDetected;

        // Enable pen mode only when: Stylus Mode is on AND (pen present OR force mode)
        boolean shouldEnablePen = stylusModeEnabled && (penDetected || forceRecognize);

        logInfo("refreshPenMode — stylus=" + stylusModeEnabled
                + ", pen=" + penDetected + ", force=" + forceRecognize
                + " → enable=" + shouldEnablePen);

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
        boolean stylusModeEnabled = mPreferences != null
                && mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        if (mPenModeEnabled && mRefreshUtils != null && stylusModeEnabled) {
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
        // Register or unregister the listener based on the combined (stylus || force) state.
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
        // Only re-evaluate pen mode when Stylus Mode is also on.
        boolean stylusModeEnabled = mPreferences != null
                && mPreferences.getBoolean(STYLUS_MODE_KEY, false);
        if (stylusModeEnabled) {
            refreshPenMode();
        } else {
            logInfo("Stylus Mode is off — force recognize change has no pen effect");
        }
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
    public static String  getCurrentRefreshMode(){ return mCurrentRefreshMode; }

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

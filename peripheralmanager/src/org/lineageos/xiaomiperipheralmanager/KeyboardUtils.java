/*
 * Copyright (C) 2023-2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.SystemProperties;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.view.InputDevice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for handling Xiaomi keyboard operations at the framework level.
 * Works in conjunction with the native xiaomi-keyboard service.
 */
public class KeyboardUtils {

    private static final String TAG = "XiaomiKeyboard";
    private static boolean DEBUG = SystemProperties.getBoolean("persist.xiaomi.keyboard.debug", false);
    private static final String KEYBOARD_MODE_KEY = "keyboard_mode_key";

    // Xiaomi keyboard identifiers
    private static final int KEYBOARD_VENDOR_ID = 5593;
    private static final int KEYBOARD_PRODUCT_ID = 163;

    private static InputManager mInputManager;
    private static boolean mLastEnabledState = false;

    /**
     * Initialize the keyboard utilities and set initial state
     * @param context Application context
     */
    public static void setup(Context context) {
        logInfo("Initializing Xiaomi keyboard framework integration");
        try {
            if (mInputManager == null) {
                mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            }
            
            // Check if keyboard monitoring is enabled in preferences
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean keyboardEnabled = prefs.getBoolean(KEYBOARD_MODE_KEY, false);
            
            if (!keyboardEnabled) {
                logInfo("Keyboard monitoring disabled by user - skipping setup");
                return;
            }
            
            setKeyboardEnabled(false);
        } catch (Exception e) {
            logError("Error setting up keyboard utils: " + e.getMessage());
        }
    }

    /**
     * Enable or disable keyboard monitoring service
     * @param context Application context
     * @param enabled Whether keyboard monitoring should be enabled
     */
    public static void setKeyboardMonitoringEnabled(Context context, boolean enabled) {
        logInfo("Setting keyboard monitoring: " + enabled);
        
        try {
            // Save preference
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean(KEYBOARD_MODE_KEY, enabled).apply();
            
            // Set system property to trigger init.rc service start/stop
            String propertyValue = enabled ? "1" : "0";
            SystemProperties.set("persist.vendor.parts.keyboard", propertyValue);

            if (enabled) {
                logInfo("Keyboard monitoring enabled (persist.vendor.parts.keyboard=1)");
            } else {
                logInfo("Keyboard monitoring disabled (persist.vendor.parts.keyboard=0)");
                // Ensure keyboard is disabled when monitoring is turned off
                setKeyboardEnabled(false);
            }
        } catch (Exception e) {
            logError("Error setting keyboard monitoring: " + e.getMessage());
        }
    }

    /**
     * Enable or disable the Xiaomi keyboard input device
     * @param enabled Whether the keyboard should be enabled
     * @return true if operation was successful, false otherwise
     */
    public static boolean setKeyboardEnabled(boolean enabled) {
        if (enabled == mLastEnabledState) {
            logDebug("Keyboard already in requested state: " + enabled);
            return true;
        }
        
        logInfo("Setting keyboard enabled: " + enabled);
        boolean success = false;
        
        try {
            if (mInputManager == null) {
                logError("InputManager not initialized");
                return false;
            }
            
            boolean deviceFound = false;
            for (int id : mInputManager.getInputDeviceIds()) {
                if (isDeviceXiaomiKeyboard(id)) {
                    deviceFound = true;
                    logDebug("Found Xiaomi Keyboard with id: " + id);
                    if (enabled) {
                        mInputManager.enableInputDevice(id);
                    } else {
                        mInputManager.disableInputDevice(id);
                    }
                    mLastEnabledState = enabled;
                    success = true;
                }
            }
            
            if (!deviceFound) {
                logInfo("Xiaomi keyboard not found in input devices");
            }
        } catch (Exception e) {
            logError("Error changing keyboard state: " + e.getMessage());
        }
        
        return success;
    }

    /**
     * Check if an input device is the Xiaomi keyboard
     * @param id Device ID to check
     * @return true if the device is the Xiaomi keyboard
     */
    private static boolean isDeviceXiaomiKeyboard(int id) {
        try {
            InputDevice inputDevice = mInputManager.getInputDevice(id);
            if (inputDevice == null) return false;
            
            return inputDevice.getVendorId() == KEYBOARD_VENDOR_ID && 
                   inputDevice.getProductId() == KEYBOARD_PRODUCT_ID;
        } catch (Exception e) {
            logError("Error checking device: " + e.getMessage());
            return false;
        }
    }
    
    // Enhanced logging helpers to match C++ style
    private static void logDebug(String message) {
        if (DEBUG) Log.d(TAG, getTimestamp() + message);
    }
    
    private static void logInfo(String message) {
        Log.i(TAG, getTimestamp() + message);
    }
    
    private static void logError(String message) {
        Log.e(TAG, getTimestamp() + message);
    }
    
    private static String getTimestamp() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}

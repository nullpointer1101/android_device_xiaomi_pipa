/*
 * Copyright (C) 2026 Mufasa
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.xiaomiperipheralmanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.hardware.input.InputManager;
import android.util.Log;
import android.view.InputDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Set;

/**
 * OEM-level Hardware Manager for reading Xiaomi Stylus wireless charging status.
 * Reads directly from sysfs nodes exposed by the IDT wireless power supply driver
 * (/sys/class/power_supply/idt) with multi-path and Bluetooth battery fallbacks.
 */
public class PenChargingManager {

    private static final String TAG = "PenChargingManager";
    public static boolean DEBUG = false;

    // Supported sysfs power supply base paths (fuda is primary for Nuvolta 1665 on pipa)
    private static final String[] POWER_SUPPLY_PATHS = {
        "/sys/class/power_supply/fuda",
        "/sys/class/power_supply/idt",
        "/sys/class/power_supply/wireless",
        "/sys/class/power_supply/pen",
        "/sys/class/power_supply/wireless_chg"
    };

    private static final int PEN_VENDOR_ID = 6421;
    private static final int PEN_PRODUCT_ID = 19841;

    // A stylus actually drawing from the reverse-charging coil pulls well over
    // 100mA. Anything below this threshold is foreign-object probing or
    // measurement noise and must never count as an attached stylus.
    private static final int MIN_ATTACH_CURRENT_MA = 20;

    // Consecutive samples required before accepting a state transition. The
    // service polls getStatus() every ~1.2s while the screen is on, so an
    // attach needs ~2.4s of sustained electrical activity and a detach needs
    // ~3.6s of silence. This rejects transient spikes and objects that are
    // only waved past the pill.
    private static final int ATTACH_CONFIRM_SAMPLES = 2;
    private static final int DETACH_CONFIRM_SAMPLES = 3;

    private static int sAttachSamples = 0;
    private static int sDetachSamples = 0;
    private static boolean sStylusConfirmed = false;

    public static class PenChargingStatus {
        public final boolean isConnected;
        public final boolean isCharging;
        public final int batteryLevel; // 0-100, or -1 if unknown

        public PenChargingStatus(boolean isConnected, boolean isCharging, int batteryLevel) {
            this.isConnected = isConnected;
            this.isCharging = isCharging;
            this.batteryLevel = batteryLevel;
        }

        public boolean isFullyCharged() {
            return batteryLevel == 100;
        }

        @Override
        public String toString() {
            return "PenChargingStatus{isConnected=" + isConnected +
                    ", isCharging=" + isCharging +
                    ", batteryLevel=" + batteryLevel + "}";
        }
    }

    /**
     * Finds the available power supply hardware path.
     */
    public static String getAvailableHardwarePath() {
        for (String path : POWER_SUPPLY_PATHS) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    /**
     * Check if wireless charging hardware is available.
     */
    public static boolean isAvailable() {
        return getAvailableHardwarePath() != null;
    }

    /**
     * Get current stylus charging status from hardware and fallback subsystems.
     */
    public static PenChargingStatus getStatus(Context context) {
        String basePath = getAvailableHardwarePath();
        if (basePath == null) {
            if (DEBUG) Log.d(TAG, "getStatus(): No wireless charging power supply available");
            return null;
        }

        try {
            // 1. Read real-time charging current (mA)
            Integer iout = readSysfsInt(basePath + "/reverse_iout");
            if (iout == null) {
                // Fallback to current_now (often in uA)
                Integer currNow = readSysfsInt(basePath + "/current_now");
                if (currNow != null) {
                    iout = Math.abs(currNow) / 1000;
                } else {
                    iout = 0;
                }
            }

            // 2. Read hall attachment sensors and charging mode
            Integer hall3 = readSysfsInt(basePath + "/reverse_chg_hall3");
            Integer hall4 = readSysfsInt(basePath + "/reverse_chg_hall4");
            Integer chgMode = readSysfsInt(basePath + "/reverse_chg_mode");

            boolean hallAttached = (hall3 != null && hall3 == 1) || (hall4 != null && hall4 == 1);
            boolean modeActive = chgMode != null && chgMode == 1;
            // A meaningful sustained load on the coil: a real receiver.
            boolean currentFlowing = iout != null && iout >= MIN_ATTACH_CURRENT_MA;

            // 3. Read battery SoC (Nuvolta IC reverse_pen_soc or capacity)
            Integer soc = readSysfsInt(basePath + "/reverse_pen_soc");
            if (soc == null || soc < 0 || soc > 100) {
                soc = readSysfsInt(basePath + "/capacity");
            }
            boolean socValid = soc != null && soc >= 0 && soc <= 100;

            // The hall sensors trip on *any* magnet near the pill (cases,
            // coins, toys...), but a stray magnet can neither draw charging
            // current nor answer the SoC handshake. Attaching therefore
            // strictly requires real current on the coil; hall state alone is
            // never enough.
            //
            // While attached, a fully charged stylus tapers its current to
            // almost zero, so it is held connected via its SoC report instead
            // of being dropped for lack of current.
            boolean attachEvidence = currentFlowing;
            boolean holdEvidence = currentFlowing || (socValid && hallAttached && modeActive);

            updateConfirmation(attachEvidence, holdEvidence);

            boolean isConnected = sStylusConfirmed;
            boolean isCharging = sStylusConfirmed && (currentFlowing || socValid);

            int batteryLevel = -1;
            if (socValid) {
                batteryLevel = soc;
            } else if (context != null && isConnected) {
                // Fallback to InputManager or Bluetooth battery level
                int fallbackSoc = getFallbackBatteryLevel(context);
                if (fallbackSoc >= 0 && fallbackSoc <= 100) {
                    batteryLevel = fallbackSoc;
                }
            }

            if (DEBUG) {
                Log.d(TAG, "getStatus(): path=" + basePath + ", iout=" + iout + "mA, soc=" + soc +
                        ", hall3=" + hall3 + ", hall4=" + hall4 + ", chgMode=" + chgMode +
                        " => confirmed=" + isConnected + ", isCharging=" + isCharging +
                        ", battery=" + batteryLevel + "%");
            }

            return new PenChargingStatus(isConnected, isCharging, batteryLevel);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read pen charging status", e);
            return null;
        }
    }

    /**
     * Debounce state machine: a transition is only accepted after enough
     * consecutive agreeing samples, so a single noisy reading or an object
     * briefly waved past the pill never changes the reported state.
     */
    private static synchronized void updateConfirmation(boolean attachEvidence,
                                                        boolean holdEvidence) {
        if (!sStylusConfirmed) {
            if (attachEvidence) {
                if (++sAttachSamples >= ATTACH_CONFIRM_SAMPLES) {
                    sStylusConfirmed = true;
                    sAttachSamples = 0;
                    sDetachSamples = 0;
                }
            } else {
                sAttachSamples = 0;
            }
        } else if (holdEvidence) {
            sDetachSamples = 0;
        } else if (++sDetachSamples >= DETACH_CONFIRM_SAMPLES) {
            sStylusConfirmed = false;
            sAttachSamples = 0;
            sDetachSamples = 0;
        }
    }

    /**
     * Clears the confirmation state machine. Called when monitoring is paused
     * so a stale confirmation cannot leak into the next session.
     */
    public static synchronized void resetDetectionState() {
        sAttachSamples = 0;
        sDetachSamples = 0;
        sStylusConfirmed = false;
    }

    /**
     * Fallback to reading battery level from Android InputManager or paired Bluetooth devices.
     */
    private static int getFallbackBatteryLevel(Context context) {
        try {
            InputManager inputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) {
                for (int id : inputManager.getInputDeviceIds()) {
                    InputDevice device = inputManager.getInputDevice(id);
                    if (device != null && device.getVendorId() == PEN_VENDOR_ID &&
                            device.getProductId() == PEN_PRODUCT_ID) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            android.hardware.BatteryState bs = device.getBatteryState();
                            if (bs != null && bs.isPresent()) {
                                float cap = bs.getCapacity();
                                if (cap >= 0f && cap <= 1f) {
                                    return Math.round(cap * 100f);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (DEBUG) Log.w(TAG, "InputDevice battery fallback failed: " + t.getMessage());
        }
        return -1;
    }

    private static Integer readSysfsInt(String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line != null) {
                String trimmed = line.trim();
                return Integer.parseInt(trimmed);
            }
        } catch (Exception e) {
            if (DEBUG) Log.v(TAG, "readSysfsInt(" + path + ") failed: " + e.getMessage());
        }
        return null;
    }
}

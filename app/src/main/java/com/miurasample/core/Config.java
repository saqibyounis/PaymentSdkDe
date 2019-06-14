/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.core;

import android.util.Log;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Config {

    private static final String REQUIRED_OS_VERSION = "7-12";
    private static final String REQUIRED_MPI_VERSION = "1-47";
    private static final String REQUIRED_RPI_VERSION = "1-6";
    private static final String REQUIRED_RPI_OS_VERSION = "1-6";
    private static final int MAX_TIME_DIFFERENCE_SECONDS = 10;
    private static final int MIN_BATTERY_LEVEL = 10;

    /*File names*/
    private static final String TEST_OS_FILE_NAME_BASE = "M000-TESTOS-V";
    private static final String TEST_OS_UPDATE_FILE_NAME = "M000-TESTOSUPDATE-V1-6.tar.gz";
    private static final String OS_FILE_NAME_BASE = "M000-OS-V";
    private static final String OS_UPDATE_FILE_NAME = "M000-OSUPDATE-V1-6.tar.gz";
    private static final String TEST_MPI_FILE_NAME_BASE = "M000-TESTMPI-V";
    private static final String TEST_MPI_CONF_FILE_NAME = "M000-TESTMPI-Vx-x-CONF00-V6.tar.gz";

    private static final String TEST_RPI_OS_FILE_NAME_BASE = "M100-TESTOS-V";
    private static final String TEST_RPI_OS_UPDATE_FILE_NAME = "M100-TESTOSUPDATE-V1-0.tar.gz";
    private static final String RPI_OS_FILE_NAME_BASE = "M100-OS-V";
    private static final String RPI_OS_UPDATE_FILE_NAME = "M100-OSUPDATE-V1-0.tar.gz";
    private static final String TEST_RPI_FILE_NAME_BASE = "M100-TESTRPI-V";
    private static final String RPI_FILE_NAME_BASE = "M100-RPI-V";

    /*Config versioning*/
    private static final String REQUIRED_CONFIG_VERSION = "mSdk-V0.1";

    private static final String FILE_EXTENSION = ".tar.gz";
    private static String TAG = Config.class.getName();

    public static String getConfigVersion()  {
        return (REQUIRED_CONFIG_VERSION);
    }

    public static boolean isConfigVersionValid(HashMap<String, String> configVersions) {

        List<String> configNames = Arrays.asList("AACDOL.CFG", "ARQCDOL.CFG", "contactless.cfg",
                "ctls-prompts.txt", "emv.cfg", "OPDOL.CFG", "P2PEDOL.CFG", "TCDOL.CFG", "TDOL.CFG",
                "TRMDOL.CFG","MPI-Dynamic.cfg");

        for (String configName : configNames) {
            String config = configVersions.get(configName);

            if (!config.equals(REQUIRED_CONFIG_VERSION)) {
                Log.d(TAG, "Config returned false");
                return false;
            } else {
                Log.v(TAG, "Config returned true");
            }
        }
        Log.d(TAG, "All configs were ok");
        return true;

    }

    public static String getTestOsFileName() {
        return (TEST_OS_FILE_NAME_BASE + REQUIRED_OS_VERSION + FILE_EXTENSION);
    }

    public static String getTestOsUpdateFileName() {
        return (TEST_OS_UPDATE_FILE_NAME);
    }

    public static String getOsFileName() {
        return (OS_FILE_NAME_BASE + REQUIRED_OS_VERSION + FILE_EXTENSION);
    }

    public static String getOsUpdateFileName() {
        return OS_UPDATE_FILE_NAME;
    }

    public static String getTestMpiFileName() {
        return (TEST_MPI_FILE_NAME_BASE + REQUIRED_MPI_VERSION + FILE_EXTENSION);
    }

    public static String getTestMpiConfFileName() {
        return TEST_MPI_CONF_FILE_NAME;
    }

    public static String getTestRpiOsFileName() {
        return (TEST_RPI_OS_FILE_NAME_BASE + REQUIRED_RPI_OS_VERSION + FILE_EXTENSION);
    }

    public static String getTestRpiOsUpdateFileName() {
        return TEST_RPI_OS_UPDATE_FILE_NAME;
    }

    public static String getRpiOsFileName() {
        return (RPI_OS_FILE_NAME_BASE + REQUIRED_RPI_OS_VERSION + FILE_EXTENSION);
    }

    public static String getRpiOsUpdateFileName() {
        return RPI_OS_UPDATE_FILE_NAME;
    }

    public static String getTestRpiFileName() {
        return (TEST_RPI_FILE_NAME_BASE + REQUIRED_RPI_VERSION + FILE_EXTENSION);
    }

    public static String getRpiFileName() {
        return (RPI_FILE_NAME_BASE + REQUIRED_RPI_VERSION + FILE_EXTENSION);
    }

    public static boolean isOsVersionValid(String osVersion) {
        return osVersion.equals(REQUIRED_OS_VERSION);
    }

    public static boolean isRpiVersionValid(String mpiVersion) {
        return mpiVersion.equals(REQUIRED_RPI_VERSION);
    }

    public static boolean isRpiOsVersionValid(String rpiVersion) {
        return rpiVersion.equals(REQUIRED_RPI_OS_VERSION);
    }

    public static boolean isMpiVersionValid(String mpiVersion) {
        return mpiVersion.equals(REQUIRED_MPI_VERSION);
    }

    public static boolean isTimeValid(Date dateTime) {
        return (new Date().getTime() - dateTime.getTime()) < (MAX_TIME_DIFFERENCE_SECONDS * 1000);
    }

    public static boolean isBatteryValid(int batteryLevel) {
        return batteryLevel >= MIN_BATTERY_LEVEL;
    }
}

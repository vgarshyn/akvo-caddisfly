/*
 *  Copyright (C) Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo Caddisfly
 *
 *  Akvo Caddisfly is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo Caddisfly is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.caddisfly.app;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;

import org.akvo.caddisfly.AppConfig;
import org.akvo.caddisfly.model.Swatch;
import org.akvo.caddisfly.model.TestInfo;
import org.akvo.caddisfly.util.ApiUtils;
import org.akvo.caddisfly.util.JsonUtils;
import org.akvo.caddisfly.util.PreferencesUtils;

import java.util.ArrayList;
import java.util.Locale;

public class CaddisflyApp extends Application {

    private static boolean hasCameraFlash;
    private static boolean checkedForFlash;
    private static CaddisflyApp app;// Singleton
    public TestInfo currentTestInfo = new TestInfo();

    /**
     * Check if the device has a camera flash
     *
     * @param context the context
     * @return true if camera flash exists otherwise false
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasFeatureCameraFlash(Context context) {
        //check only once for flash
        if (!checkedForFlash) {
            hasCameraFlash = ApiUtils.hasCameraFlash(context);
            checkedForFlash = true;
        }
        return hasCameraFlash;
    }

    /**
     * Gets the singleton app object
     *
     * @return the singleton app
     */
    public static CaddisflyApp getApp() {
        return app;
    }

    /**
     * Gets the app version
     *
     * @param context The context
     * @return The version name and number
     */
    public static String getVersion(Context context) {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            String[] words = version.split("\\s");
            String versionString = "";
            for (String word : words) {
                try {
                    Double versionNumber = Double.parseDouble(word);
                    versionString += String.format(Locale.US, "%.2f", versionNumber);
                } catch (NumberFormatException e) {
                    int id = context.getResources()
                            .getIdentifier(word.toLowerCase(), "string", context.getPackageName());
                    if (id > 0) {
                        versionString += context.getString(id);
                    } else {
                        versionString += word;
                    }
                }
                versionString += " ";
            }
            return versionString.trim();

        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
    }

    /**
     * Initialize the current test by loading the configuration and calibration information
     */
    public void initializeCurrentTest() {
        if (currentTestInfo.getCode().isEmpty()) {
            setDefaultTest();
        } else {
            loadTestConfiguration(currentTestInfo.getCode());
        }
    }

    /**
     * Select the first test type in the configuration file as the current test
     */
    public void setDefaultTest() {

        ArrayList<TestInfo> tests;
        tests = JsonUtils.loadConfigurationsForAllTests(AppConfig.getConfigJson());
        if (tests.size() > 0) {
            currentTestInfo = tests.get(0);
            if (currentTestInfo.getType() == AppConfig.TestType.COLORIMETRIC_LIQUID) {
                loadCalibratedSwatches(currentTestInfo);
            }
        }
    }

    /**
     * Load the test configuration for the given test code
     *
     * @param testCode the test code
     */
    public void loadTestConfiguration(String testCode) {

        currentTestInfo = JsonUtils.loadTestConfigurationByCode(
                AppConfig.getConfigJson(), testCode.toUpperCase());

        if (currentTestInfo != null) {
            if (currentTestInfo.getType() == AppConfig.TestType.COLORIMETRIC_LIQUID) {
                loadCalibratedSwatches(currentTestInfo);
            }
        }
    }

    /**
     * Load any user calibrated swatches
     *
     * @param testInfo The type of test
     */
    private void loadCalibratedSwatches(TestInfo testInfo) {

        CaddisflyApp context = ((CaddisflyApp) this.getApplicationContext());

        for (Swatch swatch : testInfo.getSwatches()) {
            String key = String.format(Locale.US, "%s-%.2f", testInfo.getCode(), swatch.getValue());
            swatch.setColor(PreferencesUtils.getInt(context, key, 0));
        }
    }

    /**
     * Save a single calibrated color
     *
     * @param swatch       The swatch object
     * @param resultColor The color value
     */
    public void saveCalibratedData(Swatch swatch, final int resultColor) {
        String colorKey = String.format(Locale.US, "%s-%.2f", currentTestInfo.getCode(), swatch.getValue());

        if (resultColor == 0) {
            PreferencesUtils.removeKey(getApplicationContext(), colorKey);
        } else {
            swatch.setColor(resultColor);
            PreferencesUtils.setInt(getApplicationContext(), colorKey, resultColor);
        }
    }

    /**
     * Save a list of calibrated colors
     *
     * @param swatches List of swatch colors to be saved
     */
    public void saveCalibratedSwatches(ArrayList<Swatch> swatches) {

        for (Swatch swatch : swatches) {
            String key = String.format(Locale.US, "%s-%.2f", currentTestInfo.getCode(), swatch.getValue());

            PreferencesUtils.setInt(this, key, swatch.getColor());
        }

        loadCalibratedSwatches(currentTestInfo);
    }
}
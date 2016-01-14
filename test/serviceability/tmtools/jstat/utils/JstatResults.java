/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package utils;

import common.ToolResults;

/**
 * Results of running the jstat tool Concrete subclasses will detail the jstat
 * tool options
 */
abstract public class JstatResults extends ToolResults {

    public JstatResults(ToolResults rawResults) {
        super(rawResults);
    }

    /**
     * Gets a string result from the column labeled 'name'
     *
     * @param name - name of the column
     * @return the result
     */
    public String getStringValue(String name) {
        int valueNdx = new StringOfValues(getStdoutLine(0)).getIndex(name);
        return new StringOfValues(getStdoutLine(1)).getValue(valueNdx);
    }

    /**
     * Gets a float result from the column labeled 'name'
     *
     * @param name - name of the column
     * @return the result
     */
    public float getFloatValue(String name) {
        int valueNdx = new StringOfValues(getStdoutLine(0)).getIndex(name);
        return Float.valueOf(new StringOfValues(getStdoutLine(1)).getValue(valueNdx));
    }

    /**
     * Gets an integer result from the column labeled 'name'
     *
     * @param name - name of the column
     * @return the result
     */
    public int getIntValue(String name) {
        int valueNdx = new StringOfValues(getStdoutLine(0)).getIndex(name);
        return Integer.valueOf(new StringOfValues(getStdoutLine(1)).getValue(valueNdx));
    }

    /**
     * Checks if a column with a given name exists
     *
     * @param name - name of the column
     * @return true if the column exist, false otherwise
     */
    public boolean valueExists(String name) {
        return new StringOfValues(getStdoutLine(0)).getIndex(name) != -1;
    }

    /**
     * Helper function to assert the increase of the GC events between 2
     * measurements
     *
     * @param measurement1 -first measurement
     * @param measurement2 -first measurement
     */
    public static void assertGCEventsIncreased(JstatResults measurement1, JstatResults measurement2) {
        assertThat(measurement2.getFloatValue("YGC") > measurement1.getFloatValue("YGC"), "YGC didn't increase between measurements 1 and 2");
        assertThat(measurement2.getFloatValue("FGC") > measurement1.getFloatValue("FGC"), "FGC didn't increase between measurements 2 and 3");
    }

    /**
     * Helper function to assert the increase of the GC time between 2
     * measurements
     *
     * @param measurement1 -first measurement
     * @param measurement2 -first measurement
     */
    public static void assertGCTimeIncreased(JstatResults measurement1, JstatResults measurement2) {
        assertThat(measurement2.getFloatValue("YGCT") > measurement1.getFloatValue("YGCT"), "YGCT time didn't increase between measurements 1 and 2");
        assertThat(measurement2.getFloatValue("FGCT") > measurement1.getFloatValue("FGCT"), "FGCT time didn't increase between measurements 1 and 2");
        assertThat(measurement2.getFloatValue("GCT") > measurement1.getFloatValue("GCT"), "GCT time didn't increase between measurements 1 and 2");
    }

    /**
     * Helper function to assert the utilization of the space
     *
     * @param measurement - measurement results to analyze
     * @param targetMemoryUsagePercent -assert that not less than this amount of
     * space has been utilized
     */
    public static void assertSpaceUtilization(JstatResults measurement, float targetMemoryUsagePercent) {

        if (measurement.valueExists("OU")) {
            float OC = measurement.getFloatValue("OC");
            float OU = measurement.getFloatValue("OU");
            assertThat((OU / OC) > targetMemoryUsagePercent, "Old space utilization should be > "
                    + (targetMemoryUsagePercent * 100) + "%, actually OU / OC = " + (OU / OC));
        }

        if (measurement.valueExists("MU")) {
            float MC = measurement.getFloatValue("MC");
            float MU = measurement.getFloatValue("MU");
            assertThat((MU / MC) > targetMemoryUsagePercent, "Metaspace utilization should be > "
                    + (targetMemoryUsagePercent * 100) + "%, actually MU / MC = " + (MU / MC));
        }

        if (measurement.valueExists("O")) {
            float O = measurement.getFloatValue("O");
            assertThat(O > targetMemoryUsagePercent * 100, "Old space utilization should be > "
                    + (targetMemoryUsagePercent * 100) + "%, actually O = " + O);
        }

        if (measurement.valueExists("M")) {
            float M = measurement.getFloatValue("M");
            assertThat(M > targetMemoryUsagePercent * 100, "Metaspace utilization should be > "
                    + (targetMemoryUsagePercent * 100) + "%, actually M = " + M);
        }
    }

    private static void assertThat(boolean result, String message) {
        if (!result) {
            throw new RuntimeException(message);
        }
    }

}

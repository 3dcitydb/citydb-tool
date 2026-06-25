/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import org.citydb.cli.logging.LoggerManager;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;

public class LoggerHelper {
    public static final Marker PLAIN_LOG = MarkerFactory.getMarker("org.citydb.cli.plainLog");

    public static void setLogPattern(Marker marker, String pattern) {
        LoggerManager.getInstance().withLogPattern(marker, pattern).reconfigure();
    }

    public static void setLogLevel(Class<?> type, Level level) {
        LoggerManager.getInstance().withLevel(type, map(level)).reconfigure();
    }

    public static void setLogLevel(String name, Level level) {
        LoggerManager.getInstance().withLevel(name, map(level)).reconfigure();
    }

    private static org.apache.logging.log4j.Level map(Level level) {
        return switch (level) {
            case ERROR -> org.apache.logging.log4j.Level.ERROR;
            case WARN -> org.apache.logging.log4j.Level.WARN;
            case INFO -> org.apache.logging.log4j.Level.INFO;
            case DEBUG -> org.apache.logging.log4j.Level.DEBUG;
            case TRACE -> org.apache.logging.log4j.Level.TRACE;
        };
    }
}

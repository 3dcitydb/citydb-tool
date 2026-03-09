/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.logging;

import org.apache.logging.log4j.Level;

import java.util.Objects;

public class LogConsole {
    private final LoggerManager manager;
    private boolean enabled;
    private Level logLevel = Level.INFO;
    private String logPattern = LoggerManager.DEFAULT_LOG_PATTERN;

    LogConsole(LoggerManager manager) {
        this.manager = manager;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LogConsole setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public LogConsole setLogLevel(Level logLevel) {
        this.logLevel = Objects.requireNonNull(logLevel, "The log level must not be null.");
        return this;
    }

    public String getLogPattern() {
        return logPattern;
    }

    public LogConsole setLogPattern(String logPattern) {
        this.logPattern = Objects.requireNonNull(logPattern, "The log pattern must not be null.");
        return this;
    }

    public void reconfigure() {
        manager.reconfigure();
    }
}

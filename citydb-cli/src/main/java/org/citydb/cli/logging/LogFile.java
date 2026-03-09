/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.logging;

import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.util.Objects;

public class LogFile {
    private final LoggerManager manager;
    private boolean enabled;
    private Level logLevel = Level.INFO;
    private String logPattern = LoggerManager.DEFAULT_LOG_PATTERN;
    private Path path = Path.of(LoggerManager.DEFAULT_LOG_FILE);
    private String rollingFileSuffix = LoggerManager.DEFAULT_ROLLING_FILE_SUFFIX;
    private boolean useLogRotation;

    LogFile(LoggerManager manager) {
        this.manager = manager;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LogFile setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public LogFile setLogLevel(Level logLevel) {
        this.logLevel = Objects.requireNonNull(logLevel, "The log level must not be null.");
        return this;
    }

    public String getLogPattern() {
        return logPattern;
    }

    public LogFile setLogPattern(String logPattern) {
        this.logPattern = Objects.requireNonNull(logPattern, "The log pattern must not be null.");
        return this;
    }

    public Path getPath() {
        return path;
    }

    public LogFile setPath(Path path) {
        this.path = Objects.requireNonNull(path, "The path must not be null.");
        return this;
    }

    public String getRollingFileSuffix() {
        return rollingFileSuffix;
    }

    public LogFile setRollingFileSuffix(String rollingFileSuffix) {
        this.rollingFileSuffix = Objects.requireNonNull(rollingFileSuffix, "The rolling file suffix must not be null.");
        return this;
    }

    public boolean isUseLogRotation() {
        return useLogRotation;
    }

    public LogFile setUseLogRotation(boolean useLogRotation) {
        this.useLogRotation = useLogRotation;
        return this;
    }

    public void reconfigure() {
        manager.reconfigure();
    }
}

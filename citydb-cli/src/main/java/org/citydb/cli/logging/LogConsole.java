/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

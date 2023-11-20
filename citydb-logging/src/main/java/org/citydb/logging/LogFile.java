/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.logging;

import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.util.Objects;

public class LogFile {
    private boolean enabled;
    private Level logLevel = Level.INFO;
    private String logPattern = "[%d{HH:mm:ss} %p] %m%n";
    private Path path = Path.of("citydb.log");
    private String filePattern = "-%d{yyyy-MM-dd}";
    private boolean useLogRotation;

    private final LoggerManager loggerManager;

    protected LogFile(LoggerManager loggerManager) {
        this.loggerManager = loggerManager;
    }

    public LoggerManager configure() {
        return loggerManager.updateConfigurations();
    }

    public boolean isEnabled() {
        return this.enabled;
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

    public String getFilePattern() {
        return filePattern;
    }

    public LogFile setFilePattern(String filePattern) {
        this.filePattern = Objects.requireNonNull(filePattern, "The file pattern must not be null.");
        return this;
    }

    public boolean isUseLogRotation() {
        return useLogRotation;
    }

    public LogFile setUseLogRotation(boolean useLogRotation) {
        this.useLogRotation = useLogRotation;
        return this;
    }
}

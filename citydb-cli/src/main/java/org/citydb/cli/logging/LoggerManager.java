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

import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LoggerManager {
    public static final String DEFAULT_LOG_PATTERN = "[%d{HH:mm:ss} %p] %m%n";
    public static final String DEFAULT_LOG_FILE = "citydb.log";
    public static final String DEFAULT_ROLLING_FILE_SUFFIX = "-%d{yyyy-MM-dd}";
    public static final Marker PLAIN_MARKER = MarkerManager.getMarker("PLAIN");

    private static final LoggerManager instance = new LoggerManager();
    private final LogConsole logConsole;
    private final LogFile logFile;
    private final Map<String, String> patterns = new HashMap<>();

    private LoggerManager() {
        logConsole = new LogConsole(this).setEnabled(true);
        logFile = new LogFile(this).setEnabled(false);
        withLogPattern(LoggerManager.PLAIN_MARKER, "%m%n").reconfigure();
    }

    public static LoggerManager getInstance() {
        return instance;
    }

    public LoggerManager withLogPattern(Marker marker, String pattern) {
        Objects.requireNonNull(marker, "The log marker must not be null.");
        Objects.requireNonNull(pattern, "The log pattern must not be null.");
        patterns.put(marker.getName(), pattern);
        return this;
    }

    public void reconfigure() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.ALL);

        if (logConsole.isEnabled()) {
            String CONSOLE_LOGGER_NAME = "LOG_CONSOLE";

            rootLogger.add(builder.newAppenderRef(CONSOLE_LOGGER_NAME)
                    .addAttribute("level", logConsole.getLogLevel().name()));

            builder.add(builder.newAppender(CONSOLE_LOGGER_NAME, "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
                    .add(getLayoutComponentBuilder(logConsole.getLogPattern(), builder)));
        }

        if (logFile.isEnabled()) {
            String FILE_LOGGER_NAME = "LOG_FILE";
            String fileName = logFile.getPath().toAbsolutePath().toString();

            rootLogger.add(builder.newAppenderRef(FILE_LOGGER_NAME)
                    .addAttribute("level", logFile.getLogLevel().name()));

            AppenderComponentBuilder appenderComponentBuilder = builder
                    .newAppender(FILE_LOGGER_NAME, logFile.isUseLogRotation() ? "RollingFile" : "File")
                    .addAttribute("fileName", fileName)
                    .addAttribute("append", logFile.isUseLogRotation())
                    .add(getLayoutComponentBuilder(logFile.getLogPattern(), builder));

            if (logFile.isUseLogRotation()) {
                appenderComponentBuilder
                        .addAttribute("filePattern", buildRollingFilePattern(fileName))
                        .add(builder.newLayout("TimeBasedTriggeringPolicy"));
            }

            builder.add(appenderComponentBuilder);
        }

        Configurator.reconfigure(builder.add(rootLogger).build());
    }

    public Logger getLogger() {
        return LogManager.getLogger();
    }

    public Logger getLogger(Class<?> type) {
        return LogManager.getLogger(type);
    }

    public Logger getLogger(Object value) {
        return LogManager.getLogger(value);
    }

    public Logger getLogger(String name) {
        return LogManager.getLogger(name);
    }

    public LogConsole logConsole() {
        return logConsole;
    }

    public LogFile logFile() {
        return logFile;
    }

    private LayoutComponentBuilder getLayoutComponentBuilder(String defaultPattern, ConfigurationBuilder<BuiltConfiguration> builder) {
        ComponentBuilder<?> selector = builder.newComponent("MarkerPatternSelector")
                .addAttribute("defaultPattern", defaultPattern);
        patterns.forEach((key, pattern) -> selector.addComponent(builder.newComponent("PatternMatch")
                .addAttribute("key", key)
                .addAttribute("pattern", pattern)));
        return builder.newLayout("PatternLayout").addComponent(selector);
    }

    private String buildRollingFilePattern(String fileName) {
        String suffix = logFile.getRollingFileSuffix();
        int index = fileName.lastIndexOf('.');
        return index != -1 ?
                fileName.substring(0, index) + suffix + fileName.substring(index) + ".gz" :
                fileName + suffix + ".gz";
    }
}
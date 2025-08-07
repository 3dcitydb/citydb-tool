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
import org.citydb.cli.CliConstants;
import org.citydb.core.concurrent.LazyInitializer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class LoggerManager {
    public static final String DEFAULT_LOG_PATTERN = "[%d{HH:mm:ss} %p] %m%n";
    public static final String DEFAULT_LOG_FILE = "citydb.log";
    public static final String DEFAULT_ROLLING_FILE_SUFFIX = "-%d{yyyy-MM-dd}";
    public static final Marker PLAIN_MARKER = MarkerManager.getMarker("PLAIN");

    private static final String CONSOLE_APPENDER_NAME = "LOG_CONSOLE";
    private static final String FILE_APPENDER_NAME = "LOG_FILE";
    private static final String LOGGING_CONFIG_PROPERTY = "citydb.cli.logging.config";

    private static final LoggerManager instance = new LoggerManager();
    private final LogConsole logConsole;
    private final LogFile logFile;
    private final Map<String, String> patterns = new HashMap<>();
    private final LazyInitializer<Map<String, Level>> logLevels;

    static {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        java.util.logging.LogManager.getLogManager().reset();
    }

    private LoggerManager() {
        logConsole = new LogConsole(this).setEnabled(true);
        logFile = new LogFile(this).setEnabled(false);
        logLevels = LazyInitializer.of(this::loadLogLevels);
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

    public LoggerManager withLevel(Class<?> type, Level level) {
        return withLevel(getLogger(type).getName(), level);
    }

    public LoggerManager withLevel(String name, Level level) {
        Objects.requireNonNull(name, "The logger name must not be null.");
        Objects.requireNonNull(level, "The log level must not be null.");
        logLevels.get().put(name, level);
        return this;
    }

    public Logger getLogger() {
        return LogManager.getLogger();
    }

    public Logger getLogger(Class<?> type) {
        return LogManager.getLogger(type);
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

    public void reconfigure() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.ALL);

        if (logConsole.isEnabled()) {
            rootLogger.add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)
                    .addAttribute("level", logConsole.getLogLevel().name()));

            builder.add(builder.newAppender(CONSOLE_APPENDER_NAME, "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
                    .add(getLayoutComponentBuilder(logConsole.getLogPattern(), builder)));
        }

        if (logFile.isEnabled()) {
            String fileName = logFile.getPath().toAbsolutePath().toString();

            rootLogger.add(builder.newAppenderRef(FILE_APPENDER_NAME)
                    .addAttribute("level", logFile.getLogLevel().name()));

            AppenderComponentBuilder appenderBuilder = builder
                    .newAppender(FILE_APPENDER_NAME, logFile.isUseLogRotation() ? "RollingFile" : "File")
                    .addAttribute("fileName", fileName)
                    .addAttribute("append", logFile.isUseLogRotation())
                    .add(getLayoutComponentBuilder(logFile.getLogPattern(), builder));

            if (logFile.isUseLogRotation()) {
                appenderBuilder
                        .addAttribute("filePattern", buildRollingFilePattern(fileName))
                        .addComponent(builder.newComponent("Policies")
                                .addComponent(builder.newComponent("TimeBasedTriggeringPolicy")));
            }

            builder.add(appenderBuilder);
        }

        Configurator.reconfigure(builder.add(rootLogger).build());
        logLevels.get().forEach(Configurator::setLevel);
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

    private Map<String, Level> loadLogLevels() {
        Map<String, Level> logLevels = new HashMap<>();
        Properties properties = new Properties();

        try (InputStream stream = CliConstants.class.getResourceAsStream("/org/citydb/cli/logging.properties")) {
            properties.load(stream);
        } catch (Exception e) {
            //
        }

        String configFile = System.getProperty(LOGGING_CONFIG_PROPERTY);
        if (configFile != null) {
            try (InputStream stream = Files.newInputStream(Path.of(configFile))) {
                Properties userProperties = new Properties();
                userProperties.load(stream);
                properties.putAll(userProperties);
            } catch (Exception e) {
                System.err.printf("Failed to load logging configuration file %s: %s%n", configFile, e.getMessage());
            }
        }

        properties.stringPropertyNames().forEach(key ->
                logLevels.put(key, Level.toLevel(properties.getProperty(key), Level.ERROR)));

        return logLevels;
    }
}
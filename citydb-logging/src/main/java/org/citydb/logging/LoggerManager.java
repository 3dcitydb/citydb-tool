/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.nio.file.Path;

public class LoggerManager {
    private final LogConsole logConsole = new LogConsole(this);
    private final LogFile logFile = new LogFile(this);

    private static final LoggerManager instance = new LoggerManager();

    public static LoggerManager getInstance() {
        return instance;
    }

    private LoggerManager() {
        logConsole().setEnabled(true).configure();
    }

    protected LoggerManager updateConfigurations() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.ALL);

        // configure console log
        if (logConsole.isEnabled()) {
            final String CONSOLE_LOGGER_NAME = "LOG_CONSOLE";

            rootLogger.add(builder.newAppenderRef(CONSOLE_LOGGER_NAME)
                    .addAttribute("level", logConsole.getLogLevel().name()));

            LayoutComponentBuilder patternLayoutBuilder = builder.newLayout("PatternLayout")
                    .addAttribute("pattern", logConsole.getLogPattern());

            builder.add(builder
                    .newAppender(CONSOLE_LOGGER_NAME, "CONSOLE")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                    .add(patternLayoutBuilder));
        }

        // config file log
        if (logFile.isEnabled()) {
            final String FILE_LOGGER_NAME = "LOG_FILE";

            rootLogger.add(builder.newAppenderRef(FILE_LOGGER_NAME)
                    .addAttribute("level", logFile.getLogLevel().name()));

            LayoutComponentBuilder patternLayoutBuilder = builder.newLayout("PatternLayout")
                    .addAttribute("pattern", logFile.getLogPattern());

            Path filePath = logFile.getPath().toAbsolutePath();

            AppenderComponentBuilder appenderComponentBuilder = builder
                    .newAppender(FILE_LOGGER_NAME, logFile.isUseLogRotation() ? "RollingFile" : "File")
                    .addAttribute("fileName", filePath)
                    .add(patternLayoutBuilder);

            if (logFile.isUseLogRotation()) {
                appenderComponentBuilder.addAttribute("filePattern", filePath + logFile.getFilePattern() + ".gz")
                        .add(builder.newLayout("TimeBasedTriggeringPolicy"));
            }

            builder.add(appenderComponentBuilder);
        }

        Configurator.reconfigure(builder.add(rootLogger).build());

        return this;
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
}
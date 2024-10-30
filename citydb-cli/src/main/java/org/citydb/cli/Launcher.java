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

package org.citydb.cli;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConfigOption;
import org.citydb.cli.common.Option;
import org.citydb.cli.deleter.DeleteCommand;
import org.citydb.cli.exporter.ExportCommand;
import org.citydb.cli.extension.MainCommand;
import org.citydb.cli.importer.ImportCommand;
import org.citydb.cli.index.IndexCommand;
import org.citydb.cli.util.CommandHelper;
import org.citydb.cli.util.PidFile;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.core.CoreConstants;
import org.citydb.database.DatabaseConstants;
import org.citydb.logging.LoggerManager;
import org.citydb.plugin.Extension;
import org.citydb.plugin.Plugin;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

import java.io.Console;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = CliConstants.APP_COMMAND,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Command-line interface for the 3D City Database.",
        synopsisSubcommandLabel = "COMMAND",
        abbreviateSynopsis = true,
        mixinStandardHelpOptions = true,
        versionProvider = Launcher.class,
        showAtFileInUsageHelp = true,
        sortOptions = false,
        subcommands = {
                CommandLine.HelpCommand.class
        })
public class Launcher implements Command, CommandLine.IVersionProvider {
    @CommandLine.Option(names = "--config-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Load configuration from this file.")
    private Path configFile;

    @CommandLine.Option(names = {"-L", "--log-level"}, scope = CommandLine.ScopeType.INHERIT, paramLabel = "<level>",
            defaultValue = "info", description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LogLevel logLevel;

    @CommandLine.Option(names = "--log-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Write log messages to this file.")
    private Path logFile;

    @CommandLine.Option(names = "--pid-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Create a file containing the process ID.")
    private Path pidFile;

    @CommandLine.Option(names = "--plugins", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<dir>",
            description = "Load plugins from this directory.")
    private Path pluginsDirectory;

    @CommandLine.Option(names = "--use-plugins", scope = CommandLine.ScopeType.INHERIT, split = ",",
            paramLabel = "<plugin[=true|false]", mapFallbackValue = "true",
            description = "Enable or disable plugins with a matching fully qualified class name " +
                    "(default: ${MAP-FALLBACK-VALUE}).")
    private Map<String, Boolean> usePlugins;

    private final Logger logger = LoggerManager.getInstance().getLogger(Launcher.class);
    private final PluginManager pluginManager = PluginManager.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final Config config = new Config();
    private String commandLine;
    private String subCommandName;

    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        try {
            System.exit(launcher.execute(args));
        } catch (Throwable e) {
            launcher.logException(e);
            System.exit(CommandLine.ExitCode.SOFTWARE);
        }
    }

    private int execute(String[] args) throws Exception {
        Instant start = Instant.now();
        int exitCode = CommandLine.ExitCode.SOFTWARE;

        System.setProperty("picocli.disable.closures", "true");
        CommandLine cmd = new CommandLine(this);

        pluginManager.load(parsePluginsDirectory(args));
        Command.addSubcommand(new ImportCommand(), cmd, pluginManager);
        Command.addSubcommand(new ExportCommand(), cmd, pluginManager);
        Command.addSubcommand(new DeleteCommand(), cmd, pluginManager);
        Command.addSubcommand(new IndexCommand(), cmd, pluginManager);
        for (MainCommand mainCommand : pluginManager.getAllExtensions(MainCommand.class)) {
            Command.addSubcommand(mainCommand, cmd, pluginManager);
        }

        try {
            CommandLine.ParseResult parseResult = cmd.setCaseInsensitiveEnumValuesAllowed(true)
                    .setAbbreviatedOptionsAllowed(true)
                    .setAbbreviatedSubcommandsAllowed(true)
                    .setExecutionStrategy(new CommandLine.RunAll())
                    .parseArgs(args);

            List<CommandLine> commandLines = parseResult.asCommandLineList();
            for (CommandLine commandLine : commandLines) {
                if (commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested()) {
                    return CommandLine.executeHelpRequest(parseResult);
                } else if (commandLine.getCommand() instanceof CommandLine.HelpCommand) {
                    return cmd.getExecutionStrategy().execute(parseResult);
                }
            }

            if (!parseResult.hasSubcommand()) {
                throw new CommandLine.ParameterException(cmd, "Missing required subcommand.");
            }

            if (usePlugins != null) {
                for (Plugin plugin : pluginManager.getPlugins()) {
                    plugin.setEnabled(usePlugins.getOrDefault(plugin.getClass().getName(), plugin.isEnabled()));
                }
            }

            for (CommandLine commandLine : commandLines) {
                Object command = commandLine.getCommand();

                if (command instanceof Extension extension) {
                    Plugin plugin = pluginManager.getPlugin(extension);
                    if (plugin != null && !plugin.isEnabled()) {
                        throw new CommandLine.ParameterException(commandLine, "The subcommand '" +
                                commandLine.getCommandName() + "' is added through the plugin " +
                                plugin.getClass().getName() + " but this plugin is disabled.");
                    }
                }

                Class<?> type = command.getClass();
                do {
                    for (Field field : type.getDeclaredFields()) {
                        field.setAccessible(true);
                        if (Option.class.isAssignableFrom(field.getType())) {
                            Option option = (Option) field.get(command);
                            if (option != null) {
                                option.preprocess(commandLine);
                            }
                        }

                        if (field.isAnnotationPresent(ConfigOption.class)
                                && Config.class.isAssignableFrom(field.getType())) {
                            field.set(command, config);
                        }
                    }
                } while ((type = type.getSuperclass()) != Object.class);

                if (command instanceof Command) {
                    ((Command) command).preprocess(commandLine);
                }

                CommandLine.Model.OptionSpec password = commandLine.getParseResult().matchedOption("--db-password");
                if (password != null && password.getValue().equals("")) {
                    password.setValue(readPassword(commandLine.getParseResult().matchedOptionValue("--db-username",
                            System.getenv(DatabaseConstants.ENV_CITYDB_USERNAME))));
                }
            }

            commandLine = CliConstants.APP_COMMAND + " " + String.join(" ", args);
            subCommandName = commandLines.subList(1, commandLines.size()).stream()
                    .map(CommandLine::getCommandName)
                    .collect(Collectors.joining(" "));
            exitCode = cmd.getExecutionStrategy().execute(parseResult);

            logger.info("Total execution time: {}.", formatElapsedTime(Duration.between(start, Instant.now())));

            if (exitCode == CommandLine.ExitCode.OK) {
                logger.info("{} successfully completed.", CliConstants.APP_COMMAND);
            } else {
                logger.warn("{} execution failed.", CliConstants.APP_COMMAND);
            }
        } catch (CommandLine.ParameterException e) {
            cmd.getParameterExceptionHandler().handleParseException(e, args);
            exitCode = CommandLine.ExitCode.USAGE;
        } catch (CommandLine.ExecutionException e) {
            logException(e.getCause());
        } catch (Throwable e) {
            logException(e);
        }

        return exitCode;
    }

    @Override
    public Integer call() throws ExecutionException {
        initializeLogging();
        logger.info("Starting {}, version {}.", CliConstants.APP_NAME, CliConstants.APP_VERSION);

        if (pidFile != null) {
            createPidFile();
        }

        if (configFile != null) {
            loadConfig();
        }

        loadPlugins();

        logger.info("Executing '{}' command.", subCommandName);
        return CommandLine.ExitCode.OK;
    }

    private void initializeLogging() {
        LoggerManager manager = LoggerManager.getInstance();
        manager.logConsole()
                .setLogLevel(logLevel.level)
                .setLogPattern(logLevel.level.isMoreSpecificThan(Level.INFO) ?
                        manager.logConsole().getLogPattern() + "%ex{0}" :
                        manager.logConsole().getLogPattern())
                .setEnabled(true)
                .configure();

        if (logFile != null) {
            if (Files.isDirectory(logFile)) {
                logFile = logFile.resolve(manager.logFile().getPath());
            }

            logger.debug("Writing log messages to {}.", logFile.toAbsolutePath());
            manager.logFile()
                    .setLogLevel(logLevel.level)
                    .setPath(logFile)
                    .setEnabled(true)
                    .configure();
        }
    }

    private void createPidFile() throws ExecutionException {
        try {
            logger.debug("Creating PID file at {}.", pidFile.toAbsolutePath());
            PidFile.create(pidFile, true);
        } catch (IOException e) {
            throw new ExecutionException("Failed to create PID file.", e);
        }
    }

    private void loadConfig() throws ExecutionException {
        logger.info("Loading configuration from file {}...", configFile);
        try {
            config.putAll(ConfigManager.newInstance().read(configFile, Config.class, Config::new));
        } catch (Exception e) {
            throw new ExecutionException("Failed to load config file.", e);
        }
    }

    private void loadPlugins() throws ExecutionException {
        logger.info("Loading plugins...");
        if (!pluginManager.hasExceptions()) {
            for (Plugin plugin : pluginManager.getPlugins()) {
                String name = "\"" + plugin.getMetadata().getName() + "\" (" + plugin.getClass().getName() + ")";
                if (plugin.isEnabled()) {
                    logger.info("Loaded plugin {}.", name);
                } else {
                    logger.debug("Disabling plugin {}.", name);
                }
            }
        } else {
            pluginManager.getExceptions().values().stream()
                    .flatMap(Collection::stream)
                    .forEach(e -> logger.error(e.getMessage(), e.getCause()));

            throw new ExecutionException("Failed to load plugins.");
        }
    }

    private String readPassword(String user) throws ExecutionException {
        String prompt = "Enter password for " + user;
        Console console = System.console();
        if (console != null) {
            char[] input = console.readPassword(prompt + ": ");
            return input != null ? new String(input) : null;
        } else {
            ExecutorService service = Executors.newFixedThreadPool(1);
            try {
                System.out.print(prompt + " (will timeout in 60s): ");
                return service.submit(() -> new Scanner(System.in).nextLine())
                        .get(60, TimeUnit.SECONDS);
            } catch (Throwable e) {
                throw new ExecutionException("Failed to read password from console.", e);
            } finally {
                service.shutdown();
            }
        }
    }

    private Path parsePluginsDirectory(String[] args) {
        String pluginsDirectory = null;
        for (int i = 0; pluginsDirectory == null && i < args.length; i++) {
            int delimiter = args[i].indexOf('=');
            String parameter = delimiter != -1 ? args[i].substring(0, delimiter) : args[i];
            if ("--plugins".startsWith(parameter.toLowerCase(Locale.ROOT))) {
                if (delimiter != -1 && delimiter < args[i].length()) {
                    pluginsDirectory = args[i].substring(delimiter + 1);
                } else if (i < args.length - 1) {
                    pluginsDirectory = args[++i];
                }
            }
        }

        return pluginsDirectory != null ?
                CoreConstants.WORKING_DIR.resolve(pluginsDirectory) :
                CoreConstants.APP_HOME.resolve(CliConstants.PLUGINS_DIR);
    }

    private void logException(Throwable e) {
        helper.logException(e);
        logger.warn("{} execution failed.", CliConstants.APP_COMMAND);
    }

    private String formatElapsedTime(Duration elapsed) {
        long d = elapsed.toDaysPart();
        long h = elapsed.toHoursPart();
        long m = elapsed.toMinutesPart();
        long s = elapsed.toSecondsPart();

        if (d > 0) {
            return String.format("%02d d, %02d h, %02d m, %02d s", d, h, m, s);
        } else if (h > 0) {
            return String.format("%02d h, %02d m, %02d s", h, m, s);
        } else if (m > 0) {
            return String.format("%02d m, %02d s", m, s);
        } else {
            return String.format("%02d s", s);
        }
    }

    enum LogLevel {
        fatal(Level.FATAL),
        error(Level.ERROR),
        warn(Level.WARN),
        info(Level.INFO),
        debug(Level.DEBUG),
        trace(Level.TRACE);

        private final Level level;

        LogLevel(Level level) {
            this.level = level;
        }
    }

    @Override
    public String[] getVersion() {
        return new String[]{CliConstants.APP_NAME + ", version " + CliConstants.APP_VERSION,
                "(C) " + CliConstants.APP_COPYRIGHT_YEAR + " " + CliConstants.APP_VENDOR};
    }
}
/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.Inject;
import org.citydb.cli.common.Option;
import org.citydb.cli.connect.ConnectCommand;
import org.citydb.cli.deleter.DeleteCommand;
import org.citydb.cli.exporter.ExportCommand;
import org.citydb.cli.extension.MainCommand;
import org.citydb.cli.importer.ImportCommand;
import org.citydb.cli.index.IndexCommand;
import org.citydb.cli.info.InfoCommand;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.cli.util.PidFile;
import org.citydb.config.Config;
import org.citydb.config.ConfigManager;
import org.citydb.database.DatabaseConstants;
import org.citydb.plugin.Extension;
import org.citydb.plugin.Plugin;
import org.citydb.plugin.PluginException;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LogLevel logLevel = LogLevel.info;

    @CommandLine.Option(names = "--log-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Write log messages to this file.")
    private Path logFile;

    @CommandLine.Option(names = "--quiet", scope = CommandLine.ScopeType.INHERIT,
            description = "Disable console log messages.")
    private boolean quiet;

    @CommandLine.Option(names = "--pid-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Create a file containing the process ID.")
    private Path pidFile;

    @CommandLine.Option(names = "--plugins", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<dir>",
            description = "Load plugins from this directory.")
    private Path pluginsDirectory;

    @CommandLine.Option(names = "--use-plugin", scope = CommandLine.ScopeType.INHERIT, split = ",",
            paramLabel = "<plugin[=true|false]>", mapFallbackValue = "true",
            description = "Enable or disable plugins with a matching fully qualified class name " +
                    "(default: ${MAP-FALLBACK-VALUE}).")
    private Map<String, Boolean> usePlugins;

    private final Logger logger = LoggerManager.getInstance().getLogger(Launcher.class);
    private final List<Plugin> pluginsToRegister = new ArrayList<>();
    private CommandHelper helper;
    private List<PluginException> pluginFailures;
    private String commandLine;
    private String subCommandName;

    static {
        System.setProperty("picocli.disable.closures", "true");
    }

    public static Launcher newInstance() {
        return new Launcher();
    }

    public static void main(String[] args) {
        System.exit(new Launcher().run(args));
    }

    public Launcher withPlugin(Plugin plugin) {
        pluginsToRegister.add(plugin);
        return this;
    }

    public int run(String[] args) {
        try {
            return execute(args);
        } catch (Throwable e) {
            logException(e);
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    public int execute(String[] args) throws Exception {
        Instant start = Instant.now();
        int exitCode = CommandLine.ExitCode.SOFTWARE;
        CommandLine cmd = new CommandLine(this);

        try (PluginManager pluginManager = PluginManager.newInstance()) {
            helper = new CommandHelper();
            helper.setPluginManager(pluginManager);

            for (Plugin plugin : pluginsToRegister) {
                pluginManager.register(plugin, true);
            }

            pluginFailures = pluginManager.load(parsePluginsDirectory(args));
            Command.addSubcommand(new ConnectCommand(), cmd, pluginManager);
            Command.addSubcommand(new InfoCommand(), cmd, pluginManager);
            Command.addSubcommand(new ImportCommand(), cmd, pluginManager);
            Command.addSubcommand(new ExportCommand(), cmd, pluginManager);
            Command.addSubcommand(new DeleteCommand(), cmd, pluginManager);
            Command.addSubcommand(new IndexCommand(), cmd, pluginManager);
            for (MainCommand mainCommand : pluginManager.getAllExtensions(MainCommand.class)) {
                Command.addSubcommand(mainCommand, cmd, pluginManager);
            }

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

            initializeLogging();

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

                preprocessCommand(command, commandLine);
            }

            commandLine = CliConstants.APP_COMMAND + " " + String.join(" ", args);
            subCommandName = commandLines.subList(1, commandLines.size()).stream()
                    .map(CommandLine::getCommandName)
                    .collect(Collectors.joining(" "));

            try {
                exitCode = cmd.getExecutionStrategy().execute(parseResult);
            } finally {
                helper.disconnect();
            }

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
        } finally {
            helper.disconnect();
        }

        return exitCode;
    }

    @Override
    public Integer call() throws ExecutionException {
        logger.info("Starting {}, version {}.", CliConstants.APP_NAME, CliConstants.APP_VERSION);

        if (pidFile != null) {
            createPidFile(helper.resolveAgainstWorkingDir(pidFile));
        }

        helper.setConfig(configFile != null
                ? loadConfig(helper.resolveAgainstWorkingDir(configFile))
                : new Config());

        loadPlugins();

        logger.info("Executing '{}' command.", subCommandName);
        return CommandLine.ExitCode.OK;
    }

    private void initializeLogging() {
        LoggerManager manager = LoggerManager.getInstance();
        manager.logConsole()
                .setLogLevel(logLevel.level)
                .setLogPattern(logLevel.level.isMoreSpecificThan(Level.INFO)
                        ? manager.logConsole().getLogPattern() + "%ex{0}"
                        : manager.logConsole().getLogPattern())
                .setEnabled(!quiet)
                .reconfigure();

        if (logFile != null) {
            logFile = helper.resolveAgainstWorkingDir(logFile);
            if (Files.isDirectory(logFile)) {
                logFile = logFile.resolve(LoggerManager.DEFAULT_LOG_FILE);
            }

            logger.debug("Writing log messages to {}.", logFile);
            manager.logFile()
                    .setLogLevel(logLevel.level)
                    .setPath(logFile)
                    .setEnabled(true)
                    .reconfigure();
        }
    }

    private void createPidFile(Path file) throws ExecutionException {
        try {
            logger.debug("Creating PID file at {}.", file);
            PidFile.create(file, true);
        } catch (IOException e) {
            throw new ExecutionException("Failed to create PID file.", e);
        }
    }

    private Config loadConfig(Path file) throws ExecutionException {
        try {
            logger.info("Loading configuration from file {}...", file);
            return ConfigManager.newInstance().read(file, Config.class, Config::new);
        } catch (Exception e) {
            throw new ExecutionException("Failed to load config file.", e);
        }
    }

    private void loadPlugins() throws ExecutionException {
        logger.info("Loading plugins...");
        if (pluginFailures.isEmpty()) {
            for (Plugin plugin : helper.getPluginManager().getPlugins()) {
                logger.info("{} plugin: {}{} ({})",
                        plugin.isEnabled() ? "Loaded" : "Disabled",
                        plugin.getMetadata().getName().orElse(plugin.getClass().getName()),
                        plugin.getMetadata().getVersion().map(version -> " version " + version).orElse(""),
                        plugin.getClass().getName());
            }
        } else {
            pluginFailures.forEach(e -> logger.error(e.getMessage(), e.getCause()));
            throw new ExecutionException("Failed to load plugins.");
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

        return pluginsDirectory != null
                ? CliConstants.WORKING_DIR.resolve(pluginsDirectory)
                : CliConstants.APP_HOME.resolve(CliConstants.PLUGINS_DIR);
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

    private void preprocessCommand(Object command, CommandLine commandLine) throws Exception {
        Class<?> type = command.getClass();
        do {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                boolean isOption = Option.class.isAssignableFrom(field.getType());
                boolean isInjectable = !Modifier.isFinal(field.getModifiers())
                        && field.isAnnotationPresent(Inject.class);
                if (!isOption && !isInjectable) {
                    continue;
                }

                field.setAccessible(true);

                if (isOption) {
                    Option option = (Option) field.get(command);
                    if (option != null) {
                        option.preprocess(commandLine);
                    }
                }

                if (isInjectable) {
                    if (CommandHelper.class.isAssignableFrom(field.getType())) {
                        field.set(command, helper);
                    } else {
                        logger.debug("Ignoring @Inject on unsupported type {} in {}.",
                                field.getType().getName(), type.getName());
                    }
                }
            }
        } while ((type = type.getSuperclass()) != Object.class);

        if (command instanceof Command preprocessor) {
            preprocessor.preprocess(commandLine);
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
        return new String[]{CliConstants.APP_NAME + " " + CliConstants.APP_VERSION,
                "Supports " + DatabaseConstants.CITYDB_SHORT_NAME + " versions " + DatabaseConstants.VERSION_SUPPORT,
                "Copyright (C) " + CliConstants.APP_COPYRIGHT_YEAR + " " + CliConstants.APP_VENDOR + "\n"};
    }
}
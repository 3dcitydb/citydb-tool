/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.cli.ExecutionException;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

public interface Command extends Callable<Integer> {
    @Override
    Integer call() throws ExecutionException;

    default void preprocess(CommandLine commandLine) throws Exception {
    }

    default void registerSubcommands(CommandLine commandLine, PluginManager pluginManager) throws Exception {
    }

    static boolean hasMatchedOption(String name, CommandLine.Model.CommandSpec commandSpec) {
        return commandSpec.commandLine().getParseResult().hasMatchedOption(name);
    }

    static void addSubcommand(Command command, CommandLine commandLine, PluginManager pluginManager) throws Exception {
        CommandLine subcommandLine = new CommandLine(command);
        command.registerSubcommands(subcommandLine, pluginManager);
        commandLine.addSubcommand(subcommandLine);
    }
}

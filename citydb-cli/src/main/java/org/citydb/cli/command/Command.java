/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.cli.command;

import org.citydb.cli.ExecutionException;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

public interface Command extends Callable<Integer> {
    @Override
    Integer call() throws ExecutionException;
    default void preprocess(CommandLine commandLine) throws Exception {}
    default void registerSubcommands(CommandLine commandLine, PluginManager pluginManager) throws Exception {}

    static void addSubcommand(Command command, CommandLine commandLine, PluginManager pluginManager) throws Exception {
        CommandLine subcommandLine = new CommandLine(command);
        command.registerSubcommands(subcommandLine, pluginManager);
        commandLine.addSubcommand(subcommandLine);
    }
}

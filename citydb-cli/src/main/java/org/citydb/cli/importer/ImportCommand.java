/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer;

import org.citydb.cli.common.Command;
import org.citydb.cli.extension.ImportFormatCommand;
import org.citydb.cli.importer.citygml.CityGMLImportCommand;
import org.citydb.cli.importer.cityjson.CityJSONImportCommand;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = "import",
        scope = CommandLine.ScopeType.INHERIT,
        description = "Import data in a supported format.",
        subcommands = {
                CommandLine.HelpCommand.class
        })
public class ImportCommand implements Command {

    @Override
    public Integer call() {
        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (!commandLine.getParseResult().hasSubcommand()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Missing required subcommand for the input format.");
        }
    }

    @Override
    public void registerSubcommands(CommandLine commandLine, PluginManager pluginManager) throws Exception {
        commandLine.addSubcommand(new CityGMLImportCommand());
        commandLine.addSubcommand(new CityJSONImportCommand());
        for (ImportFormatCommand extension : pluginManager.getAllExtensions(ImportFormatCommand.class)) {
            Command.addSubcommand(extension, commandLine, pluginManager);
        }
    }
}

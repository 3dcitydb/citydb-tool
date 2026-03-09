/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter;

import org.citydb.cli.common.Command;
import org.citydb.cli.exporter.citygml.CityGMLExportCommand;
import org.citydb.cli.exporter.cityjson.CityJSONExportCommand;
import org.citydb.cli.extension.ExportFormatCommand;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = "export",
        scope = CommandLine.ScopeType.INHERIT,
        description = "Export data in a supported format.",
        subcommands = {
                CommandLine.HelpCommand.class
        })
public class ExportCommand implements Command {

    @Override
    public Integer call() {
        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (!commandLine.getParseResult().hasSubcommand()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Missing required subcommand for the output format.");
        }
    }

    @Override
    public void registerSubcommands(CommandLine commandLine, PluginManager pluginManager) throws Exception {
        commandLine.addSubcommand(new CityGMLExportCommand());
        commandLine.addSubcommand(new CityJSONExportCommand());
        for (ExportFormatCommand extension : pluginManager.getAllExtensions(ExportFormatCommand.class)) {
            Command.addSubcommand(extension, commandLine, pluginManager);
        }
    }
}

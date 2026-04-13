/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.visExporter;

import org.citydb.cli.common.Command;
import org.citydb.cli.visExporter.i3s.I3SExportCommand;
import org.citydb.cli.visExporter.tiles3d.Tiles3DExportCommand;
import org.citydb.plugin.PluginManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = "vis-export",
        scope = CommandLine.ScopeType.INHERIT,
        description = "Export data in a visualization format.",
        subcommands = {
                CommandLine.HelpCommand.class
        })
public class VisExportCommand implements Command {

    @Override
    public Integer call() {
        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (!commandLine.getParseResult().hasSubcommand()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Missing required subcommand for the visualization format.");
        }
    }

    @Override
    public void registerSubcommands(CommandLine commandLine, PluginManager pluginManager) throws Exception {
        commandLine.addSubcommand(new I3SExportCommand());
        commandLine.addSubcommand(new Tiles3DExportCommand());
    }
}

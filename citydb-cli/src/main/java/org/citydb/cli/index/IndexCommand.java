/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index;

import org.citydb.cli.common.Command;
import org.citydb.cli.index.create.CreateIndexCommand;
import org.citydb.cli.index.drop.DropIndexCommand;
import org.citydb.cli.index.status.IndexStatusCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "index",
        scope = CommandLine.ScopeType.INHERIT,
        description = "Perform index operations.",
        subcommands = {
                CommandLine.HelpCommand.class,
                IndexStatusCommand.class,
                CreateIndexCommand.class,
                DropIndexCommand.class
        })
public class IndexCommand implements Command {

    @Override
    public Integer call() {
        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (!commandLine.getParseResult().hasSubcommand()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Missing required subcommand for the index operation.");
        }
    }
}

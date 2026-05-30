/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.index;

import org.citydb.cli.CommandHelper;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.ConnectionOptions;
import org.citydb.cli.common.Inject;
import picocli.CommandLine;

public abstract class IndexController implements Command {
    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Database connection options:%n")
    protected ConnectionOptions connectionOptions;

    @Inject
    protected CommandHelper helper;
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

public class ThreadsOptions implements Option {
    @CommandLine.Option(names = "--threads",
            description = "Number of threads to use for parallel processing.")
    private Integer threads;

    public Integer getNumberOfThreads() {
        return threads;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (threads != null && threads <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Number of threads must be a positive integer but was '" + threads + "'");
        }
    }
}

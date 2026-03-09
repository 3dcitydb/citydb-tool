/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.exporter.cityjson;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

public class UpgradeOptions implements Option {
    @CommandLine.Option(names = "--use-lod4-as-lod3",
            description = "Use LoD4 as LoD3, replacing an existing LoD3.")
    private Boolean useLod4AsLod3;

    public Boolean getUseLod4AsLod3() {
        return useLod4AsLod3;
    }
}

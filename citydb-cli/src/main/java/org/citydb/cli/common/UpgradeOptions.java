/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import picocli.CommandLine;

public class UpgradeOptions implements Option {
    @CommandLine.Option(names = "--use-lod4-as-lod3",
            description = "Use LoD4 as LoD3, replacing an existing LoD3.")
    private Boolean useLod4AsLod3;

    @CommandLine.Option(names = "--map-lod0-roof-edge",
            description = "Map LoD0 roof edges onto roof surfaces.")
    private Boolean mapLod0RoofEdge;

    @CommandLine.Option(names = "--map-lod1-surface",
            description = "Map LoD1 multi-surfaces onto generic thematic surfaces.")
    private Boolean mapLod1MultiSurface;

    public Boolean getUseLod4AsLod3() {
        return useLod4AsLod3;
    }

    public Boolean getMapLod0RoofEdge() {
        return mapLod0RoofEdge;
    }

    public Boolean getMapLod1MultiSurface() {
        return mapLod1MultiSurface;
    }
}

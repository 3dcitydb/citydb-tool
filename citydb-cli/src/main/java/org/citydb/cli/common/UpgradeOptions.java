/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

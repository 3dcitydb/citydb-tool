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

package org.citydb.cli.importer.citygml;

import org.citydb.cli.importer.ImportController;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.reader.CityGMLFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "citygml",
        description = "Import data in CityGML format.")
public class CityGMLImportCommand extends ImportController {
    @CommandLine.Option(names = "--import-xal-source",
            description = "Import XML snippets of xAL address elements.")
    protected boolean importXALSource;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Upgrade options for CityGML 2.0 and 1.0:%n")
    private UpgradeOptions upgradeOptions;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityGMLAdapter.class);
    }

    @Override
    protected Object getFormatOptions() {
        CityGMLFormatOptions formatOptions = new CityGMLFormatOptions()
                .setImportXALSource(importXALSource);

        if (upgradeOptions != null) {
            formatOptions.setUseLod4AsLod3(upgradeOptions.isUseLod4AsLod3())
                    .setMapLod0RoofEdge(upgradeOptions.isMapLod0RoofEdge())
                    .setMapLod1MultiSurfaces(upgradeOptions.isMapLod1MultiSurface());
        }

        return formatOptions;
    }
}

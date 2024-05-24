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

package org.citydb.cli.importer.citygml;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.importer.ImportController;
import org.citydb.cli.option.UpgradeOptions;
import org.citydb.config.ConfigException;
import org.citydb.config.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.reader.CityGMLFormatOptions;
import org.citydb.io.reader.option.InputFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "citygml",
        description = "Import data in CityGML format.")
public class CityGMLImportCommand extends ImportController {
    @CommandLine.Option(names = "--import-xal-source",
            description = "Import XML snippets of xAL address elements.")
    protected Boolean importXALSource;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Upgrade options for CityGML 2.0 and 1.0:%n")
    private UpgradeOptions upgradeOptions;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityGMLAdapter.class);
    }

    @Override
    protected InputFormatOptions getFormatOptions(ConfigObject<InputFormatOptions> formatOptions) throws ExecutionException {
        CityGMLFormatOptions options;
        try {
            options = formatOptions.getOrElse(CityGMLFormatOptions.class, CityGMLFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get CityGML format options from config.", e);
        }

        if (importXALSource != null) {
            options.setImportXALSource(importXALSource);
        }

        if (upgradeOptions != null) {
            if (upgradeOptions.getUseLod4AsLod3() != null) {
                options.setUseLod4AsLod3(upgradeOptions.getUseLod4AsLod3());
            }

            if (upgradeOptions.getMapLod0RoofEdge() != null) {
                options.setMapLod0RoofEdge(upgradeOptions.getMapLod0RoofEdge());
            }

            if (upgradeOptions.getMapLod1MultiSurface() != null) {
                options.setMapLod1MultiSurfaces(upgradeOptions.getMapLod1MultiSurface());
            }
        }

        return options;
    }
}

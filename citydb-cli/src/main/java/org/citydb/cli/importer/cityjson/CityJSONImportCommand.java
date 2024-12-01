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

package org.citydb.cli.importer.cityjson;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.importer.ImportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.citygml.reader.CityJSONFormatOptions;
import org.citydb.io.citygml.reader.options.FormatOptions;
import org.citydb.io.reader.options.InputFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "cityjson",
        description = "Import data in CityJSON format.")
public class CityJSONImportCommand extends ImportController {
    @CommandLine.Option(names = "--no-map-unknown-objects", negatable = true, defaultValue = "true",
            description = "Map city objects from unsupported extensions onto generic city objects " +
                    "(default: ${DEFAULT-VALUE}).")
    private boolean mapUnknownObjects;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityJSONAdapter.class);
    }

    @Override
    protected InputFormatOptions getFormatOptions(ConfigObject<InputFormatOptions> formatOptions) throws ExecutionException {
        CityJSONFormatOptions options;
        try {
            options = formatOptions.get(CityJSONFormatOptions.class);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get CityJSON format options from config.", e);
        }

        if (options != null) {
            if (Command.hasMatchedOption("--no-map-unknown-objects", commandSpec)) {
                options.setMapUnsupportedTypesToGenerics(mapUnknownObjects);
            }
        } else {
            options = new CityJSONFormatOptions()
                    .setMapUnsupportedTypesToGenerics(mapUnknownObjects);
        }

        if (filterOptions != null
                && filterOptions.getAppearanceOptions() != null) {
            getAppearanceOptions(options)
                    .setReadAppearances(filterOptions.getAppearanceOptions().isProcessAppearances())
                    .setThemes(filterOptions.getAppearanceOptions().getThemes());
        }

        return options;
    }

    private org.citydb.io.citygml.reader.options.AppearanceOptions getAppearanceOptions(FormatOptions<?> formatOptions) {
        org.citydb.io.citygml.reader.options.AppearanceOptions appearanceOptions = formatOptions.getAppearanceOptions()
                .orElseGet(org.citydb.io.citygml.reader.options.AppearanceOptions::new);
        formatOptions.setAppearanceOptions(appearanceOptions);
        return appearanceOptions;
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.cityjson;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.AppearanceOptions;
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

    @CommandLine.ArgGroup(exclusive = false)
    private AppearanceOptions appearanceOptions;

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
            options = formatOptions.getOrElse(CityJSONFormatOptions.class, CityJSONFormatOptions::new);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get CityJSON format options from config.", e);
        }

        if (Command.hasMatchedOption("--no-map-unknown-objects", commandSpec)) {
            options.setMapUnsupportedTypesToGenerics(mapUnknownObjects);
        }

        if (appearanceOptions != null) {
            getAppearanceOptions(options)
                    .setReadAppearances(appearanceOptions.isProcessAppearances())
                    .setThemes(appearanceOptions.getThemes());
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

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.citygml;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.AppearanceOptions;
import org.citydb.cli.common.UpgradeOptions;
import org.citydb.cli.importer.ImportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.reader.CityGMLFormatOptions;
import org.citydb.io.citygml.reader.options.FormatOptions;
import org.citydb.io.reader.options.InputFormatOptions;
import picocli.CommandLine;

@CommandLine.Command(
        name = "citygml",
        description = "Import data in CityGML format.")
public class CityGMLImportCommand extends ImportController {
    @CommandLine.Option(names = "--import-xal-source",
            description = "Import XML snippets of xAL address elements.")
    private Boolean importXALSource;

    @CommandLine.Option(names = {"-x", "--xsl-transform"}, split = ",", paramLabel = "<stylesheet>",
            description = "Apply XSLT stylesheets to transform input.")
    private String[] stylesheets;

    @CommandLine.ArgGroup(exclusive = false)
    private AppearanceOptions appearanceOptions;

    @CommandLine.ArgGroup(exclusive = false, order = ARG_GROUP_ORDER,
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
            options.setIncludeXALSource(importXALSource);
        }

        if (stylesheets != null) {
            for (String stylesheet : stylesheets) {
                options.addXslTransform(helper.resolveAgainstWorkingDir(stylesheet));
            }
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

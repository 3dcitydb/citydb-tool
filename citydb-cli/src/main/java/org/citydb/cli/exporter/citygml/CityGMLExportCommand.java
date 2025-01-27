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

package org.citydb.cli.exporter.citygml;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.common.Command;
import org.citydb.cli.common.UpgradeOptions;
import org.citydb.cli.exporter.ExportController;
import org.citydb.config.ConfigException;
import org.citydb.config.common.ConfigObject;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.writer.CityGMLFormatOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citygml4j.core.model.CityGMLVersion;
import picocli.CommandLine;

import java.util.Arrays;

@CommandLine.Command(
        name = "citygml",
        description = "Export data in CityGML format.")
public class CityGMLExportCommand extends ExportController {
    @CommandLine.Option(names = {"-v", "--citygml-version"}, required = true, defaultValue = "3.0",
            description = "CityGML version: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private CityGMLVersion version;

    @CommandLine.Option(names = "--no-pretty-print", negatable = true, defaultValue = "true",
            description = "Format and indent output file (default: ${DEFAULT-VALUE}).")
    private boolean prettyPrint;

    @CommandLine.Option(names = {"-x", "--xsl-transform"}, split = ",", paramLabel = "<stylesheet>",
            description = "Apply XSLT stylesheets to transform output.")
    private String[] stylesheets;

    @CommandLine.ArgGroup(exclusive = false, order = ARG_GROUP_ORDER,
            heading = "Upgrade options for CityGML 2.0 and 1.0:%n")
    private UpgradeOptions upgradeOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityGMLAdapter.class);
    }

    @Override
    protected OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions) throws ExecutionException {
        CityGMLFormatOptions options;
        try {
            options = formatOptions.get(CityGMLFormatOptions.class);
        } catch (ConfigException e) {
            throw new ExecutionException("Failed to get CityGML format options from config.", e);
        }

        if (options != null) {
            if (Command.hasMatchedOption("--citygml-version", commandSpec)) {
                options.setVersion(version);
            }

            if (Command.hasMatchedOption("--no-pretty-print", commandSpec)) {
                options.setPrettyPrint(prettyPrint);
            }
        } else {
            options = new CityGMLFormatOptions()
                    .setVersion(version)
                    .setPrettyPrint(prettyPrint);
        }

        if (stylesheets != null) {
            options.setXslTransforms(Arrays.asList(stylesheets));
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

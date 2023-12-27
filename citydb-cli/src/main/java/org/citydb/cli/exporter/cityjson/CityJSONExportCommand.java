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

package org.citydb.cli.exporter.cityjson;

import org.citydb.cli.ExecutionException;
import org.citydb.cli.command.Command;
import org.citydb.cli.exporter.ExportController;
import org.citydb.config.ConfigObject;
import org.citydb.database.DatabaseManager;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterManager;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.citygml.writer.CityJSONFormatOptions;
import org.citydb.io.writer.option.OutputFormatOptions;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.operation.exporter.Exporter;
import org.citygml4j.cityjson.adapter.appearance.serializer.AppearanceSerializer;
import org.citygml4j.cityjson.adapter.geometry.serializer.GeometrySerializer;
import org.citygml4j.cityjson.model.CityJSONVersion;
import picocli.CommandLine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.IdentityHashMap;
import java.util.Map;

@CommandLine.Command(
        name = "cityjson",
        description = "Export data in CityJSON format.")
public class CityJSONExportCommand extends ExportController {
    @CommandLine.Option(names = {"-v", "--cityjson-version"}, required = true, defaultValue = "2.0",
            description = "CityJSON version: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private CityJSONVersion version;

    @CommandLine.Option(names = "--no-json-lines", negatable = true, defaultValue = "true",
            description = "Write output as CityJSON Sequence in JSON Lines format (default: ${DEFAULT-VALUE}). " +
                    "This option requires CityJSON 1.1 or later.")
    private boolean jsonLines;

    @CommandLine.Option(names = "--pretty-print",
            description = "Format and indent output file.")
    private Boolean prettyPrint;

    @CommandLine.Option(names = "--html-safe",
            description = "Write JSON that is safe to embed into HTML.")
    private Boolean htmlSafe;

    @CommandLine.Option(names = "--vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for geometry vertices (default: ${DEFAULT-VALUE}).")
    private int vertexPrecision = GeometrySerializer.DEFAULT_VERTEX_PRECISION;

    @CommandLine.Option(names = "--template-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for template vertices (default: ${DEFAULT-VALUE}).")
    private int templatePrecision = GeometrySerializer.DEFAULT_TEMPLATE_PRECISION;

    @CommandLine.Option(names = "--texture-vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVertexPrecision = AppearanceSerializer.DEFAULT_TEXTURE_VERTEX_PRECISION;

    @CommandLine.Option(names = "--no-transform-coordinates", negatable = true, defaultValue = "true",
            description = "Transform coordinates to integer values when exporting in " +
                    "CityJSON 1.0 (default: ${DEFAULT-VALUE}).")
    private boolean transformCoordinates;

    @CommandLine.Option(names = "--replace-templates",
            description = "Replace template geometries with real coordinates.")
    private Boolean replaceTemplateGeometries;

    @CommandLine.Option(names = "--no-material-defaults", negatable = true, defaultValue = "true",
            description = "Use CityGML default values for material properties (default: ${DEFAULT-VALUE}).")
    private boolean useMaterialDefaults;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Upgrade options for CityGML 2.0 and 1.0:%n")
    private UpgradeOptions upgradeOptions;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    private final Map<ImplicitGeometry, String> globalTemplates = new IdentityHashMap<>();
    private volatile boolean shouldRun = true;
    private Throwable exception;

    @Override
    protected IOAdapter getIOAdapter(IOAdapterManager ioManager) {
        return ioManager.getAdapter(CityJSONAdapter.class);
    }

    @Override
    protected OutputFormatOptions getFormatOptions(ConfigObject<OutputFormatOptions> formatOptions) {
        CityJSONFormatOptions options = formatOptions.get(CityJSONFormatOptions.class);
        if (options != null) {
            if (Command.hasMatchedOption("--cityjson-version", commandSpec)) {
                options.setVersion(version);
            }

            if (Command.hasMatchedOption("--no-json-lines", commandSpec)) {
                options.setJsonLines(jsonLines);
            }

            if (Command.hasMatchedOption("--vertex-precision", commandSpec)) {
                options.setVertexPrecision(vertexPrecision);
            }

            if (Command.hasMatchedOption("--template-precision", commandSpec)) {
                options.setTemplatePrecision(templatePrecision);
            }

            if (Command.hasMatchedOption("--texture-vertex-precision", commandSpec)) {
                options.setTextureVertexPrecision(textureVertexPrecision);
            }

            if (Command.hasMatchedOption("--no-transform-coordinates", commandSpec)) {
                options.setTransformCoordinates(transformCoordinates);
            }

            if (Command.hasMatchedOption("--no-material-defaults", commandSpec)) {
                options.setUseMaterialDefaults(useMaterialDefaults);
            }
        } else {
            options = new CityJSONFormatOptions()
                    .setVersion(version)
                    .setJsonLines(jsonLines)
                    .setVertexPrecision(vertexPrecision)
                    .setTemplatePrecision(templatePrecision)
                    .setTextureVertexPrecision(textureVertexPrecision)
                    .setTransformCoordinates(transformCoordinates)
                    .setUseMaterialDefaults(useMaterialDefaults);
        }

        if (prettyPrint != null) {
            options.setPrettyPrint(prettyPrint);
        }

        if (htmlSafe != null) {
            options.setHtmlSafe(htmlSafe);
        }

        if (replaceTemplateGeometries != null) {
            options.setReplaceTemplateGeometries(replaceTemplateGeometries);
        }

        if (upgradeOptions != null && upgradeOptions.getUseLod4AsLod3() != null) {
            options.setUseLod4AsLod3(upgradeOptions.getUseLod4AsLod3());
        }

        if (!globalTemplates.isEmpty()) {
            globalTemplates.forEach(options::addGlobalTemplate);
            globalTemplates.clear();
        }

        return options;
    }

    @Override
    protected void initialize(DatabaseManager databaseManager) throws ExecutionException {
        try {
            Exporter exporter = Exporter.newInstance();
            String query = databaseManager.getAdapter().getSchemaAdapter()
                    .getRecursiveImplicitGeometryQuery(getQuery(databaseManager.getAdapter()));

            try (Connection connection = databaseManager.getAdapter().getPool().getConnection();
                 Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                exporter.startSession(databaseManager.getAdapter(), getExportOptions());
                while (shouldRun && rs.next()) {
                    long id = rs.getLong("id");
                    String lod = rs.getString("lod");

                    exporter.exportImplicitGeometry(id).whenComplete((implicitGeometry, e) -> {
                        if (implicitGeometry != null) {
                            globalTemplates.put(implicitGeometry, lod);
                        } else {
                            shouldRun = false;
                            exception = e;
                        }
                    });
                }
            } finally {
                exporter.closeSession();
            }

            if (exception != null) {
                throw exception;
            }
        } catch (Throwable e) {
            throw new ExecutionException("Failed to process global template geometries.", e);
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (version == CityJSONVersion.v1_0) {
            jsonLines = false;
        }

        if (jsonLines && prettyPrint != null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --json-lines and --pretty-print are mutually exclusive (specify only one)");
        }
    }
}

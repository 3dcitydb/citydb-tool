/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader;

import org.citydb.config.common.ConfigObject;
import org.citydb.core.file.input.RegularInputFile;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadOptions;
import org.citydb.io.reader.options.InputFormatOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.SurfaceCollection;
import org.citydb.model.property.GeometryProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Lod4AsLod3Test {
    private static final int NUMBER_OF_BUILDINGS = 2000;
    private static final String MULTI_SURFACE = """
            <gml:MultiSurface srsDimension="3">
              <gml:surfaceMember>
                <gml:Polygon>
                  <gml:exterior>
                    <gml:LinearRing>
                      <gml:posList>0 0 %1$d 1 0 %1$d 1 1 %1$d 0 1 %1$d 0 0 %1$d</gml:posList>
                    </gml:LinearRing>
                  </gml:exterior>
                </gml:Polygon>
              </gml:surfaceMember>
            </gml:MultiSurface>""";

    @TempDir
    Path tempDir;

    // Every building has a LoD3 polygon at z = 3 and a LoD4 polygon at z = 4. With useLod4AsLod3,
    // the LoD4 polygon replaces the LoD3 polygon. The buildings are read with several threads,
    // which must not affect the result.
    @Test
    void lod4ReplacesLod3WithSeveralThreads() throws Exception {
        StringBuilder content = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <core:CityModel xmlns:core="http://www.opengis.net/citygml/2.0"
                                xmlns:bldg="http://www.opengis.net/citygml/building/2.0"
                                xmlns:gml="http://www.opengis.net/gml">
                """);
        for (int i = 0; i < NUMBER_OF_BUILDINGS; i++) {
            content.append("<core:cityObjectMember><bldg:Building gml:id=\"B").append(i).append("\">")
                    .append("<bldg:lod3MultiSurface>").append(MULTI_SURFACE.formatted(3))
                    .append("</bldg:lod3MultiSurface>")
                    .append("<bldg:lod4MultiSurface>").append(MULTI_SURFACE.formatted(4))
                    .append("</bldg:lod4MultiSurface>")
                    .append("</bldg:Building></core:cityObjectMember>\n");
        }

        content.append("</core:CityModel>\n");
        Path file = tempDir.resolve("lod4.gml");
        Files.writeString(file, content);

        CityGMLAdapter adapter = new CityGMLAdapter();
        adapter.initialize(CityGMLAdapter.class.getClassLoader());

        ReadOptions options = new ReadOptions()
                .setNumberOfThreads(8)
                .setTempDirectory(tempDir)
                .setFormatOptions(ConfigObject.<InputFormatOptions>of(new CityGMLFormatOptions()
                        .setUseLod4AsLod3(true)));

        Map<String, String> results = new ConcurrentHashMap<>();
        try (FeatureReader reader = adapter.createReader(new RegularInputFile(file), options)) {
            reader.read(feature -> results.put(feature.getObjectId().orElseThrow(), describe(feature)));
        }

        Map<String, Long> counts = results.values().stream()
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        assertEquals(Map.of("3:z=4.0", (long) NUMBER_OF_BUILDINGS), counts);
    }

    private String describe(Feature feature) {
        List<String> geometries = feature.getGeometries().getAll().stream()
                .map(property -> property.getLod().orElse("-") + ":" + describe(property))
                .sorted()
                .toList();
        return String.join(",", geometries);
    }

    private String describe(GeometryProperty property) {
        return property.getObject() instanceof SurfaceCollection<?> collection
                ? collection.getPolygons().stream()
                .map(polygon -> "z=" + polygon.getExteriorRing().getPoints().get(0).getZ())
                .collect(Collectors.joining(";"))
                : "?";
    }
}

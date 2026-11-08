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
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.SurfaceCollection;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.GeometryProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CrossLodReferenceTest {
    private static final String CITYGML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <core:CityModel xmlns:core="http://www.opengis.net/citygml/2.0"
                            xmlns:bldg="http://www.opengis.net/citygml/building/2.0"
                            xmlns:app="http://www.opengis.net/citygml/appearance/2.0"
                            xmlns:gml="http://www.opengis.net/gml"
                            xmlns:xlink="http://www.w3.org/1999/xlink">
              <core:cityObjectMember>
                <bldg:Building gml:id="B1">
                  <app:appearance>
                    <app:Appearance>
                      <app:theme>material</app:theme>
                      <app:surfaceDataMember>
                        <app:X3DMaterial>
                          <app:diffuseColor>1 0 0</app:diffuseColor>
                          <app:target>#P3</app:target>
                        </app:X3DMaterial>
                      </app:surfaceDataMember>
                    </app:Appearance>
                  </app:appearance>
                  <bldg:lod3MultiSurface>
                    <gml:MultiSurface gml:id="MS3">
                      <gml:surfaceMember>
                        <gml:Polygon gml:id="P3">
                          <gml:exterior>
                            <gml:LinearRing>
                              <gml:posList srsDimension="3">0 0 0 1 0 0 1 1 0 0 1 0 0 0 0</gml:posList>
                            </gml:LinearRing>
                          </gml:exterior>
                        </gml:Polygon>
                      </gml:surfaceMember>
                    </gml:MultiSurface>
                  </bldg:lod3MultiSurface>
                  <bldg:lod4MultiSurface>
                    <gml:MultiSurface gml:id="MS4">
                      <gml:surfaceMember xlink:href="#P3"/>
                    </gml:MultiSurface>
                  </bldg:lod4MultiSurface>
                </bldg:Building>
              </core:cityObjectMember>
            </core:CityModel>
            """;

    @TempDir
    Path tempDir;

    // Building B1 has an inline LoD3 polygon P3 with a material and a LoD4 multi-surface
    // referencing P3 via xlink. Every polygon that is read must keep the material.
    @ParameterizedTest(name = "useLod4AsLod3={0}, resolveCrossLodReferences={1}")
    @CsvSource({
            "false, true,  '3:1,4:1', 2",
            "false, false, '3:1,4:1', 2",
            "true,  true,  '3:1',     1",
            "true,  false, '3:1',     1"
    })
    void lod4ReferencingLod3(boolean useLod4AsLod3, boolean resolveCrossLodReferences,
                             String expectedPolygons, int expectedMaterialTargets) throws Exception {
        Path file = tempDir.resolve("cross-lod.gml");
        Files.writeString(file, CITYGML);

        CityGMLAdapter adapter = new CityGMLAdapter();
        adapter.initialize(CityGMLAdapter.class.getClassLoader());

        ReadOptions options = new ReadOptions()
                .setNumberOfThreads(1)
                .setTempDirectory(tempDir)
                .setFormatOptions(ConfigObject.<InputFormatOptions>of(new CityGMLFormatOptions()
                        .setUseLod4AsLod3(useLod4AsLod3)
                        .setResolveCrossLodReferences(resolveCrossLodReferences)));

        List<Feature> features = new ArrayList<>();
        try (FeatureReader reader = adapter.createReader(new RegularInputFile(file), options)) {
            reader.read(features::add);
        }

        assertEquals(1, features.size());
        assertEquals(expectedPolygons, getPolygonsByLod(features.get(0)));
        assertEquals(expectedMaterialTargets, getMaterialTargets(features.get(0)));
    }

    private String getPolygonsByLod(Feature feature) {
        return String.join(",", feature.getGeometries().getAll().stream()
                .sorted(Comparator.comparing(property -> property.getLod().orElse("")))
                .map(property -> property.getLod().orElse("-") + ":" + countPolygons(property))
                .toList());
    }

    private int countPolygons(GeometryProperty property) {
        return property.getObject() instanceof SurfaceCollection<?> collection
                ? collection.getPolygons().size()
                : -1;
    }

    private long getMaterialTargets(Feature feature) {
        return feature.getAppearances().getAll().stream()
                .map(AppearanceProperty::getObject)
                .flatMap(appearance -> appearance.getSurfaceData().stream())
                .map(property -> property.getObject().orElse(null))
                .filter(X3DMaterial.class::isInstance)
                .map(X3DMaterial.class::cast)
                .mapToLong(material -> material.getTargets().size())
                .sum();
    }
}

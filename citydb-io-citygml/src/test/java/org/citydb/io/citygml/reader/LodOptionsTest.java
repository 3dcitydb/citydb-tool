/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader;

import com.alibaba.fastjson2.JSON;
import org.citydb.core.file.input.RegularInputFile;
import org.citydb.io.IOAdapter;
import org.citydb.io.citygml.CityGMLAdapter;
import org.citydb.io.citygml.CityJSONAdapter;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadOptions;
import org.citydb.io.reader.options.EmptyFeatureMode;
import org.citydb.io.reader.options.LodMode;
import org.citydb.io.reader.options.LodOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LodOptionsTest {
    private static final String CITYGML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <core:CityModel xmlns:core="http://www.opengis.net/citygml/2.0"
                            xmlns:bldg="http://www.opengis.net/citygml/building/2.0"
                            xmlns:gml="http://www.opengis.net/gml">
              <core:cityObjectMember>
                <bldg:Building gml:id="A">
                  <bldg:lod1MultiSurface>%1$s</bldg:lod1MultiSurface>
                  <bldg:lod2MultiSurface>%1$s</bldg:lod2MultiSurface>
                  <bldg:boundedBy>
                    <bldg:WallSurface gml:id="W">
                      <bldg:lod2MultiSurface>%1$s</bldg:lod2MultiSurface>
                      <bldg:lod3MultiSurface>%1$s</bldg:lod3MultiSurface>
                    </bldg:WallSurface>
                  </bldg:boundedBy>
                  <bldg:boundedBy>
                    <bldg:RoofSurface gml:id="R">
                      <bldg:lod3MultiSurface>%1$s</bldg:lod3MultiSurface>
                    </bldg:RoofSurface>
                  </bldg:boundedBy>
                  <bldg:consistsOfBuildingPart>
                    <bldg:BuildingPart gml:id="P">
                      <bldg:boundedBy>
                        <bldg:WallSurface gml:id="X">
                          <bldg:lod2MultiSurface>%1$s</bldg:lod2MultiSurface>
                        </bldg:WallSurface>
                      </bldg:boundedBy>
                    </bldg:BuildingPart>
                  </bldg:consistsOfBuildingPart>
                  <bldg:consistsOfBuildingPart>
                    <bldg:BuildingPart gml:id="Q"/>
                  </bldg:consistsOfBuildingPart>
                </bldg:Building>
              </core:cityObjectMember>
              <core:cityObjectMember>
                <bldg:Building gml:id="B">
                  <bldg:lod1MultiSurface>%1$s</bldg:lod1MultiSurface>
                  <bldg:consistsOfBuildingPart>
                    <bldg:BuildingPart gml:id="C">
                      <bldg:lod1MultiSurface>%1$s</bldg:lod1MultiSurface>
                    </bldg:BuildingPart>
                  </bldg:consistsOfBuildingPart>
                </bldg:Building>
              </core:cityObjectMember>
            </core:CityModel>
            """.formatted("""
            <gml:MultiSurface srsDimension="3">
              <gml:surfaceMember>
                <gml:Polygon>
                  <gml:exterior>
                    <gml:LinearRing>
                      <gml:posList>0 0 0 1 0 0 1 1 0 0 1 0 0 0 0</gml:posList>
                    </gml:LinearRing>
                  </gml:exterior>
                </gml:Polygon>
              </gml:surfaceMember>
            </gml:MultiSurface>""");

    private static final String CITYJSON = """
            {
              "type": "CityJSON",
              "version": "2.0",
              "transform": {"scale": [1.0, 1.0, 1.0], "translate": [0.0, 0.0, 0.0]},
              "CityObjects": {
                "A": {
                  "type": "Building",
                  "children": ["P", "Q"],
                  "geometry": [
                    {"type": "Solid", "lod": "1", "boundaries": [[[[0, 1, 2, 3]]]]},
                    {"type": "MultiSurface", "lod": "2.2", "boundaries": [[[0, 1, 2, 3]]]}
                  ]
                },
                "P": {
                  "type": "BuildingPart",
                  "parents": ["A"],
                  "geometry": [
                    {"type": "Solid", "lod": "1", "boundaries": [[[[0, 1, 2, 3]]]]}
                  ]
                },
                "Q": {
                  "type": "BuildingPart",
                  "parents": ["A"]
                },
                "B": {
                  "type": "Building",
                  "geometry": [
                    {"type": "Solid", "lod": "1", "boundaries": [[[[0, 1, 2, 3]]]]}
                  ]
                }
              },
              "vertices": [[0, 0, 0], [1, 0, 0], [1, 1, 0], [0, 1, 0]]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void cityGMLWithoutLodOptions() throws Exception {
        assertEquals(List.of(
                "Building A [1, 2] (BuildingPart P (WallSurface X [2]), BuildingPart Q, " +
                        "RoofSurface R [3], WallSurface W [2, 3])",
                "Building B [1] (BuildingPart C [1])"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, null));
    }

    @Test
    void cityGMLKeepLod() throws Exception {
        // R loses its only geometry and is removed. Q never had any geometry and is kept. P has no
        // geometry itself but is kept as structural ancestor of X. B loses all geometry and is removed.
        assertEquals(List.of(
                "Building A [2] (BuildingPart P (WallSurface X [2]), BuildingPart Q, WallSurface W [2])"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, new LodOptions().withLod(2)));
    }

    @Test
    void cityGMLKeepLodWithEmptyFeatures() throws Exception {
        // only geometry is removed
        assertEquals(List.of(
                "Building A [2] (BuildingPart P (WallSurface X [2]), BuildingPart Q, RoofSurface R, " +
                        "WallSurface W [2])",
                "Building B (BuildingPart C)"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, new LodOptions()
                .withLod(2)
                .setEmptyFeatures(EmptyFeatureMode.KEEP)));
    }

    @Test
    void cityGMLRemoveLod() throws Exception {
        // R loses its only geometry and is removed, Q never had any geometry and is kept
        assertEquals(List.of(
                "Building A [1, 2] (BuildingPart P (WallSurface X [2]), BuildingPart Q, WallSurface W [2])",
                "Building B [1] (BuildingPart C [1])"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, new LodOptions()
                .withLod(3)
                .setMode(LodMode.REMOVE)));
    }

    @Test
    void cityGMLMaximumLod() throws Exception {
        // the maximum LoD is determined per top-level feature: LoD 3 for A and LoD 1 for B. X loses its
        // only geometry and is removed together with its ancestor P, which has no other content.
        assertEquals(List.of(
                "Building A (BuildingPart Q, RoofSurface R [3], WallSurface W [3])",
                "Building B [1] (BuildingPart C [1])"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, new LodOptions().setMode(LodMode.MAXIMUM)));
    }

    @Test
    void cityGMLForIntegration() throws Exception {
        // like cityGMLKeepLod, but B is returned although it has no LoD 2 geometry left
        assertEquals(List.of(
                "Building A [2] (BuildingPart P (WallSurface X [2]), BuildingPart Q, WallSurface W [2])",
                "Building B"
        ), read(new CityGMLAdapter(), "test.gml", CITYGML, integrationOptions(2)));
    }

    @Test
    void cityJSONWithoutLodOptions() throws Exception {
        assertEquals(List.of(
                "Building A [1, 2] (BuildingPart P [1], BuildingPart Q)",
                "Building B [1]"
        ), read(new CityJSONAdapter(), "test.city.json", CITYJSON, null));
    }

    @Test
    void cityJSONKeepLod() throws Exception {
        // the sub-LoD 2.2 is selected by LoD 2. P loses its only geometry and is removed, Q never had
        // any geometry and is kept. B loses all geometry and is removed.
        assertEquals(List.of(
                "Building A [2] (BuildingPart Q)"
        ), read(new CityJSONAdapter(), "test.city.json", CITYJSON, new LodOptions().withLod(2)));
    }

    @Test
    void cityJSONForIntegration() throws Exception {
        assertEquals(List.of(
                "Building A [2] (BuildingPart Q)",
                "Building B"
        ), read(new CityJSONAdapter(), "test.city.json", CITYJSON, integrationOptions(2)));
    }

    @Test
    void lodOptionsFromJson() {
        ReadOptions options = JSON.parseObject("""
                {"lodOptions": {"lods": [2, 3], "mode": "maximum", "emptyFeatures": "keepTopLevel"}}""",
                ReadOptions.class);
        LodOptions lodOptions = options.getLodOptions().orElseThrow();
        assertEquals(Set.of(2, 3), lodOptions.getLods());
        assertEquals(LodMode.MAXIMUM, lodOptions.getMode());
        assertEquals(EmptyFeatureMode.KEEP_TOP_LEVEL, lodOptions.getEmptyFeatures());
        assertTrue(lodOptions.isUpdateExtents());
    }

    private LodOptions integrationOptions(int lod) {
        return new LodOptions()
                .withLod(lod)
                .setMode(LodMode.KEEP)
                .setEmptyFeatures(EmptyFeatureMode.KEEP_TOP_LEVEL);
    }

    private List<String> read(IOAdapter adapter, String fileName, String content, LodOptions lodOptions) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        adapter.initialize(adapter.getClass().getClassLoader());

        ReadOptions options = new ReadOptions()
                .setNumberOfThreads(1)
                .setTempDirectory(tempDir)
                .setLodOptions(lodOptions);

        List<String> features = new ArrayList<>();
        try (FeatureReader reader = adapter.createReader(new RegularInputFile(file), options)) {
            reader.read(feature -> features.add(describe(feature)));
        }

        Collections.sort(features);
        return features;
    }

    private String describe(Feature feature) {
        StringBuilder builder = new StringBuilder(feature.getFeatureType().getLocalName());
        feature.getObjectId().ifPresent(id -> builder.append(' ').append(id));

        SortedSet<String> lods = new TreeSet<>();
        Stream.concat(
                feature.getGeometries().getAll().stream().map(GeometryProperty::getLod),
                feature.getImplicitGeometries().getAll().stream().map(ImplicitGeometryProperty::getLod)
        ).forEach(lod -> lod.ifPresent(lods::add));
        if (!lods.isEmpty()) {
            builder.append(' ').append(lods.toString());
        }

        List<String> children = feature.getFeatures().getAll().stream()
                .map(FeatureProperty::getObject)
                .flatMap(Optional::stream)
                .map(this::describe)
                .sorted()
                .toList();
        if (!children.isEmpty()) {
            builder.append(" (").append(String.join(", ", children)).append(')');
        }

        return builder.toString();
    }
}

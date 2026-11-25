/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.core.json.Json;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.geometry.*;
import org.citydb.operation.importer.util.SurfaceDataMapping;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceDataMapperTest {

    @Test
    void roundTripsTextureMapping() throws Exception {
        Polygon textured = polygon("p1");
        Polygon untextured = polygon("p2");
        List<TextureCoordinate> coordinates = List.of(TextureCoordinate.of(0.1f, 0.2f),
                TextureCoordinate.of(1f, 0f), TextureCoordinate.of(0.5f, 0.75f), TextureCoordinate.of(0.1f, 0.2f));
        Matrix3x4 worldToTexture = Matrix3x4.ofRowMajor(1, 0, 0, 0.5, 0, 1, 0, 0, 0, 0, 1, 0);

        ParameterizedTexture texture = ParameterizedTexture.newInstance()
                .addTextureCoordinates(textured.getExteriorRing(), coordinates)
                .addWorldToTextureMapping(untextured, worldToTexture);

        SurfaceDataMapping mapping = org.citydb.operation.importer.util.SurfaceDataMapper.newInstance()
                .createMapping(texture)
                .get(1L);
        assertEquals("{\"p1\":[[[0.1,0.2],[1.0,0.0],[0.5,0.75],[0.1,0.2]]]}",
                Json.write(mapping.getTextureMapping()));
        assertEquals("{\"p2\":[1.0,0.0,0.0,0.5,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0]}",
                Json.write(mapping.getWorldToTextureMapping()));
        assertFalse(mapping.hasMaterialMapping());
        assertFalse(mapping.hasGeoreferencedTextureMapping());

        ParameterizedTexture exported = ParameterizedTexture.newInstance();
        SurfaceDataMapper mapper = new SurfaceDataMapper()
                .buildTextureMapping(viaDatabase(mapping.getTextureMapping()), 1, exported)
                .buildWorldToTextureMapping(viaDatabase(mapping.getWorldToTextureMapping()), 1, exported)
                .buildMaterialMapping(viaDatabase(mapping.getMaterialMapping()), 1, exported);

        assertEquals(Map.of("1#p1", List.of(toString(coordinates))), toString(mapper.getTextureMappings(exported)));
        assertEquals(Map.of("1#p2", worldToTexture.toRowMajor()), mapper.getWorldToTextureMappings(exported));
        assertTrue(mapper.getMaterialMappings(exported).isEmpty());
    }

    @Test
    void roundTripsMaterialMapping() throws Exception {
        X3DMaterial material = X3DMaterial.newInstance()
                .addTarget(polygon("p1"))
                .addTarget(polygon("p2"))
                .addTarget(polygon("p1"));

        SurfaceDataMapping mapping = org.citydb.operation.importer.util.SurfaceDataMapper.newInstance()
                .createMapping(material)
                .get(1L);
        assertEquals("{\"p1\":true,\"p2\":true}", Json.write(mapping.getMaterialMapping()));

        X3DMaterial exported = X3DMaterial.newInstance();
        SurfaceDataMapper mapper = new SurfaceDataMapper()
                .buildMaterialMapping(viaDatabase(mapping.getMaterialMapping()), 1, exported);
        assertEquals(List.of("1#p1", "1#p2"), mapper.getMaterialMappings(exported));
    }

    @Test
    void roundTripsGeoreferencedTextureMapping() throws Exception {
        GeoreferencedTexture texture = GeoreferencedTexture.newInstance()
                .addTarget(polygon("p1"))
                .addTarget(polygon("p2"));

        SurfaceDataMapping mapping = org.citydb.operation.importer.util.SurfaceDataMapper.newInstance()
                .createMapping(texture)
                .get(1L);
        assertEquals("{\"p1\":true,\"p2\":true}", Json.write(mapping.getGeoreferencedTextureMapping()));
        assertFalse(mapping.hasTextureMapping());

        GeoreferencedTexture exported = GeoreferencedTexture.newInstance();
        SurfaceDataMapper mapper = new SurfaceDataMapper()
                .buildGeoreferencedTextureMapping(viaDatabase(mapping.getGeoreferencedTextureMapping()), 1, exported);
        assertEquals(List.of("1#p1", "1#p2"), mapper.getGeoreferencedTextureMappings(exported));
    }

    @Test
    void skipsInvalidEntries() {
        ParameterizedTexture texture = ParameterizedTexture.newInstance();
        SurfaceDataMapper mapper = new SurfaceDataMapper()
                .buildTextureMapping(Json.readObject(
                        "{\"a\":1,\"b\":[[[1]],[[0.5,\"x\"]],{}],\"c\":[[[0.5,0.25,9],2],[]]}"), 2, texture)
                .buildWorldToTextureMapping(Json.readObject("{\"a\":[1,\"x\",{}],\"b\":{}}"), 2, texture)
                .buildMaterialMapping(Json.readObject(
                        "{\"a\":true,\"b\":false,\"c\":\"true\",\"d\":{\"e\":true}}"), 2, texture)
                .buildGeoreferencedTextureMapping(Json.readObject("[true]"), 2, texture);

        assertEquals(Map.of("2#c", List.of(List.of("0.5,0.25"))), toString(mapper.getTextureMappings(texture)));
        assertEquals(Map.of("2#a", Arrays.asList(1.0, null, null)), mapper.getWorldToTextureMappings(texture));
        assertEquals(List.of("2#a"), mapper.getMaterialMappings(texture));
        assertTrue(mapper.getGeoreferencedTextureMappings(texture).isEmpty());
    }

    private ObjectNode viaDatabase(ObjectNode mapping) {
        return mapping != null ? Json.readObject(Json.write(mapping)) : null;
    }

    private Map<String, List<List<String>>> toString(Map<String, List<List<TextureCoordinate>>> mappings) {
        return mappings.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().stream().map(this::toString).toList()));
    }

    private List<String> toString(List<TextureCoordinate> coordinates) {
        return coordinates.stream()
                .map(coordinate -> coordinate.getS() + "," + coordinate.getT())
                .toList();
    }

    private Polygon polygon(String objectId) {
        return Polygon.of(LinearRing.of(List.of(Coordinate.of(0, 0, 0), Coordinate.of(1, 0, 0),
                        Coordinate.of(1, 1, 0), Coordinate.of(0, 0, 0))))
                .setObjectId(objectId)
                .setDescriptor(GeometryDescriptor.of(1, 1));
    }
}

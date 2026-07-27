/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.util.ColorUtils;
import org.citydb.vis.util.JsonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code 3dSceneLayer.json} — the layer manifest ArcGIS Pro / Online and
 * CesiumJS parse before touching a single byte of geometry. It is the
 * declaration half of a contract whose other half is already pinned by
 * {@code I3SGeometryEncoderTest}: that test locks the binary lane order the
 * encoder writes, this one locks the schema the manifest promises. The two must
 * stay in lock-step — a lane emitted but not declared (or declared but not
 * emitted) is accepted at export time and only surfaces as a client-side parse
 * failure or silently missing geometry.
 * <p>
 * Assertions run against the serialized JSON via {@link JsonHelper} (the same
 * writer configuration production uses; {@code I3SJsonSerializer} only adds the
 * {@code index.json} file name), so field-visibility and null-skipping
 * behaviour are covered too — several of the invariants below are expressed as
 * the <em>absence</em> of a key.
 * <p>
 * Guards in particular the fixed definition indices ({@code UNTEXTURED=0} …
 * {@code STYLED_COLORED_BLEND=5}): node pages address geometry and material
 * definitions positionally, so a slot that shifts under one flag combination
 * repoints every mesh in the layer at the wrong material.
 */
class SceneLayerDescriptorTest {

    private static final double[] EXTENT = {8.1, 48.7, 10.0, 8.2, 48.8, 30.0};

    private static final List<AttrField> FIELDS = List.of(
            new AttrField("featureType", AttrType.STRING),
            new AttrField("OID", AttrType.OID),
            new AttrField("OBJECTID", AttrType.STRING),
            new AttrField("storeys", AttrType.INT),
            new AttrField("height", AttrType.DOUBLE));

    // ---- definition slots --------------------------------------------------

    @Test
    void definitionSlotCountsMatchTheDeclaredIndicesForEveryFlagCombination(@TempDir Path dir)
            throws IOException {
        // hasTextures / hasColors / hasStyleOverrides -> expected slot count.
        // Placeholder slots exist purely so the constants below stay valid.
        assertSlotCount(dir, false, false, false, 1);
        assertSlotCount(dir, true, false, false, 2);
        // No textures but colors: slot 1 is a placeholder so the colored pair
        // still lands on 2/3.
        assertSlotCount(dir, false, true, false, 4);
        assertSlotCount(dir, true, true, false, 4);
        // Style overrides always allocate the X3DMaterial pair as placeholders
        // so the styled pair stays on 4/5.
        assertSlotCount(dir, false, false, true, 6);
        assertSlotCount(dir, true, false, true, 6);
        assertSlotCount(dir, false, true, true, 6);
        assertSlotCount(dir, true, true, true, 6);
    }

    private void assertSlotCount(Path dir, boolean textures, boolean colors, boolean styles,
                                 int expected) throws IOException {
        JSONObject json = descriptor(dir, textures, colors, styles, false,
                DefaultObjectStyle.defaults());
        String where = "textures=" + textures + " colors=" + colors + " styles=" + styles;

        assertEquals(expected, json.getJSONArray("geometryDefinitions").size(),
                "geometryDefinitions count for " + where);
        // The two lists are indexed by the SAME node-page value, so any
        // divergence in length means some slot has no material at all.
        assertEquals(expected, json.getJSONArray("materialDefinitions").size(),
                "materialDefinitions must stay 1:1 with geometryDefinitions for " + where);
    }

    @Test
    void everySlotDeclaresUv0AndTheColorLaneExactlyWhereItIsEmitted(@TempDir Path dir)
            throws IOException {
        JSONArray definitions = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions");

        for (int i = 0; i < definitions.size(); i++) {
            // CesiumJS's I3SLayer._findBestGeometryBuffers looks slots up by
            // the hardcoded key ["position", "uv0"] — an untextured slot that
            // omits uv0 is simply never selected.
            assertNotNull(buffer(definitions, i).getJSONObject("uv0"), "uv0 on slot " + i);
            assertNotNull(buffer(definitions, i).getJSONObject("position"), "position on slot " + i);
        }

        // Slot 0 is the plain slot; the encoder never emits COLOR_0 there.
        assertNull(buffer(definitions, SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .getJSONObject("color"), "the untextured slot must not declare COLOR_0");
        // Every colored slot declares it.
        for (int i : new int[]{
                SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX,
                SceneLayerDescriptor.VERTEX_COLORED_OPAQUE_DEFINITION_INDEX,
                SceneLayerDescriptor.VERTEX_COLORED_BLEND_DEFINITION_INDEX,
                SceneLayerDescriptor.STYLED_COLORED_OPAQUE_DEFINITION_INDEX,
                SceneLayerDescriptor.STYLED_COLORED_BLEND_DEFINITION_INDEX}) {
            assertNotNull(buffer(definitions, i).getJSONObject("color"),
                    "COLOR_0 must be declared on slot " + i);
        }
    }

    @Test
    void texturedSlotDropsTheColorLaneWhenTheLayerHasNoX3dMaterial(@TempDir Path dir)
            throws IOException {
        JSONArray definitions = descriptor(dir, true, false, false, false,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions");

        assertNull(buffer(definitions, SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX)
                        .getJSONObject("color"),
                "a textured layer without baked colors must not declare COLOR_0");
    }

    @Test
    void normalLaneIsPresentOnEverySlotExactlyWhenShadingIsEnabled(@TempDir Path dir)
            throws IOException {
        // ArcGIS Pro / Online refuse a layer whose legacy buffer omits NORMAL,
        // which is why --enable-shading is mandatory for those targets. The
        // toggle must move every slot together with the encoder.
        JSONArray unlit = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions");
        for (int i = 0; i < unlit.size(); i++) {
            assertNull(buffer(unlit, i).getJSONObject("normal"),
                    "slot " + i + " must not declare NORMAL without --enable-shading");
        }

        JSONArray shaded = descriptor(dir, true, true, true, true,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions");
        assertEquals(unlit.size(), shaded.size(), "shading must not change the slot count");
        for (int i = 0; i < shaded.size(); i++) {
            JSONObject normal = buffer(shaded, i).getJSONObject("normal");
            assertNotNull(normal, "slot " + i + " must declare NORMAL with --enable-shading");
            assertEquals("Float32", normal.getString("type"));
            assertEquals(3, normal.getIntValue("component"));
        }
    }

    @Test
    void arcgisTargetConfigDeclaresNormalOnTheTexturedSlotWithoutAColorLane(@TempDir Path dir)
            throws IOException {
        // The canonical ArcGIS export: textured, --enable-shading, no
        // X3DMaterial. It is the only combination that reaches the
        // textured-shaded buffer flavour, and getting it wrong is what makes
        // Pro refuse the SLPK — so it gets its own case rather than riding
        // along on the all-flags-on layer above.
        JSONArray definitions = descriptor(dir, true, false, false, true,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions");
        assertEquals(2, definitions.size());

        JSONObject textured = buffer(definitions,
                SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX);
        assertNotNull(textured.getJSONObject("normal"), "ArcGIS requires NORMAL");
        assertNotNull(textured.getJSONObject("uv0"));
        assertNull(textured.getJSONObject("color"),
                "no X3DMaterial in this layer, so no COLOR_0 may be declared");
        assertNotNull(buffer(definitions, SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .getJSONObject("normal"));
    }

    @Test
    void legacyBufferDeclaresTheHeaderOffsetAndPerFeatureLanes(@TempDir Path dir)
            throws IOException {
        JSONObject buffer = buffer(descriptor(dir, false, false, false, true,
                DefaultObjectStyle.defaults()).getJSONArray("geometryDefinitions"), 0);

        // offset 8 = UInt32 vertexCount + UInt32 featureCount, matching the
        // two header ints I3SGeometryEncoderTest reads off the binary stream.
        assertEquals(8, buffer.getIntValue("offset"));

        JSONObject featureId = buffer.getJSONObject("featureId");
        assertEquals("UInt64", featureId.getString("type"));
        assertEquals(1, featureId.getIntValue("component"));
        assertEquals("per-feature", featureId.getString("binding"));

        JSONObject faceRange = buffer.getJSONObject("faceRange");
        assertEquals("UInt32", faceRange.getString("type"));
        assertEquals(2, faceRange.getIntValue("component"));
        assertEquals("per-feature", faceRange.getString("binding"));

        // Vertex-bound lanes carry no binding at all (not "per-vertex").
        assertNull(buffer.getJSONObject("position").getString("binding"));
    }

    // ---- materials ---------------------------------------------------------

    @Test
    void everyMaterialDeclaresBothCullFaceAndDoubleSided(@TempDir Path dir) throws IOException {
        JSONArray materials = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).getJSONArray("materialDefinitions");

        for (int i = 0; i < materials.size(); i++) {
            JSONObject material = materials.getJSONObject(i);
            // ArcGIS Pro reads the I3S 1.6-era cullFace enum, CesiumJS reads
            // the glTF-aligned doubleSided boolean. Both are needed because
            // CityGML wall winding is routinely inconsistent.
            assertEquals("none", material.getString("cullFace"), "cullFace on material " + i);
            assertTrue(material.getBooleanValue("doubleSided"), "doubleSided on material " + i);
            assertNotNull(material.getJSONObject("pbrMetallicRoughness"), "pbr on material " + i);
        }
    }

    @Test
    void blendSlotsCarryLowercaseAlphaModeAndOpaqueSlotsCarryNone(@TempDir Path dir)
            throws IOException {
        JSONArray materials = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).getJSONArray("materialDefinitions");

        // ArcGIS Pro 3.x rejects the uppercase glTF spelling outright, so the
        // I3S spec literal is asserted verbatim.
        assertEquals("blend", materials.getJSONObject(
                SceneLayerDescriptor.VERTEX_COLORED_BLEND_DEFINITION_INDEX).getString("alphaMode"));
        assertEquals("blend", materials.getJSONObject(
                SceneLayerDescriptor.STYLED_COLORED_BLEND_DEFINITION_INDEX).getString("alphaMode"));

        // Opaque slots omit the key entirely and rely on the I3S default.
        assertNull(materials.getJSONObject(
                SceneLayerDescriptor.VERTEX_COLORED_OPAQUE_DEFINITION_INDEX).getString("alphaMode"));
        assertNull(materials.getJSONObject(
                SceneLayerDescriptor.STYLED_COLORED_OPAQUE_DEFINITION_INDEX).getString("alphaMode"));
        assertNull(materials.getJSONObject(
                SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX).getString("alphaMode"));
    }

    @Test
    void texturedMaterialBindsTextureSetZeroAndTheLayerDeclaresIt(@TempDir Path dir)
            throws IOException {
        JSONObject json = descriptor(dir, true, false, false, false,
                DefaultObjectStyle.defaults());

        JSONObject pbr = json.getJSONArray("materialDefinitions")
                .getJSONObject(SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX)
                .getJSONObject("pbrMetallicRoughness");
        JSONObject baseColorTexture = pbr.getJSONObject("baseColorTexture");
        assertNotNull(baseColorTexture, "the textured slot must bind a base color texture");
        assertEquals(0, baseColorTexture.getIntValue("textureSetDefinitionId"));
        assertEquals(0, baseColorTexture.getIntValue("texCoord"));

        // The referenced texture set must actually be declared, as JPEG.
        JSONArray sets = json.getJSONArray("textureSetDefinitions");
        assertEquals(1, sets.size());
        assertEquals("jpg", sets.getJSONObject(0).getJSONArray("formats")
                .getJSONObject(0).getString("format"));
        assertEquals(List.of("image/jpeg"),
                json.getJSONObject("store").getJSONArray("textureEncoding").toList(String.class));
    }

    @Test
    void untexturedLayerDeclaresNoTextureSetAndNoTextureEncoding(@TempDir Path dir)
            throws IOException {
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        assertFalse(json.containsKey("textureSetDefinitions"));
        assertFalse(json.getJSONObject("store").containsKey("textureEncoding"));
        assertNull(json.getJSONArray("materialDefinitions").getJSONObject(0)
                        .getJSONObject("pbrMetallicRoughness").getJSONObject("baseColorTexture"),
                "an untextured material must not reference a texture set");
    }

    @Test
    void defaultColorIsBakedIntoTheUntexturedMaterialAsLinearRgba(@TempDir Path dir)
            throws IOException {
        // --default-color #ff8000cc: sRGB is converted to linear for
        // baseColorFactor (glTF/I3S define the field in linear space) while
        // alpha passes through, and alpha < 1 promotes the slot to blend.
        DefaultObjectStyle style = DefaultObjectStyle.parseColor("#ff8000cc");
        JSONObject material = descriptor(dir, false, false, false, false, style)
                .getJSONArray("materialDefinitions")
                .getJSONObject(SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX);

        JSONArray factor = material.getJSONObject("pbrMetallicRoughness")
                .getJSONArray("baseColorFactor");
        assertEquals(4, factor.size());
        assertEquals(ColorUtils.srgbToLinear(1f), factor.getFloatValue(0), 1e-6);
        assertEquals(ColorUtils.srgbToLinear(0x80 / 255f), factor.getFloatValue(1), 1e-6);
        assertEquals(ColorUtils.srgbToLinear(0f), factor.getFloatValue(2), 1e-6);
        assertEquals(0xcc / 255f, factor.getFloatValue(3), 1e-6, "alpha passes through unchanged");
        assertEquals("blend", material.getString("alphaMode"));
    }

    @Test
    void defaultWhiteStyleEmitsNoBaseColorFactor(@TempDir Path dir) throws IOException {
        JSONObject pbr = descriptor(dir, false, false, false, false, DefaultObjectStyle.defaults())
                .getJSONArray("materialDefinitions")
                .getJSONObject(SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .getJSONObject("pbrMetallicRoughness");

        // Opaque white is the PBR default — emitting it would be noise.
        assertFalse(pbr.containsKey("baseColorFactor"));
    }

    // ---- attribute schema --------------------------------------------------

    @Test
    void attributeResourcesAreIndexAlignedAcrossFieldsStorageAndStatistics(@TempDir Path dir)
            throws IOException {
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        JSONArray fields = json.getJSONArray("fields");
        JSONArray storage = json.getJSONArray("attributeStorageInfo");
        JSONArray statistics = json.getJSONArray("statisticsInfo");
        JSONArray popupFields = json.getJSONObject("popupInfo").getJSONArray("fieldInfos");
        assertEquals(FIELDS.size(), fields.size());
        assertEquals(FIELDS.size(), storage.size());
        assertEquals(FIELDS.size(), statistics.size());
        assertEquals(FIELDS.size(), popupFields.size());

        // The f_<i> key is how a consumer maps a declared field to its binary
        // attribute resource and its statistics resource; all three lists are
        // addressed by the same ordinal, so a reorder in any one of them
        // silently pairs a field with another field's values.
        for (int i = 0; i < FIELDS.size(); i++) {
            String name = FIELDS.get(i).name();
            assertEquals(name, fields.getJSONObject(i).getString("name"), "fields[" + i + "]");
            assertEquals(name, storage.getJSONObject(i).getString("name"), "storage[" + i + "]");
            assertEquals("f_" + i, storage.getJSONObject(i).getString("key"));
            assertEquals(name, statistics.getJSONObject(i).getString("name"));
            assertEquals("f_" + i, statistics.getJSONObject(i).getString("key"));
            assertEquals("statistics/f_" + i + "/0", statistics.getJSONObject(i).getString("href"));
            assertEquals(name, popupFields.getJSONObject(i).getString("fieldName"));
        }
    }

    @Test
    void attrTypesMapToEsriFieldTypesAndBinaryValueTypes(@TempDir Path dir) throws IOException {
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());
        JSONArray fields = json.getJSONArray("fields");
        JSONArray storage = json.getJSONArray("attributeStorageInfo");

        assertEquals("esriFieldTypeString", fields.getJSONObject(0).getString("type"));
        assertEquals("esriFieldTypeOID", fields.getJSONObject(1).getString("type"));
        assertEquals("esriFieldTypeInteger", fields.getJSONObject(3).getString("type"));
        assertEquals("esriFieldTypeDouble", fields.getJSONObject(4).getString("type"));

        // STRING carries the two-section layout (byte counts + UTF-8 values);
        // the numeric types carry a single values section.
        JSONObject stringStorage = storage.getJSONObject(0);
        assertEquals(List.of("attributeByteCounts", "attributeValues"),
                stringStorage.getJSONArray("ordering").toList(String.class));
        assertEquals("UTF-8",
                stringStorage.getJSONObject("attributeValues").getString("encoding"));
        assertEquals(2, stringStorage.getJSONArray("header").size());

        assertEquals("Oid32", valueType(storage, 1));
        assertEquals("Int32", valueType(storage, 3));
        assertEquals("Float64", valueType(storage, 4));
        assertEquals(List.of("attributeValues"),
                storage.getJSONObject(3).getJSONArray("ordering").toList(String.class));
    }

    @Test
    void objectIdFieldNamesTheOidTypedFieldNotTheGmlIdColumn(@TempDir Path dir)
            throws IOException {
        // Our OID-typed field is named "OID" while "OBJECTID" holds the gml:id
        // string. Esri resolves the OID by convention from the name, so the
        // layer has to state it explicitly or picking breaks in ArcGIS.
        assertEquals("OID", descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).getString("objectIdField"));
    }

    @Test
    void layerWithoutAnOidFieldOmitsObjectIdField(@TempDir Path dir) throws IOException {
        JSONObject json = descriptorFor(dir, List.of(new AttrField("name", AttrType.STRING)),
                false, false, false, false, DefaultObjectStyle.defaults());

        assertFalse(json.containsKey("objectIdField"));
        assertEquals(1, json.getJSONArray("fields").size());
    }

    // ---- layer / store identity -------------------------------------------

    @Test
    void layerVersionIsALifecycleUuidWhileTheI3sVersionLivesInTheStore(@TempDir Path dir)
            throws IOException {
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        // ArcGIS Pro rejects a non-UUID here and silently drops the whole
        // fields array on parse, which breaks the identify popup while the
        // layer still renders — confusing enough to be worth pinning.
        String version = json.getString("version");
        assertEquals(version.toUpperCase(), version, "layer version must be an uppercase UUID");
        assertEquals(version, UUID.fromString(version).toString().toUpperCase());

        assertEquals("1.9", json.getJSONObject("store").getString("version"),
                "store.version carries the I3S spec version, not the layer lifecycle id");
        assertEquals("3DObject", json.getString("layerType"));
        assertEquals(List.of("View", "Query"), json.getJSONArray("capabilities").toList(String.class));
        assertEquals(0, json.getIntValue("id"));
    }

    @Test
    void storeDeclaresTheProfileCrsAndDefaultGeometrySchemaArcgisRequires(@TempDir Path dir)
            throws IOException {
        JSONObject store = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).getJSONObject("store");

        assertEquals("meshpyramids", store.getString("profile"));
        assertEquals("./nodes/root", store.getString("rootNode"));
        assertEquals("earth-centered", store.getString("normalReferenceFrame"));
        assertEquals("MeshPyramid", store.getString("lodType"));
        assertEquals("node-switching", store.getString("lodModel"));
        assertEquals("application/octet-stream", store.getString("geometryEncoding"));
        assertEquals("http://www.opengis.net/def/crs/EPSG/0/4326", store.getString("indexCRS"));
        assertEquals("http://www.opengis.net/def/crs/EPSG/0/4326", store.getString("vertexCRS"));

        // ArcGIS Pro's loader rejects a layer with no defaultGeometrySchema,
        // independently of the per-slot buffers declared above.
        JSONObject schema = store.getJSONObject("defaultGeometrySchema");
        assertNotNull(schema);
        assertEquals("triangles", schema.getString("geometryType"));
        assertEquals("PerAttributeArray", schema.getString("topology"));
        assertEquals(List.of("position", "normal", "uv0", "color"),
                schema.getJSONArray("ordering").toList(String.class));
        assertEquals(List.of("id", "faceRange"),
                schema.getJSONArray("featureAttributeOrder").toList(String.class));
    }

    @Test
    void extentsAreReorderedCorrectlyForBothConsumers(@TempDir Path dir) throws IOException {
        // Internal extent is [minX, minY, minZ, maxX, maxY, maxZ]. The store
        // wants a flat 2D box, fullExtent wants XY before Z — two different
        // reshuffles of the same array, both easy to get index-wrong.
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        assertEquals(List.of(8.1, 48.7, 8.2, 48.8),
                json.getJSONObject("store").getJSONArray("extent").toList(Double.class));

        JSONObject fullExtent = json.getJSONObject("fullExtent");
        assertEquals(8.1, fullExtent.getDoubleValue("xmin"), 1e-12);
        assertEquals(48.7, fullExtent.getDoubleValue("ymin"), 1e-12);
        assertEquals(8.2, fullExtent.getDoubleValue("xmax"), 1e-12);
        assertEquals(48.8, fullExtent.getDoubleValue("ymax"), 1e-12);
        assertEquals(10.0, fullExtent.getDoubleValue("zmin"), 1e-12);
        assertEquals(30.0, fullExtent.getDoubleValue("zmax"), 1e-12);
    }

    @Test
    void nodePagesInfoMatchesThePageSizeTheSerializerWritesWith(@TempDir Path dir)
            throws IOException {
        JSONObject nodePages = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).getJSONObject("nodePages");

        // 64 is a literal here for the same reason it is one in
        // I3SJsonSerializerTest: consumers locate a node as
        // floor(index / nodesPerPage) using THIS advertised value, so the
        // manifest and the emitted pages must break together, not drift.
        assertEquals(64, nodePages.getIntValue("nodesPerPage"));
        assertEquals("maxScreenThresholdSQ", nodePages.getString("lodSelectionMetricType"));
    }

    @Test
    void spatialAndHeightModelDeclarationsFollowTheExportCrsContract(@TempDir Path dir)
            throws IOException {
        JSONObject json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        JSONObject spatialReference = json.getJSONObject("spatialReference");
        assertEquals(4326, spatialReference.getIntValue("wkid"));
        assertEquals(4326, spatialReference.getIntValue("latestWkid"));

        JSONObject heightModel = json.getJSONObject("heightModelInfo");
        assertEquals("gravity_related_height", heightModel.getString("heightModel"));
        assertEquals("EGM96_Geoid", heightModel.getString("vertCRS"));
        assertEquals("meter", heightModel.getString("heightUnit"));
    }

    // ---- fixture -----------------------------------------------------------

    private static JSONObject descriptor(Path dir, boolean hasTextures, boolean hasColors,
                                         boolean hasStyleOverrides, boolean enableShading,
                                         DefaultObjectStyle defaultStyle) throws IOException {
        return descriptorFor(dir, FIELDS, hasTextures, hasColors, hasStyleOverrides,
                enableShading, defaultStyle);
    }

    /** Build, serialize through the production writer config, and parse back. */
    private static JSONObject descriptorFor(Path dir, List<AttrField> attrFields,
                                            boolean hasTextures, boolean hasColors,
                                            boolean hasStyleOverrides, boolean enableShading,
                                            DefaultObjectStyle defaultStyle) throws IOException {
        SceneLayer sceneLayer = new SceneLayer()
                .setName("Railway")
                .setDescription("test layer")
                .setWkid(4326)
                .setExtent(EXTENT);
        SceneLayerDescriptor descriptor = SceneLayerDescriptor.of(sceneLayer, attrFields,
                hasTextures, hasColors, hasStyleOverrides, enableShading, defaultStyle);

        Path file = dir.resolve("index.json");
        JsonHelper.writePojo(file, descriptor);
        return JSONObject.parseObject(Files.readString(file));
    }

    /** The single legacy geometry buffer declared on definition slot {@code index}. */
    private static JSONObject buffer(JSONArray definitions, int index) {
        return definitions.getJSONObject(index).getJSONArray("geometryBuffers").getJSONObject(0);
    }

    private static String valueType(JSONArray storage, int index) {
        return storage.getJSONObject(index).getJSONObject("attributeValues").getString("valueType");
    }
}

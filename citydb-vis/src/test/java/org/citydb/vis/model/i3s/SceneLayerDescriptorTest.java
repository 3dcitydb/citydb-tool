/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import org.citydb.core.json.Json;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.util.ColorUtils;
import org.citydb.vis.util.JsonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

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
        JsonNode json = descriptor(dir, textures, colors, styles, false,
                DefaultObjectStyle.defaults());
        String where = "textures=" + textures + " colors=" + colors + " styles=" + styles;

        assertEquals(expected, json.get("geometryDefinitions").size(),
                "geometryDefinitions count for " + where);
        // The two lists are indexed by the SAME node-page value, so any
        // divergence in length means some slot has no material at all.
        assertEquals(expected, json.get("materialDefinitions").size(),
                "materialDefinitions must stay 1:1 with geometryDefinitions for " + where);
    }

    @Test
    void everySlotDeclaresUv0AndTheColorLaneExactlyWhereItIsEmitted(@TempDir Path dir)
            throws IOException {
        JsonNode definitions = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).get("geometryDefinitions");

        for (int i = 0; i < definitions.size(); i++) {
            // CesiumJS's I3SLayer._findBestGeometryBuffers looks slots up by
            // the hardcoded key ["position", "uv0"] — an untextured slot that
            // omits uv0 is simply never selected.
            assertNotNull(buffer(definitions, i).get("uv0"), "uv0 on slot " + i);
            assertNotNull(buffer(definitions, i).get("position"), "position on slot " + i);
        }

        // Slot 0 is the plain slot; the encoder never emits COLOR_0 there.
        assertNull(buffer(definitions, SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .get("color"), "the untextured slot must not declare COLOR_0");
        // Every colored slot declares it.
        for (int i : new int[]{
                SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX,
                SceneLayerDescriptor.VERTEX_COLORED_OPAQUE_DEFINITION_INDEX,
                SceneLayerDescriptor.VERTEX_COLORED_BLEND_DEFINITION_INDEX,
                SceneLayerDescriptor.STYLED_COLORED_OPAQUE_DEFINITION_INDEX,
                SceneLayerDescriptor.STYLED_COLORED_BLEND_DEFINITION_INDEX}) {
            assertNotNull(buffer(definitions, i).get("color"),
                    "COLOR_0 must be declared on slot " + i);
        }
    }

    @Test
    void texturedSlotDropsTheColorLaneWhenTheLayerHasNoX3dMaterial(@TempDir Path dir)
            throws IOException {
        JsonNode definitions = descriptor(dir, true, false, false, false,
                DefaultObjectStyle.defaults()).get("geometryDefinitions");

        assertNull(buffer(definitions, SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX)
                        .get("color"),
                "a textured layer without baked colors must not declare COLOR_0");
    }

    @Test
    void normalLaneIsPresentOnEverySlotExactlyWhenShadingIsEnabled(@TempDir Path dir)
            throws IOException {
        // ArcGIS Pro / Online refuse a layer whose legacy buffer omits NORMAL,
        // which is why --enable-shading is mandatory for those targets. The
        // toggle must move every slot together with the encoder.
        JsonNode unlit = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).get("geometryDefinitions");
        for (int i = 0; i < unlit.size(); i++) {
            assertNull(buffer(unlit, i).get("normal"),
                    "slot " + i + " must not declare NORMAL without --enable-shading");
        }

        JsonNode shaded = descriptor(dir, true, true, true, true,
                DefaultObjectStyle.defaults()).get("geometryDefinitions");
        assertEquals(unlit.size(), shaded.size(), "shading must not change the slot count");
        for (int i = 0; i < shaded.size(); i++) {
            JsonNode normal = buffer(shaded, i).get("normal");
            assertNotNull(normal, "slot " + i + " must declare NORMAL with --enable-shading");
            assertEquals("Float32", normal.path("type").asString(null));
            assertEquals(3, normal.path("component").asInt(0));
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
        JsonNode definitions = descriptor(dir, true, false, false, true,
                DefaultObjectStyle.defaults()).get("geometryDefinitions");
        assertEquals(2, definitions.size());

        JsonNode textured = buffer(definitions,
                SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX);
        assertNotNull(textured.get("normal"), "ArcGIS requires NORMAL");
        assertNotNull(textured.get("uv0"));
        assertNull(textured.get("color"),
                "no X3DMaterial in this layer, so no COLOR_0 may be declared");
        assertNotNull(buffer(definitions, SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .get("normal"));
    }

    @Test
    void legacyBufferDeclaresTheHeaderOffsetAndPerFeatureLanes(@TempDir Path dir)
            throws IOException {
        JsonNode buffer = buffer(descriptor(dir, false, false, false, true,
                DefaultObjectStyle.defaults()).get("geometryDefinitions"), 0);

        // offset 8 = UInt32 vertexCount + UInt32 featureCount, matching the
        // two header ints I3SGeometryEncoderTest reads off the binary stream.
        assertEquals(8, buffer.path("offset").asInt(0));

        JsonNode featureId = buffer.get("featureId");
        assertEquals("UInt64", featureId.path("type").asString(null));
        assertEquals(1, featureId.path("component").asInt(0));
        assertEquals("per-feature", featureId.path("binding").asString(null));

        JsonNode faceRange = buffer.get("faceRange");
        assertEquals("UInt32", faceRange.path("type").asString(null));
        assertEquals(2, faceRange.path("component").asInt(0));
        assertEquals("per-feature", faceRange.path("binding").asString(null));

        // Vertex-bound lanes carry no binding at all (not "per-vertex").
        assertNull(buffer.get("position").path("binding").asString(null));
    }

    // ---- materials ---------------------------------------------------------

    @Test
    void everyMaterialDeclaresBothCullFaceAndDoubleSided(@TempDir Path dir) throws IOException {
        JsonNode materials = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).get("materialDefinitions");

        for (int i = 0; i < materials.size(); i++) {
            JsonNode material = materials.get(i);
            // ArcGIS Pro reads the I3S 1.6-era cullFace enum, CesiumJS reads
            // the glTF-aligned doubleSided boolean. Both are needed because
            // CityGML wall winding is routinely inconsistent.
            assertEquals("none", material.path("cullFace").asString(null), "cullFace on material " + i);
            assertTrue(material.path("doubleSided").asBoolean(false), "doubleSided on material " + i);
            assertNotNull(material.get("pbrMetallicRoughness"), "pbr on material " + i);
        }
    }

    @Test
    void blendSlotsCarryLowercaseAlphaModeAndOpaqueSlotsCarryNone(@TempDir Path dir)
            throws IOException {
        JsonNode materials = descriptor(dir, true, true, true, false,
                DefaultObjectStyle.defaults()).get("materialDefinitions");

        // ArcGIS Pro 3.x rejects the uppercase glTF spelling outright, so the
        // I3S spec literal is asserted verbatim.
        assertEquals("blend", materials.get(
                SceneLayerDescriptor.VERTEX_COLORED_BLEND_DEFINITION_INDEX).path("alphaMode").asString(null));
        assertEquals("blend", materials.get(
                SceneLayerDescriptor.STYLED_COLORED_BLEND_DEFINITION_INDEX).path("alphaMode").asString(null));

        // Opaque slots omit the key entirely and rely on the I3S default.
        assertNull(materials.get(
                SceneLayerDescriptor.VERTEX_COLORED_OPAQUE_DEFINITION_INDEX).path("alphaMode").asString(null));
        assertNull(materials.get(
                SceneLayerDescriptor.STYLED_COLORED_OPAQUE_DEFINITION_INDEX).path("alphaMode").asString(null));
        assertNull(materials.get(
                SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX).path("alphaMode").asString(null));
    }

    @Test
    void texturedMaterialBindsTextureSetZeroAndTheLayerDeclaresIt(@TempDir Path dir)
            throws IOException {
        JsonNode json = descriptor(dir, true, false, false, false,
                DefaultObjectStyle.defaults());

        JsonNode pbr = json.get("materialDefinitions")
                .get(SceneLayerDescriptor.TEXTURED_DEFINITION_INDEX)
                .get("pbrMetallicRoughness");
        JsonNode baseColorTexture = pbr.get("baseColorTexture");
        assertNotNull(baseColorTexture, "the textured slot must bind a base color texture");
        assertEquals(0, baseColorTexture.path("textureSetDefinitionId").asInt(0));
        assertEquals(0, baseColorTexture.path("texCoord").asInt(0));

        // The referenced texture set must actually be declared, as JPEG.
        JsonNode sets = json.get("textureSetDefinitions");
        assertEquals(1, sets.size());
        assertEquals("jpg", sets.get(0).get("formats")
                .get(0).path("format").asString(null));
        assertEquals(List.of("image/jpeg"),
                json.get("store").get("textureEncoding").valueStream().map(JsonNode::asString).toList());
    }

    @Test
    void untexturedLayerDeclaresNoTextureSetAndNoTextureEncoding(@TempDir Path dir)
            throws IOException {
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        assertFalse(json.has("textureSetDefinitions"));
        assertFalse(json.get("store").has("textureEncoding"));
        assertNull(json.get("materialDefinitions").get(0)
                        .get("pbrMetallicRoughness").get("baseColorTexture"),
                "an untextured material must not reference a texture set");
    }

    @Test
    void defaultColorIsBakedIntoTheUntexturedMaterialAsLinearRgba(@TempDir Path dir)
            throws IOException {
        // --default-color #ff8000cc: sRGB is converted to linear for
        // baseColorFactor (glTF/I3S define the field in linear space) while
        // alpha passes through, and alpha < 1 promotes the slot to blend.
        DefaultObjectStyle style = DefaultObjectStyle.parseColor("#ff8000cc");
        JsonNode material = descriptor(dir, false, false, false, false, style)
                .get("materialDefinitions")
                .get(SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX);

        JsonNode factor = material.get("pbrMetallicRoughness")
                .get("baseColorFactor");
        assertEquals(4, factor.size());
        assertEquals(ColorUtils.srgbToLinear(1f), factor.path(0).asDouble(0), 1e-6);
        assertEquals(ColorUtils.srgbToLinear(0x80 / 255f), factor.path(1).asDouble(0), 1e-6);
        assertEquals(ColorUtils.srgbToLinear(0f), factor.path(2).asDouble(0), 1e-6);
        assertEquals(0xcc / 255f, factor.path(3).asDouble(0), 1e-6, "alpha passes through unchanged");
        assertEquals("blend", material.path("alphaMode").asString(null));
    }

    @Test
    void defaultWhiteStyleEmitsNoBaseColorFactor(@TempDir Path dir) throws IOException {
        JsonNode pbr = descriptor(dir, false, false, false, false, DefaultObjectStyle.defaults())
                .get("materialDefinitions")
                .get(SceneLayerDescriptor.UNTEXTURED_DEFINITION_INDEX)
                .get("pbrMetallicRoughness");

        // Opaque white is the PBR default — emitting it would be noise.
        assertFalse(pbr.has("baseColorFactor"));
    }

    // ---- attribute schema --------------------------------------------------

    @Test
    void attributeResourcesAreIndexAlignedAcrossFieldsStorageAndStatistics(@TempDir Path dir)
            throws IOException {
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        JsonNode fields = json.get("fields");
        JsonNode storage = json.get("attributeStorageInfo");
        JsonNode statistics = json.get("statisticsInfo");
        JsonNode popupFields = json.get("popupInfo").get("fieldInfos");
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
            assertEquals(name, fields.get(i).path("name").asString(null), "fields[" + i + "]");
            assertEquals(name, storage.get(i).path("name").asString(null), "storage[" + i + "]");
            assertEquals("f_" + i, storage.get(i).path("key").asString(null));
            assertEquals(name, statistics.get(i).path("name").asString(null));
            assertEquals("f_" + i, statistics.get(i).path("key").asString(null));
            assertEquals("statistics/f_" + i + "/0", statistics.get(i).path("href").asString(null));
            assertEquals(name, popupFields.get(i).path("fieldName").asString(null));
        }
    }

    @Test
    void attrTypesMapToEsriFieldTypesAndBinaryValueTypes(@TempDir Path dir) throws IOException {
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());
        JsonNode fields = json.get("fields");
        JsonNode storage = json.get("attributeStorageInfo");

        assertEquals("esriFieldTypeString", fields.get(0).path("type").asString(null));
        assertEquals("esriFieldTypeOID", fields.get(1).path("type").asString(null));
        assertEquals("esriFieldTypeInteger", fields.get(3).path("type").asString(null));
        assertEquals("esriFieldTypeDouble", fields.get(4).path("type").asString(null));

        // STRING carries the two-section layout (byte counts + UTF-8 values);
        // the numeric types carry a single values section.
        JsonNode stringStorage = storage.get(0);
        assertEquals(List.of("attributeByteCounts", "attributeValues"),
                stringStorage.get("ordering").valueStream().map(JsonNode::asString).toList());
        assertEquals("UTF-8",
                stringStorage.get("attributeValues").path("encoding").asString(null));
        assertEquals(2, stringStorage.get("header").size());

        assertEquals("Oid32", valueType(storage, 1));
        assertEquals("Int32", valueType(storage, 3));
        assertEquals("Float64", valueType(storage, 4));
        assertEquals(List.of("attributeValues"),
                storage.get(3).get("ordering").valueStream().map(JsonNode::asString).toList());
    }

    @Test
    void objectIdFieldNamesTheOidTypedFieldNotTheGmlIdColumn(@TempDir Path dir)
            throws IOException {
        // Our OID-typed field is named "OID" while "OBJECTID" holds the gml:id
        // string. Esri resolves the OID by convention from the name, so the
        // layer has to state it explicitly or picking breaks in ArcGIS.
        assertEquals("OID", descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).path("objectIdField").asString(null));
    }

    @Test
    void layerWithoutAnOidFieldOmitsObjectIdField(@TempDir Path dir) throws IOException {
        JsonNode json = descriptorFor(dir, List.of(new AttrField("name", AttrType.STRING)),
                false, false, false, false, DefaultObjectStyle.defaults());

        assertFalse(json.has("objectIdField"));
        assertEquals(1, json.get("fields").size());
    }

    // ---- layer / store identity -------------------------------------------

    @Test
    void layerVersionIsALifecycleUuidWhileTheI3sVersionLivesInTheStore(@TempDir Path dir)
            throws IOException {
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        // ArcGIS Pro rejects a non-UUID here and silently drops the whole
        // fields array on parse, which breaks the identify popup while the
        // layer still renders — confusing enough to be worth pinning.
        String version = json.path("version").asString(null);
        assertEquals(version.toUpperCase(), version, "layer version must be an uppercase UUID");
        assertEquals(version, UUID.fromString(version).toString().toUpperCase());

        assertEquals("1.9", json.get("store").path("version").asString(null),
                "store.version carries the I3S spec version, not the layer lifecycle id");
        assertEquals("3DObject", json.path("layerType").asString(null));
        assertEquals(List.of("View", "Query"), json.get("capabilities").valueStream().map(JsonNode::asString).toList());
        assertEquals(0, json.path("id").asInt(0));
    }

    @Test
    void propertiesAreWrittenInDeclarationOrder(@TempDir Path dir) throws IOException {
        // Jackson 3 sorts properties alphabetically by default; JsonHelper
        // turns that off so the model classes control the layout of the file.
        JsonNode store = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).get("store");

        assertEquals(List.of("id", "profile", "version", "resourcePattern", "rootNode", "extent",
                        "indexCRS", "vertexCRS", "normalReferenceFrame", "lodType", "lodModel",
                        "defaultGeometrySchema", "geometryEncoding"),
                List.copyOf(store.propertyNames()));
    }

    @Test
    void storeDeclaresTheProfileCrsAndDefaultGeometrySchemaArcgisRequires(@TempDir Path dir)
            throws IOException {
        JsonNode store = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).get("store");

        assertEquals("meshpyramids", store.path("profile").asString(null));
        assertEquals("./nodes/root", store.path("rootNode").asString(null));
        assertEquals("earth-centered", store.path("normalReferenceFrame").asString(null));
        assertEquals("MeshPyramid", store.path("lodType").asString(null));
        assertEquals("node-switching", store.path("lodModel").asString(null));
        assertEquals("application/octet-stream", store.path("geometryEncoding").asString(null));
        assertEquals("http://www.opengis.net/def/crs/EPSG/0/4326", store.path("indexCRS").asString(null));
        assertEquals("http://www.opengis.net/def/crs/EPSG/0/4326", store.path("vertexCRS").asString(null));

        // ArcGIS Pro's loader rejects a layer with no defaultGeometrySchema,
        // independently of the per-slot buffers declared above.
        JsonNode schema = store.get("defaultGeometrySchema");
        assertNotNull(schema);
        assertEquals("triangles", schema.path("geometryType").asString(null));
        assertEquals("PerAttributeArray", schema.path("topology").asString(null));
        assertEquals(List.of("position", "normal", "uv0", "color"),
                schema.get("ordering").valueStream().map(JsonNode::asString).toList());
        assertEquals(List.of("id", "faceRange"),
                schema.get("featureAttributeOrder").valueStream().map(JsonNode::asString).toList());
    }

    @Test
    void extentsAreReorderedCorrectlyForBothConsumers(@TempDir Path dir) throws IOException {
        // Internal extent is [minX, minY, minZ, maxX, maxY, maxZ]. The store
        // wants a flat 2D box, fullExtent wants XY before Z — two different
        // reshuffles of the same array, both easy to get index-wrong.
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        assertEquals(List.of(8.1, 48.7, 8.2, 48.8),
                json.get("store").get("extent").valueStream().map(JsonNode::doubleValue).toList());

        JsonNode fullExtent = json.get("fullExtent");
        assertEquals(8.1, fullExtent.path("xmin").asDouble(0), 1e-12);
        assertEquals(48.7, fullExtent.path("ymin").asDouble(0), 1e-12);
        assertEquals(8.2, fullExtent.path("xmax").asDouble(0), 1e-12);
        assertEquals(48.8, fullExtent.path("ymax").asDouble(0), 1e-12);
        assertEquals(10.0, fullExtent.path("zmin").asDouble(0), 1e-12);
        assertEquals(30.0, fullExtent.path("zmax").asDouble(0), 1e-12);
    }

    @Test
    void nodePagesInfoMatchesThePageSizeTheSerializerWritesWith(@TempDir Path dir)
            throws IOException {
        JsonNode nodePages = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults()).get("nodePages");

        // 64 is a literal here for the same reason it is one in
        // I3SJsonSerializerTest: consumers locate a node as
        // floor(index / nodesPerPage) using THIS advertised value, so the
        // manifest and the emitted pages must break together, not drift.
        assertEquals(64, nodePages.path("nodesPerPage").asInt(0));
        assertEquals("maxScreenThresholdSQ", nodePages.path("lodSelectionMetricType").asString(null));
    }

    @Test
    void spatialAndHeightModelDeclarationsFollowTheExportCrsContract(@TempDir Path dir)
            throws IOException {
        JsonNode json = descriptor(dir, false, false, false, false,
                DefaultObjectStyle.defaults());

        JsonNode spatialReference = json.get("spatialReference");
        assertEquals(4326, spatialReference.path("wkid").asInt(0));
        assertEquals(4326, spatialReference.path("latestWkid").asInt(0));

        JsonNode heightModel = json.get("heightModelInfo");
        assertEquals("gravity_related_height", heightModel.path("heightModel").asString(null));
        assertEquals("EGM96_Geoid", heightModel.path("vertCRS").asString(null));
        assertEquals("meter", heightModel.path("heightUnit").asString(null));
    }

    // ---- fixture -----------------------------------------------------------

    private static JsonNode descriptor(Path dir, boolean hasTextures, boolean hasColors,
                                         boolean hasStyleOverrides, boolean enableShading,
                                         DefaultObjectStyle defaultStyle) throws IOException {
        return descriptorFor(dir, FIELDS, hasTextures, hasColors, hasStyleOverrides,
                enableShading, defaultStyle);
    }

    /** Build, serialize through the production writer config, and parse back. */
    private static JsonNode descriptorFor(Path dir, List<AttrField> attrFields,
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
        return Json.readObject(Files.readString(file));
    }

    /** The single legacy geometry buffer declared on definition slot {@code index}. */
    private static JsonNode buffer(JsonNode definitions, int index) {
        return definitions.get(index).get("geometryBuffers").get(0);
    }

    private static String valueType(JsonNode storage, int index) {
        return storage.get(index).get("attributeValues").path("valueType").asString(null);
    }
}

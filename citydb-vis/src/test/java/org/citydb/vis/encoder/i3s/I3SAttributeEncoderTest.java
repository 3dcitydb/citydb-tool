/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.i3s;

import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.scene.SceneNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-node binary attribute columns ({@code attributes/f_K/0}) that
 * ArcGIS and CesiumJS parse positionally, including the two most fragile
 * conventions: the 4 padding bytes after the Float64 header (CesiumJS aligns
 * the body to the value size) and the NUL-terminated strings whose per-entry
 * byteCount includes the terminator (ArcGIS Pro's parser fails silently
 * without it, killing the identify popup).
 */
class I3SAttributeEncoderTest {
    private static final List<AttrField> FIELDS = List.of(
            new AttrField("OID", AttrType.OID),
            new AttrField("name", AttrType.STRING),
            new AttrField("measuredHeight", AttrType.DOUBLE));
    private static final List<FeatureData> FEATURES = List.of(
            new FeatureData(7L, "BLDG_1", "Building", Map.of(
                    "name", "café", "measuredHeight", 17.5)),
            new FeatureData(9L, "BLDG_2", "Building", Map.of(
                    "name", "中央车站", "measuredHeight", -3.25)));

    @Test
    void oidColumnIsCountPrefixedInt32(@TempDir Path tempDir) throws IOException {
        writeAll(tempDir);

        ByteBuffer buf = column(tempDir, 0);
        assertEquals(4 + 2 * 4, buf.remaining());
        assertEquals(2, buf.getInt());
        assertEquals(7, buf.getInt());
        assertEquals(9, buf.getInt());
    }

    @Test
    void stringColumnIsNulTerminatedWithInclusiveByteCounts(@TempDir Path tempDir)
            throws IOException {
        writeAll(tempDir);

        byte[] first = "café".getBytes(StandardCharsets.UTF_8);
        byte[] second = "中央车站".getBytes(StandardCharsets.UTF_8);

        ByteBuffer buf = column(tempDir, 1);
        assertEquals(2, buf.getInt());
        // totalBytes and each per-entry byteCount INCLUDE the NUL terminator.
        assertEquals(first.length + 1 + second.length + 1, buf.getInt());
        assertEquals(first.length + 1, buf.getInt());
        assertEquals(second.length + 1, buf.getInt());

        byte[] firstOut = new byte[first.length];
        buf.get(firstOut);
        assertEquals("café", new String(firstOut, StandardCharsets.UTF_8));
        assertEquals(0, buf.get(), "string values must be NUL-terminated");
        byte[] secondOut = new byte[second.length];
        buf.get(secondOut);
        assertEquals("中央车站", new String(secondOut, StandardCharsets.UTF_8));
        assertEquals(0, buf.get());
        assertFalse(buf.hasRemaining());
    }

    @Test
    void doubleColumnPadsHeaderToEightByteAlignment(@TempDir Path tempDir) throws IOException {
        writeAll(tempDir);

        ByteBuffer buf = column(tempDir, 2);
        assertEquals(8 + 2 * 8, buf.remaining());
        assertEquals(2, buf.getInt());
        assertEquals(0, buf.getInt(), "4 padding bytes required for Float64 body alignment");
        assertEquals(17.5, buf.getDouble());
        assertEquals(-3.25, buf.getDouble());
    }

    @Test
    void statsAccumulateForEveryWrittenField(@TempDir Path tempDir) throws IOException {
        I3SAttributeEncoder encoder = new I3SAttributeEncoder();
        encoder.writeNodeAttributes(tempDir, new SceneNode(1, 1), FIELDS, FEATURES);

        assertTrue(encoder.snapshotStats().keySet()
                .containsAll(List.of("OID", "name", "measuredHeight")));
    }

    private static void writeAll(Path tempDir) throws IOException {
        new I3SAttributeEncoder().writeNodeAttributes(
                tempDir, new SceneNode(1, 1), FIELDS, FEATURES);
    }

    private static ByteBuffer column(Path layerDir, int fieldIndex) throws IOException {
        byte[] bytes = Files.readAllBytes(layerDir.resolve("nodes").resolve("1")
                .resolve("attributes").resolve("f_" + fieldIndex).resolve("0"));
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}

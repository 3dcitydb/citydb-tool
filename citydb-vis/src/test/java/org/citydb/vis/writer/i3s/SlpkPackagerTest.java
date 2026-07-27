/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer.i3s;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the ArcGIS SLPK packaging contract, which is otherwise only
 * validated by loading the archive in ArcGIS itself:
 * <ul>
 *   <li>loose-folder paths remap to the SLPK layout ({@code layers/0/} prefix
 *       dropped, {@code <dir>/index.json} collapsed to {@code <dir>.json.gz},
 *       extensionless texture files gain {@code .jpg});</li>
 *   <li>every ZIP entry is STORED (ZIP-level compression is forbidden) while
 *       JSON/binary resources are GZIP-compressed payloads and textures stay
 *       raw;</li>
 *   <li>{@code @specialIndexFileHASH128@} is the last entry and maps
 *       MD5(lowercase path minus extension) to the exact byte range of each
 *       resource — the O(1) random-access index ArcGIS reads, and the reason
 *       the local-file-header offset math must stay correct.</li>
 * </ul>
 */
class SlpkPackagerTest {
    private static final String SCENE_LAYER_JSON = "{\"store\":{\"id\":\"test\"}}";
    private static final byte[] TEXTURE_BYTES =
            "fake-jpeg-payload".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GEOMETRY_BYTES = {1, 2, 3, 4, 5, 6, 7, 8};

    @Test
    void remapsLooseFolderToSlpkLayout(@TempDir Path tempDir) throws IOException {
        Path slpk = pack(tempDir);

        try (ZipFile zip = new ZipFile(slpk.toFile())) {
            Set<String> names = new java.util.HashSet<>();
            zip.stream().forEach(entry -> names.add(entry.getName()));

            assertEquals(Set.of(
                    "metadata.json",
                    "3dSceneLayer.json.gz",
                    "nodepages/0.json.gz",
                    "statistics/f_0/0.json.gz",
                    "nodes/2/features/0.json.gz",
                    "nodes/2/geometries/0.bin.gz",
                    "nodes/2/attributes/f_0/0.bin.gz",
                    "nodes/2/textures/0.jpg",
                    "nodes/2/shared/sharedResource.json.gz",
                    "@specialIndexFileHASH128@"), names);
        }

        // The hash index must be the physically last entry in the archive.
        String lastEntry = null;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(slpk))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                lastEntry = entry.getName();
            }
        }
        assertEquals("@specialIndexFileHASH128@", lastEntry);
    }

    @Test
    void allEntriesAreStoredAndResourcesAreGzipPayloads(@TempDir Path tempDir)
            throws IOException {
        Path slpk = pack(tempDir);

        try (ZipFile zip = new ZipFile(slpk.toFile())) {
            zip.stream().forEach(entry ->
                    assertEquals(ZipEntry.STORED, entry.getMethod(),
                            entry.getName() + " must use the STORED method"));

            // JSON resource: gunzip payload equals the loose file content.
            byte[] descriptor = zip.getInputStream(
                    zip.getEntry("3dSceneLayer.json.gz")).readAllBytes();
            assertEquals(SCENE_LAYER_JSON, new String(gunzip(descriptor), StandardCharsets.UTF_8));

            // Binary resource: gzipped as well.
            byte[] geometry = zip.getInputStream(
                    zip.getEntry("nodes/2/geometries/0.bin.gz")).readAllBytes();
            assertArrayEquals(GEOMETRY_BYTES, gunzip(geometry));

            // Textures are raw bytes — never gzipped.
            byte[] texture = zip.getInputStream(
                    zip.getEntry("nodes/2/textures/0.jpg")).readAllBytes();
            assertArrayEquals(TEXTURE_BYTES, texture);
        }
    }

    @Test
    void metadataDescribesStoreArchiveWithGzipResources(@TempDir Path tempDir)
            throws IOException {
        Path slpk = pack(tempDir);

        try (ZipFile zip = new ZipFile(slpk.toFile())) {
            JSONObject metadata = JSONObject.parseObject(new String(
                    zip.getInputStream(zip.getEntry("metadata.json")).readAllBytes(),
                    StandardCharsets.UTF_8));
            assertEquals("STORE", metadata.getString("archiveCompressionType"));
            assertEquals("GZIP", metadata.getString("resourceCompressionType"));
            assertEquals(1, metadata.getIntValue("nodeCount"));
            assertNotNull(metadata.getString("I3SVersion"));
        }
    }

    @Test
    void hashIndexAddressesEveryResourceByteRange(@TempDir Path tempDir)
            throws IOException, NoSuchAlgorithmException {
        Path slpk = pack(tempDir);
        byte[] archive = Files.readAllBytes(slpk);

        Map<String, byte[]> md5ToData = new HashMap<>();
        List<byte[]> recordKeys = new ArrayList<>();
        Map<String, long[]> md5ToRange = new HashMap<>();

        try (ZipFile zip = new ZipFile(slpk.toFile())) {
            // Parse the 28-byte records: 16 MD5 + uint64 LE offset + uint32 LE size.
            byte[] blob = zip.getInputStream(
                    zip.getEntry("@specialIndexFileHASH128@")).readAllBytes();
            assertEquals(0, blob.length % 28, "hash index must be 28-byte records");
            ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
            while (buf.hasRemaining()) {
                byte[] md5 = new byte[16];
                buf.get(md5);
                long offset = buf.getLong();
                int size = buf.getInt();
                recordKeys.add(md5);
                md5ToRange.put(hex(md5), new long[]{offset, size});
            }

            // Every entry except metadata.json and the index itself is indexed.
            List<? extends ZipEntry> indexed = zip.stream()
                    .filter(e -> !e.getName().equals("metadata.json")
                            && !e.getName().equals("@specialIndexFileHASH128@"))
                    .toList();
            assertEquals(indexed.size(), recordKeys.size());
            for (ZipEntry entry : indexed) {
                md5ToData.put(hex(md5Of(hashKey(entry.getName()))),
                        zip.getInputStream(entry).readAllBytes());
            }
        }

        // Records are sorted by unsigned MD5 comparison (ArcGIS binary-search
        // precondition).
        for (int i = 1; i < recordKeys.size(); i++) {
            assertTrue(Arrays.compareUnsigned(recordKeys.get(i - 1), recordKeys.get(i)) < 0,
                    "hash index records must be strictly sorted by unsigned MD5");
        }

        // Each record's (offset, size) must point at exactly the entry's stored
        // bytes inside the raw archive — this pins the local-file-header offset
        // arithmetic that ArcGIS's random access depends on.
        assertEquals(md5ToData.keySet(), md5ToRange.keySet());
        for (Map.Entry<String, byte[]> e : md5ToData.entrySet()) {
            long[] range = md5ToRange.get(e.getKey());
            byte[] expected = e.getValue();
            assertEquals(expected.length, (int) range[1], "size mismatch for " + e.getKey());
            byte[] actual = Arrays.copyOfRange(archive, (int) range[0],
                    (int) (range[0] + range[1]));
            assertArrayEquals(expected, actual, "offset points at wrong bytes for " + e.getKey());
        }
    }

    @Test
    void missingLayerDirectoryIsRejected(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> SlpkPackager.pack(
                tempDir.resolve("empty-scene"), tempDir.resolve("out.slpk")));
    }

    // ---- fixture -----------------------------------------------------------

    /**
     * Minimal loose I3S scene mirroring the {@link I3SWriter} folder layout:
     * one node with geometry, attributes, features doc, shared resource and an
     * extensionless texture file (the writer names textures after the format
     * key, e.g. {@code textures/0}).
     */
    private static Path pack(Path tempDir) throws IOException {
        Path scene = tempDir.resolve("scene");
        Path layer = scene.resolve("layers").resolve("0");
        write(layer.resolve("index.json"), SCENE_LAYER_JSON.getBytes(StandardCharsets.UTF_8));
        write(layer.resolve("nodepages/0/index.json"),
                "{\"nodes\":[]}".getBytes(StandardCharsets.UTF_8));
        write(layer.resolve("statistics/f_0/0/index.json"),
                "{\"stats\":{}}".getBytes(StandardCharsets.UTF_8));
        write(layer.resolve("nodes/2/features/0/index.json"),
                "{\"featureData\":[]}".getBytes(StandardCharsets.UTF_8));
        write(layer.resolve("nodes/2/geometries/0"), GEOMETRY_BYTES);
        write(layer.resolve("nodes/2/attributes/f_0/0"), new byte[]{9, 9});
        write(layer.resolve("nodes/2/shared/sharedResource/index.json"),
                "{\"materialDefinitions\":{}}".getBytes(StandardCharsets.UTF_8));
        write(layer.resolve("nodes/2/textures/0"), TEXTURE_BYTES);

        Path slpk = tempDir.resolve("out.slpk");
        SlpkPackager.pack(scene, slpk);
        return slpk;
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    /** Mirrors the packager's key derivation: lowercase, first known extension stripped. */
    private static String hashKey(String archivePath) {
        String key = archivePath.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".json.gz", ".bin.gz", ".json", ".bin",
                ".jpg", ".jpeg", ".png"}) {
            if (key.endsWith(ext)) {
                return key.substring(0, key.length() - ext.length());
            }
        }
        return key;
    }

    private static byte[] md5Of(String key) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

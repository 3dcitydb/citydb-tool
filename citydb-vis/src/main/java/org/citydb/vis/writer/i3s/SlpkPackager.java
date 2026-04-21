/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer.i3s;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.vis.util.BufferUtils;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages an I3S folder-structure export (produced by {@link I3SWriter})
 * into an ArcGIS-compatible Scene Layer Package ({@code .slpk}) archive.
 * <p>
 * An SLPK is a STORE-compressed ZIP with:
 * <ul>
 *   <li>{@code metadata.json} at the root describing archive layout</li>
 *   <li>GZIP-compressed JSON/binary resources ({@code .json.gz}, {@code .bin.gz})</li>
 *   <li>Raw (non-compressed) textures ({@code .jpg}, {@code .png})</li>
 *   <li>{@code @specialIndexFileHASH128@} — a binary hash index mapping
 *       MD5(lowercase resource path without extension) → (data offset, size)
 *       for O(1) random access by ArcGIS</li>
 * </ul>
 *
 * @see <a href="https://github.com/Esri/i3s-spec">Esri I3S specification</a>
 */
public final class SlpkPackager {
    /** Size of a ZIP local file header (signature + fields), excluding filename & extra. */
    private static final int LOCAL_FILE_HEADER_SIZE = 30;
    /** Size of a single hash index record: 16 bytes MD5 + 8 bytes offset + 4 bytes size. */
    private static final int HASH_RECORD_SIZE = 28;
    /** I3S SLPK version. */
    private static final String I3S_VERSION = "1.7";

    private SlpkPackager() {
    }

    /**
     * Package the given I3S folder into an SLPK archive.
     *
     * @param sourceFolder directory containing the I3S layer structure
     *                     (must contain {@code layers/0/index.json})
     * @param targetSlpk   output SLPK file path (typically ending in {@code .slpk})
     */
    public static void pack(Path sourceFolder, Path targetSlpk) throws IOException {
        Objects.requireNonNull(sourceFolder, "The source folder must not be null.");
        Objects.requireNonNull(targetSlpk, "The target SLPK path must not be null.");

        Path layerDir = sourceFolder.resolve("layers").resolve("0");
        if (!Files.isDirectory(layerDir)) {
            throw new IOException("I3S layer directory not found: " + layerDir);
        }

        List<Entry> entries = collectEntries(layerDir);

        int nodeCount = countNodes(layerDir.resolve("nodes"));
        String metadata = buildMetadataJson(nodeCount);

        List<HashRecord> records = new ArrayList<>(entries.size() + 1);

        Files.createDirectories(targetSlpk.getParent());
        try (OutputStream fileOut = Files.newOutputStream(targetSlpk,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
             CountingOutputStream counting = new CountingOutputStream(fileOut);
             ZipOutputStream zip = new ZipOutputStream(counting)) {

            zip.setMethod(ZipOutputStream.STORED);

            // metadata.json — no hash record needed (implicitly addressed)
            writeStoredEntry(zip, counting, "metadata.json",
                    metadata.getBytes(StandardCharsets.UTF_8), records, false);

            // All remapped entries
            for (Entry e : entries) {
                byte[] data = e.readAndEncode();
                writeStoredEntry(zip, counting, e.archivePath(), data, records, true);
            }

            // Hash index must be the last entry
            byte[] hashBlob = buildHashIndex(records);
            writeStoredEntry(zip, counting, "@specialIndexFileHASH128@",
                    hashBlob, records, false);
        }
    }

    // ---- Entry collection & path remapping --------------------------------

    /**
     * A file to include in the SLPK: source path, remapped archive path,
     * and whether the content should be GZIP-compressed.
     */
    private record Entry(Path sourceFile, String archivePath, boolean gzip) {
        byte[] readAndEncode() throws IOException {
            byte[] data = Files.readAllBytes(sourceFile);
            if (!gzip) {
                return data;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
                gz.write(data);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Walk the I3S layer folder and produce a list of entries with
     * SLPK-style archive paths.
     */
    private static List<Entry> collectEntries(Path layerDir) throws IOException {
        List<Entry> entries = new ArrayList<>();

        // 3dSceneLayer.json.gz — from layers/0/index.json
        Path sld = layerDir.resolve("index.json");
        if (Files.isRegularFile(sld)) {
            entries.add(new Entry(sld, "3dSceneLayer.json.gz", true));
        }

        // nodepages/N.json.gz — from layers/0/nodepages/N/index.json
        Path nodepagesDir = layerDir.resolve("nodepages");
        if (Files.isDirectory(nodepagesDir)) {
            try (Stream<Path> stream = Files.list(nodepagesDir)) {
                List<Path> pages = stream.filter(Files::isDirectory).toList();
                for (Path pageDir : pages) {
                    Path pageFile = pageDir.resolve("index.json");
                    if (Files.isRegularFile(pageFile)) {
                        entries.add(new Entry(pageFile,
                                "nodepages/" + pageDir.getFileName() + ".json.gz", true));
                    }
                }
            }
        }

        // nodes/*/**
        Path nodesDir = layerDir.resolve("nodes");
        if (Files.isDirectory(nodesDir)) {
            Files.walkFileTree(nodesDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = nodesDir.relativize(file).toString().replace('\\', '/');
                    String name = file.getFileName().toString();
                    String archivePath;
                    boolean gzip;

                    if (rel.contains("/textures/") || isImage(name)) {
                        // Textures are raw (uncompressed) JPEG. The I3SWriter
                        // stores them as a file named after the format key
                        // (e.g., "0" with no extension) containing JPEG bytes.
                        // ArcGIS expects .jpg extension on the archive path.
                        archivePath = "nodes/" + rel;
                        if (!isImage(name)) {
                            archivePath += ".jpg";
                        }
                        gzip = false;
                    } else if ("index.json".equals(name)) {
                        // features/K/index.json → features/K.json.gz
                        String parent = rel.substring(0, rel.lastIndexOf('/'));
                        archivePath = "nodes/" + parent + ".json.gz";
                        gzip = true;
                    } else {
                        // Binary files: geometries/N, attributes/f_K/N
                        archivePath = "nodes/" + rel + ".bin.gz";
                        gzip = true;
                    }

                    entries.add(new Entry(file, archivePath, gzip));
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return entries;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png");
    }

    private static int countNodes(Path nodesDir) throws IOException {
        if (!Files.isDirectory(nodesDir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(nodesDir)) {
            return (int) stream.filter(Files::isDirectory).count();
        }
    }

    private static String buildMetadataJson(int nodeCount) {
        JSONObject meta = new JSONObject();
        meta.put("folderPattern", "BASIC");
        meta.put("archiveCompressionType", "STORE");
        meta.put("resourceCompressionType", "GZIP");
        meta.put("I3SVersion", I3S_VERSION);
        meta.put("nodeCount", nodeCount);
        return meta.toJSONString();
    }

    // ---- ZIP writing with offset tracking ---------------------------------

    /**
     * Write a ZIP entry with {@code STORED} method (no ZIP-level compression),
     * recording the data offset for the hash index.
     */
    private static void writeStoredEntry(ZipOutputStream zip, CountingOutputStream counting,
                                         String name, byte[] data,
                                         List<HashRecord> records, boolean indexed)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());

        long headerOffset = counting.getCount();
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();

        if (indexed) {
            // Local file header = 30 bytes fixed + filename + extra
            long dataOffset = headerOffset + LOCAL_FILE_HEADER_SIZE
                    + name.getBytes(StandardCharsets.UTF_8).length;
            records.add(new HashRecord(hashKey(name), dataOffset, data.length));
        }
    }

    // ---- Hash index -------------------------------------------------------

    private record HashRecord(byte[] md5, long dataOffset, int size) {
    }

    /**
     * Compute the MD5 hash key for a resource path.
     * Strips {@code .json.gz}, {@code .bin.gz}, {@code .json}, {@code .bin}
     * extensions and lowercases the path — matches what ArcGIS expects.
     */
    private static byte[] hashKey(String archivePath) {
        String key = archivePath.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".json.gz", ".bin.gz", ".json", ".bin",
                ".jpg", ".jpeg", ".png"}) {
            if (key.endsWith(ext)) {
                key = key.substring(0, key.length() - ext.length());
                break;
            }
        }
        try {
            return MessageDigest.getInstance("MD5")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Build the {@code @specialIndexFileHASH128@} binary blob:
     * records sorted by MD5 (unsigned byte comparison), each 28 bytes:
     * 16 bytes MD5 + 8 bytes offset (little-endian uint64) + 4 bytes size (uint32).
     */
    private static byte[] buildHashIndex(List<HashRecord> records) {
        records.sort(Comparator.comparing(HashRecord::md5, Arrays::compareUnsigned));

        ByteBuffer buf = BufferUtils.allocateLittleEndian(records.size() * HASH_RECORD_SIZE);
        for (HashRecord r : records) {
            buf.put(r.md5());
            buf.putLong(r.dataOffset());
            buf.putInt(r.size());
        }
        return buf.array();
    }

    // ---- Byte-counting output stream --------------------------------------

    /**
     * Filter stream that counts every byte written. Used to recover local
     * file header offsets for the hash index.
     */
    private static final class CountingOutputStream extends FilterOutputStream {
        private long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        long getCount() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}

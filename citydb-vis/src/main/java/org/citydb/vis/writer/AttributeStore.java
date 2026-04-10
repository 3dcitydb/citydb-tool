/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Disk-backed storage for per-feature attribute data (objectId, featureType,
 * and user attributes). Keeps the JVM heap free of string data when processing
 * millions of features.
 * <p>
 * Writes are synchronized; reads use positional I/O and are thread-safe.
 */
class AttributeStore implements Closeable {
    private final Path tempFile;
    private final FileChannel channel;
    private long writePosition = 0;

    /**
     * Loaded attribute data for a single feature.
     */
    record FeatureAttrs(String objectId, String featureType,
                        Map<String, Object> attributes) {
    }

    AttributeStore(Path tempDir) throws IOException {
        tempFile = Files.createTempFile(tempDir, "i3s-attr-", ".bin");
        tempFile.toFile().deleteOnExit();
        channel = FileChannel.open(tempFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    /**
     * Store feature attributes and return a handle (file offset) for later retrieval.
     */
    synchronized long store(String objectId, String featureType,
                            Map<String, Object> attributes) throws IOException {
        byte[] data = serialize(objectId, featureType, attributes);
        ByteBuffer buf = ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(data.length);
        buf.put(data);
        buf.flip();

        long startPos = writePosition;
        long pos = startPos;
        while (buf.hasRemaining()) {
            pos += channel.write(buf, pos);
        }
        writePosition = pos;
        return startPos;
    }

    /**
     * Load attributes previously stored. Thread-safe — uses positional reads.
     */
    FeatureAttrs load(long offset) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(header, offset);
        header.flip();
        int dataSize = header.getInt();

        ByteBuffer buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        readFully(buf, offset + 4);
        buf.flip();

        return deserialize(buf);
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            channel.close();
        }
        Files.deleteIfExists(tempFile);
    }

    // ---- serialization ------------------------------------------------

    /**
     * Binary format per entry:
     * <pre>
     *   uint16 objectIdLen + bytes objectId (UTF-8)
     *   uint16 featureTypeLen + bytes featureType (UTF-8)
     *   uint16 attrCount
     *     for each attr:
     *       uint16 keyLen + bytes key (UTF-8)
     *       int8   type (0=null, 1=Long, 2=Double, 3=String)
     *       [type-specific value]
     * </pre>
     */
    private static byte[] serialize(String objectId, String featureType,
                                    Map<String, Object> attributes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            DataOutputStream dos = new DataOutputStream(baos);
            writeString(dos, objectId);
            writeString(dos, featureType);
            dos.writeShort(attributes.size());
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                writeString(dos, entry.getKey());
                Object val = entry.getValue();
                if (val instanceof Long l) {
                    dos.writeByte(1);
                    dos.writeLong(l);
                } else if (val instanceof Double d) {
                    dos.writeByte(2);
                    dos.writeDouble(d);
                } else if (val instanceof String s) {
                    dos.writeByte(3);
                    writeString(dos, s);
                } else {
                    dos.writeByte(0); // null
                }
            }
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static FeatureAttrs deserialize(ByteBuffer buf) {
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            String objectId = readString(dis);
            String featureType = readString(dis);
            int attrCount = dis.readUnsignedShort();
            Map<String, Object> attrs = new LinkedHashMap<>();
            for (int i = 0; i < attrCount; i++) {
                String key = readString(dis);
                int type = dis.readByte();
                switch (type) {
                    case 1 -> attrs.put(key, dis.readLong());
                    case 2 -> attrs.put(key, dis.readDouble());
                    case 3 -> attrs.put(key, readString(dis));
                    default -> { /* null — skip */ }
                }
            }
            return new FeatureAttrs(objectId, featureType, attrs);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = dis.readUnsignedShort();
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void readFully(ByteBuffer buf, long startPosition) throws IOException {
        int totalRead = 0;
        int needed = buf.remaining();
        while (totalRead < needed) {
            int read = channel.read(buf, startPosition + totalRead);
            if (read < 0) {
                throw new IOException("Unexpected end of attribute store file");
            }
            totalRead += read;
        }
    }
}

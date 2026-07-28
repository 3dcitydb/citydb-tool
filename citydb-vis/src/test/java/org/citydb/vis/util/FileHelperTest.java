/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FileHelper#readFully}. Every disk-backed store reads
 * fixed-width records through it, so its end-of-file behaviour decides whether
 * a truncated store surfaces as a clear {@link IOException} or as a silently
 * short — and therefore garbage — record.
 */
class FileHelperTest {

    @TempDir
    Path tempDir;

    private Path fileOf(int byteCount) throws IOException {
        byte[] content = new byte[byteCount];
        for (int i = 0; i < byteCount; i++) {
            content[i] = (byte) i;
        }
        Path file = tempDir.resolve("record.bin");
        Files.write(file, content);
        return file;
    }

    @Test
    void fillsTheBufferCompletelyFromTheGivenPosition() throws IOException {
        Path file = fileOf(64);
        ByteBuffer buffer = ByteBuffer.allocate(8);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            FileHelper.readFully(channel, buffer, 16);
        }

        assertEquals(0, buffer.remaining(), "readFully must leave the buffer full.");
        buffer.flip();
        for (int i = 0; i < 8; i++) {
            assertEquals((byte) (16 + i), buffer.get(), "byte " + i + " came from the wrong offset");
        }
    }

    @Test
    void throwsWhenTheFileEndsBeforeTheBufferIsFull() throws IOException {
        // 20 bytes of file, 16 requested from offset 12: the channel returns 8
        // and then -1. Returning a short read here would hand the caller a
        // half-populated record with stale bytes in the tail.
        Path file = fileOf(20);
        ByteBuffer buffer = ByteBuffer.allocate(16);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            IOException ex = assertThrows(IOException.class,
                    () -> FileHelper.readFully(channel, buffer, 12));
            assertTrue(ex.getMessage().contains("Unexpected end of file at position 20"),
                    "the message must name the absolute position reached: " + ex.getMessage());
        }
    }

    @Test
    void readingEntirelyBeyondTheEndFailsAtTheStartPosition() throws IOException {
        Path file = fileOf(8);
        ByteBuffer buffer = ByteBuffer.allocate(4);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            IOException ex = assertThrows(IOException.class,
                    () -> FileHelper.readFully(channel, buffer, 64));
            assertTrue(ex.getMessage().contains("position 64"),
                    "unexpected message: " + ex.getMessage());
        }
    }
}

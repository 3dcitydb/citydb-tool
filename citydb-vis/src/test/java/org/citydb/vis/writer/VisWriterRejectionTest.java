/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.core.file.FileType;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.GZipOutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.core.file.output.ZipOutputFile;
import org.citydb.io.IOAdapter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.vis.I3SAdapter;
import org.citydb.vis.Tiles3DAdapter;
import org.citydb.vis.config.ClampMode;
import org.citydb.vis.config.I3SFormatOptions;
import org.citydb.vis.config.Tiles3DFormatOptions;
import org.citydb.vis.config.VisFormatOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construction-time rejections of the visualization writers — the
 * {@link WriteException} boundary, which is what a CLI user actually sees when
 * an export cannot start.
 * <p>
 * Everything here is asserted for <b>both</b> formats: the guards live in the
 * shared {@link VisWriter} base, so a check silently lost in one subclass is
 * exactly the kind of asymmetry a single-format test would miss.
 * <p>
 * These are all <i>fail-fast</i> contracts. Each condition is detected while the
 * writer is being constructed, before any feature is handed over, because the
 * alternative — discovering an unusable output target or a bad ion token after
 * a multi-hour export has already streamed millions of features — is the
 * expensive failure mode.
 */
class VisWriterRejectionTest {

    /** The two adapters plus a factory for their matching format options. */
    private record Format(String name, Supplier<IOAdapter> adapter,
                          Supplier<VisFormatOptions> options, String extension) {
    }

    private static Stream<Format> formats() {
        return Stream.of(
                new Format("I3S", I3SAdapter::new, I3SFormatOptions::new, ".i3s"),
                new Format("3D Tiles", Tiles3DAdapter::new, Tiles3DFormatOptions::new, ".3dtiles"));
    }

    private static WriteOptions writeOptions(Path tempDir, VisFormatOptions formatOptions) {
        WriteOptions options = new WriteOptions();
        options.setTempDirectory(tempDir);
        options.setNumberOfThreads(1);
        options.getFormatOptions().set(formatOptions);
        return options;
    }

    /**
     * Zip output is rejected rather than silently written next to the archive:
     * both formats emit a <i>directory tree</i> (tileset plus tiles, or a scene
     * layer plus nodes), which a single-stream target cannot represent. I3S has
     * its own packaging switch ({@code --slpk}) for the archived form.
     */
    @Test
    void rejectsZipOutputForEveryFormat(@TempDir Path tempDir) throws IOException {
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        for (Format format : formats().toList()) {
            OutputFile archive = new ZipOutputFile("scene", tempDir.resolve("scene.zip"), workDir);
            WriteException ex = assertThrows(WriteException.class,
                    () -> format.adapter().get().createWriter(archive,
                            writeOptions(workDir, format.options().get())),
                    format.name() + " accepted archive output " + archive.getFile());
            assertTrue(ex.getMessage().contains("does not support archive output"),
                    format.name() + ": unexpected message " + ex.getMessage());
            // The message must name the format, or a user exporting both
            // cannot tell which half of the run failed.
            assertTrue(ex.getMessage().contains(format.name()),
                    format.name() + ": message does not name the format: " + ex.getMessage());
        }
    }

    /**
     * Documents a hole in the guard above rather than the intended contract:
     * {@code -o scene.gz} reaches the writer as a {@link GZipOutputFile}, which
     * extends {@link RegularOutputFile} and therefore reports
     * {@code FileType.REGULAR}. The archive check keys on the file <i>type</i>,
     * so gzip slips through and the export writes a plain {@code scene}
     * directory, silently ignoring the requested compression.
     * {@code OutputFileBuilder} maps both {@code .gz} and {@code .gzip} to this
     * class, so the CLI really can produce it.
     * <p>
     * Asserted as-is so the behaviour cannot change unnoticed in either
     * direction: tightening the guard to cover gzip should fail this test and
     * be a deliberate edit, not a silent side effect.
     */
    @Test
    void gzipOutputIsNotCaughtByTheArchiveGuard(@TempDir Path tempDir) throws Exception {
        Path workDir = Files.createDirectories(tempDir.resolve("work"));
        OutputFile gzip = new GZipOutputFile(tempDir.resolve("scene.gz"));

        assertEquals(FileType.REGULAR, gzip.getFileType(),
                "GZipOutputFile extends RegularOutputFile; if this ever changes, "
                        + "the archive guard starts covering gzip on its own.");
        new I3SAdapter().createWriter(gzip, writeOptions(workDir, new I3SFormatOptions())).close();
    }

    /**
     * A {@code --temp-dir} that is not usable as a directory must surface as a
     * {@link WriteException} naming the stores, not as a raw
     * {@link java.nio.file.FileSystemException} from whichever store happened
     * to be created first.
     */
    @Test
    void rejectsATempDirectoryThatIsAnExistingFile(@TempDir Path tempDir) throws IOException {
        Path notADirectory = Files.writeString(tempDir.resolve("temp.txt"), "occupied");

        for (Format format : formats().toList()) {
            OutputFile output = new RegularOutputFile(
                    tempDir.resolve("scene" + format.extension()));

            WriteException ex = assertThrows(WriteException.class,
                    () -> format.adapter().get().createWriter(output,
                            writeOptions(notADirectory, format.options().get())),
                    format.name() + " accepted a regular file as its temp directory");
            assertTrue(ex.getMessage().contains("disk-backed stores"),
                    format.name() + ": unexpected message " + ex.getMessage());
            assertNotNull(ex.getCause(), format.name() + ": the underlying I/O failure was swallowed");
        }
    }

    /**
     * Cesium World Terrain clamping needs an ion token, and the check happens
     * up front rather than at the first sampled feature. The token is not a
     * build-time secret: a blank or absent one is ordinary user error, so it
     * must produce a diagnosable {@link WriteException} and not a
     * {@link NullPointerException} deep in the terrain provider.
     */
    @Test
    void rejectsCesiumWorldTerrainClampingWithoutAnIonToken(@TempDir Path tempDir) throws IOException {
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        for (Format format : formats().toList()) {
            for (String token : new String[]{null, "", "   "}) {
                VisFormatOptions formatOptions = format.options().get();
                formatOptions.setClampMode(ClampMode.CESIUM_WORLD_TERRAIN);
                formatOptions.setCesiumIonToken(token);
                OutputFile output = new RegularOutputFile(
                        tempDir.resolve("scene" + format.extension()));

                WriteException ex = assertThrows(WriteException.class,
                        () -> format.adapter().get().createWriter(output,
                                writeOptions(workDir, formatOptions)),
                        format.name() + " accepted terrain clamping without a token (token="
                                + token + ")");
                assertTrue(ex.getMessage().contains("Cesium World Terrain"),
                        format.name() + ": unexpected message " + ex.getMessage());
                // The cause carries the actionable half of the diagnosis.
                assertNotNull(ex.getCause(), format.name() + ": the ion failure was swallowed");
                assertTrue(ex.getCause().getMessage().contains("ion token is required"),
                        format.name() + ": unexpected cause " + ex.getCause().getMessage());
            }
        }
    }

    /**
     * The failed-construction path must not leak the store files it already
     * created. {@code VisWriter} stands up its disk-backed stores before the
     * terrain provider, so the token failure above happens with the stores
     * open; {@code close()} never runs for a constructor that throws, which is
     * why the constructor releases them itself — and that release wipes the
     * whole temp directory tree.
     */
    @Test
    void failedConstructionLeavesNoStoreFilesBehind(@TempDir Path tempDir) throws IOException {
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        I3SFormatOptions formatOptions = new I3SFormatOptions();
        formatOptions.setClampMode(ClampMode.CESIUM_WORLD_TERRAIN);
        OutputFile output = new RegularOutputFile(tempDir.resolve("scene.i3s"));

        assertThrows(WriteException.class, () -> new I3SAdapter()
                .createWriter(output, writeOptions(workDir, formatOptions)));

        if (Files.exists(workDir)) {
            try (Stream<Path> leftovers = Files.list(workDir)) {
                assertEquals(List.of(), leftovers.toList(),
                        "the aborted constructor left store files behind in the temp directory");
            }
        }
    }
}

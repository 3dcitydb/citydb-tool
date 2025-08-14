/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.io;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.citydb.core.file.InputFile;
import org.citydb.core.file.input.GZipInputFile;
import org.citydb.core.file.input.RegularInputFile;
import org.citydb.core.file.input.ZipInputFile;
import org.citydb.io.util.MediaTypes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class InputFiles {
    private final Collection<String> pathsOrGlobPatterns;
    private final TikaConfig tikaConfig;
    private final Set<String> extensions = new HashSet<>();
    private final Set<MediaType> mediaTypes = new HashSet<>();
    private Path baseDirectory = Path.of(".").toAbsolutePath().normalize();
    private Predicate<Path> filter;

    private InputFiles(Collection<String> pathsOrGlobPatterns) throws IOException {
        this.pathsOrGlobPatterns = pathsOrGlobPatterns;
        try {
            tikaConfig = new TikaConfig();
        } catch (TikaException e) {
            throw new IOException("Failed to initialize media type detection.", e);
        }
    }

    public static InputFiles of(Collection<String> pathsOrGlobPatterns) throws IOException {
        Objects.requireNonNull(pathsOrGlobPatterns, "The list of paths or glob patterns must not be null.");
        return new InputFiles(pathsOrGlobPatterns);
    }

    public static InputFiles of(String... pathsOrGlobPatterns) throws IOException {
        Objects.requireNonNull(pathsOrGlobPatterns, "The list of paths or glob patterns must not be null.");
        return new InputFiles(Arrays.asList(pathsOrGlobPatterns));
    }

    public InputFiles withFileExtension(String extension) {
        if (extension != null) {
            extensions.add(processFileExtension(extension));
        }

        return this;
    }

    public InputFiles withFileExtensions(Collection<String> extensions) {
        if (extensions != null) {
            extensions.forEach(this::withFileExtension);
        }

        return this;
    }

    public InputFiles withMediaType(MediaType mediaType) {
        if (mediaType != null) {
            mediaTypes.add(mediaType);
        }

        return this;
    }

    public InputFiles withMediaTypes(Collection<MediaType> mediaTypes) {
        if (mediaTypes != null) {
            mediaTypes.forEach(this::withMediaType);
        }

        return this;
    }

    public InputFiles withBaseDirectory(Path baseDirectory) {
        if (baseDirectory != null) {
            this.baseDirectory = baseDirectory;
        }

        return this;
    }

    public InputFiles withFilter(Predicate<Path> filter) {
        this.filter = filter;
        return this;
    }

    public List<InputFile> find() throws IOException {
        List<InputFile> inputFiles = new ArrayList<>();
        String defaultPattern = !extensions.isEmpty() ?
                "regex:(?i).*\\.((" + String.join(")|(", extensions) + ")|(zip)|(gz)|(gzip))$" :
                "regex:.*";

        for (String pathOrGlobPattern : pathsOrGlobPatterns) {
            LinkedList<String> elements = parse(pathOrGlobPattern);
            Path path = Paths.get(elements.pop());

            if (elements.isEmpty() && Files.isRegularFile(path)) {
                detect(path, inputFiles, defaultPattern, true);
            } else {
                // construct a pattern from the path and the truncated elements
                String pattern = !elements.isEmpty() ?
                        ("glob:" + path.toAbsolutePath().normalize() + File.separator +
                                String.join(File.separator, elements)).replace("\\", "\\\\") :
                        defaultPattern;

                // find files matching the pattern
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
                try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> matcher.matches(p.toAbsolutePath().normalize()))
                            .filter(p -> filter == null || filter.test(p))
                            .forEach(p -> detect(p, inputFiles, defaultPattern, false));
                }
            }
        }

        return inputFiles;
    }

    private LinkedList<String> parse(String file) {
        Matcher matcher = Pattern.compile("[^*?{}!\\[\\]]+").matcher("");
        LinkedList<String> elements = new LinkedList<>();
        Path path = null;

        if (file.startsWith("~" + File.separator)) {
            file = System.getProperty("user.home") + file.substring(1);
        }

        do {
            if (matcher.reset(file).matches()) {
                try {
                    path = Paths.get(file);
                } catch (Exception e) {
                    //
                }
            }

            if (path == null) {
                // the file is not a valid path, possibly because of glob patterns.
                // so, let's iteratively truncate the last path element and try again.
                int index = file.lastIndexOf(File.separator);
                String pathElement = file.substring(index + 1);
                file = file.substring(0, index != -1 ? index : 0);

                // remember the truncated element
                elements.addFirst(pathElement);
            }
        } while (path == null && !file.isEmpty());

        // resolve path against the base directory
        path = path == null ?
                baseDirectory :
                baseDirectory.resolve(path);

        elements.addFirst(path.toAbsolutePath().toString());
        return elements;
    }

    private void detect(Path file, List<InputFile> inputFiles, String pattern, boolean force) {
        MediaType mediaType = getMediaType(file);
        if (mediaType.equals(MediaType.APPLICATION_ZIP)) {
            processZip(file, inputFiles, pattern);
        } else if (mediaType.equals(MediaType.application("gzip"))) {
            processGzip(file, inputFiles);
        } else if (force || isSupportedMediaType(mediaType)) {
            inputFiles.add(new RegularInputFile(file, mediaType));
        }
    }

    private void processZip(Path file, List<InputFile> inputFiles, String pattern) {
        URI uri = URI.create("jar:" + file.toAbsolutePath().toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
             Stream<Path> stream = Files.walk(fileSystem.getPath("/"))) {
            PathMatcher matcher = fileSystem.getPathMatcher(pattern);
            stream.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.toAbsolutePath().normalize()))
                    .filter(p -> filter == null || filter.test(p))
                    .forEach(p -> {
                        MediaType mediaType = getMediaType(p);
                        if (isSupportedMediaType(mediaType)) {
                            inputFiles.add(new ZipInputFile(p.toString(), file, uri, mediaType));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open ZIP file " + file + ".", e);
        }
    }

    private void processGzip(Path file, List<InputFile> inputFiles) {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(file))) {
            String fileName = file.getFileName().toString().replaceAll("\\.gz(ip)?$", "");
            MediaType mediaType = getMediaType(input, fileName);
            if (isSupportedMediaType(mediaType)) {
                inputFiles.add(new GZipInputFile(file, mediaType));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open GZIP file " + file + ".", e);
        }
    }

    private boolean isSupportedMediaType(MediaType mediaType) {
        return mediaTypes.isEmpty()
                || mediaTypes.stream().anyMatch(candidate -> MediaTypes.isEqualOrSubMediaType(candidate, mediaType));
    }

    private MediaType getMediaType(Path file) {
        try {
            return getMediaType(TikaInputStream.get(file), file.getFileName().toString());
        } catch (IOException e) {
            return MediaType.EMPTY;
        }
    }

    private MediaType getMediaType(InputStream input, String fileName) {
        return getMediaType(TikaInputStream.get(input), fileName);
    }

    private MediaType getMediaType(TikaInputStream input, String fileName) {
        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            return tikaConfig.getDetector().detect(input, metadata);
        } catch (IOException e) {
            return MediaType.EMPTY;
        }
    }

    private String processFileExtension(String extension) {
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }

        return extension.toLowerCase(Locale.ROOT);
    }
}

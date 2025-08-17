/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

import org.apache.tika.mime.MediaType;
import org.citydb.io.util.MediaTypes;

import java.util.*;
import java.util.stream.Collectors;

public class IOAdapterManager {
    private final Map<String, IOAdapterInfo> adapters = new HashMap<>();
    private Map<String, List<IOAdapterException>> exceptions;

    private IOAdapterManager() {
    }

    public static IOAdapterManager newInstance() {
        return new IOAdapterManager();
    }

    public IOAdapterManager load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public IOAdapterManager load(ClassLoader loader) {
        for (IOAdapter adapter : ServiceLoader.load(IOAdapter.class, loader)) {
            if ((exceptions != null
                    && exceptions.containsKey(adapter.getClass().getName()))
                    || adapters.containsKey(adapter.getClass().getName())) {
                continue;
            }

            try {
                register(adapter, loader, false);
            } catch (IOAdapterException e) {
                addException(adapter, "Failed to load the IO adapter " + adapter.getClass().getName() + ".", e);
            }
        }

        return this;
    }

    public IOAdapterManager register(IOAdapter adapter) throws IOAdapterException {
        return register(adapter, false);
    }

    public IOAdapterManager register(IOAdapter adapter, boolean overwriteExisting) throws IOAdapterException {
        return register(adapter, Thread.currentThread().getContextClassLoader(), overwriteExisting);
    }

    public IOAdapterManager register(IOAdapter adapter, ClassLoader loader, boolean overwriteExisting) throws IOAdapterException {
        FileFormat fileFormat = adapter.getClass().getAnnotation(FileFormat.class);
        if (fileFormat == null) {
            throw new IOAdapterException("No @FileFormat definition provided.");
        }

        String name = fileFormat.name();
        if (!overwriteExisting && adapters.values().stream().anyMatch(info -> info.name.equalsIgnoreCase(name))) {
            throw new IOAdapterException("An IO adapter has already been registered for the format '" + name + "'.");
        }

        MediaType mediaType = MediaType.parse(fileFormat.mediaType());
        if (mediaType == null) {
            mediaType = MediaType.OCTET_STREAM;
        }

        Set<String> fileExtensions = Arrays.stream(fileFormat.fileExtensions())
                .map(this::processFileExtension)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (fileExtensions.isEmpty()) {
            throw new IOAdapterException("The @FileFormat definition lacks valid file extensions.");
        }

        adapter.initialize(loader);
        adapters.put(adapter.getClass().getName(), new IOAdapterInfo(adapter, name, mediaType, fileExtensions));
        return this;
    }

    public List<IOAdapter> getAdapters() {
        return adapters.values().stream()
                .map(info -> info.adapter)
                .collect(Collectors.toList());
    }

    public <T extends IOAdapter> T getAdapter(Class<T> type) {
        IOAdapterInfo info = adapters.get(type.getName());
        return info != null && type.isInstance(info.adapter) ?
                type.cast(info.adapter) :
                null;
    }

    public IOAdapter getAdapterForFileFormat(String name) {
        return adapters.values().stream()
                .filter(info -> info.name.equalsIgnoreCase(name))
                .map(info -> info.adapter)
                .findFirst()
                .orElse(null);
    }

    public List<IOAdapter> getAdaptersForMediaType(MediaType mediaType) {
        return adapters.values().stream()
                .filter(info -> MediaTypes.isEqualOrSubMediaType(info.mediaType, mediaType))
                .map(info -> info.adapter)
                .collect(Collectors.toList());
    }

    public List<IOAdapter> getAdaptersForFileExtension(String fileExtension) {
        return adapters.values().stream()
                .filter(info -> info.fileExtensions.contains(processFileExtension(fileExtension)))
                .map(info -> info.adapter)
                .collect(Collectors.toList());
    }

    public List<String> getFileFormats() {
        return adapters.values().stream()
                .map(info -> info.name)
                .collect(Collectors.toList());
    }

    public Set<MediaType> getMediaTypes() {
        return adapters.values().stream()
                .map(info -> info.mediaType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getFileExtensions() {
        return adapters.values().stream()
                .map(info -> info.fileExtensions)
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getFileFormat(IOAdapter adapter) {
        IOAdapterInfo info = adapters.get(adapter.getClass().getName());
        return info != null ? info.name : null;
    }

    public MediaType getMediaType(IOAdapter adapter) {
        IOAdapterInfo info = adapters.get(adapter.getClass().getName());
        return info != null ? info.mediaType : null;
    }

    public Set<String> getFileExtensions(IOAdapter adapter) {
        IOAdapterInfo info = adapters.get(adapter.getClass().getName());
        return info != null ? info.fileExtensions : Collections.emptySet();
    }

    public boolean hasExceptions() {
        return exceptions != null && !exceptions.isEmpty();
    }

    public Map<String, List<IOAdapterException>> getExceptions() {
        return exceptions != null ? exceptions : Collections.emptyMap();
    }

    private void addException(IOAdapter adapter, String message, Exception cause) {
        if (exceptions == null) {
            exceptions = new HashMap<>();
        }

        exceptions.computeIfAbsent(adapter.getClass().getName(), v -> new ArrayList<>())
                .add(new IOAdapterException(message, cause));
    }

    private String processFileExtension(String fileExtension) {
        if (fileExtension != null) {
            if (fileExtension.startsWith(".")) {
                fileExtension = fileExtension.substring(1);
            }

            return !fileExtension.isEmpty() ?
                    fileExtension.toLowerCase(Locale.ROOT) :
                    null;
        } else {
            return null;
        }
    }

    private static class IOAdapterInfo {
        final IOAdapter adapter;
        final String name;
        final MediaType mediaType;
        final Set<String> fileExtensions;

        IOAdapterInfo(IOAdapter adapter, String name, MediaType mediaType, Set<String> fileExtensions) {
            this.adapter = adapter;
            this.name = name;
            this.mediaType = mediaType;
            this.fileExtensions = fileExtensions;
        }
    }
}

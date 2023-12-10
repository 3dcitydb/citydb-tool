/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.plugin;

import com.alibaba.fastjson2.JSON;
import org.citydb.core.concurrent.LazyInitializer;
import org.citydb.plugin.extension.Extension;
import org.citydb.plugin.metadata.PluginMetadata;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class PluginManager {
    private static final PluginManager instance = new PluginManager();
    private final Map<String, Plugin> plugins = new HashMap<>();
    private final Map<Class<? extends Extension>, ExtensionInfo> extensions = new HashMap<>();
    private Map<String, List<PluginException>> exceptions;
    private ClassLoader loader;

    private record ExtensionInfo(LazyInitializer<Extension, PluginException> extension, Plugin plugin) {
    }

    private PluginManager() {
    }

    public static PluginManager getInstance() {
        return instance;
    }

    public PluginManager load() {
        return load(Thread.currentThread().getContextClassLoader());
    }

    public PluginManager load(Path pluginsDirectory) throws PluginException {
        return load(pluginsDirectory, Thread.currentThread().getContextClassLoader());
    }

    public PluginManager load(Path pluginsDirectory, ClassLoader loader) throws PluginException {
        List<URL> urls = new ArrayList<>();
        if (Files.exists(pluginsDirectory)) {
            try (Stream<Path> stream = Files.walk(pluginsDirectory, FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
                stream.forEach(path -> {
                    try {
                        urls.add(path.toUri().toURL());
                    } catch (MalformedURLException e) {
                        //
                    }
                });
            } catch (Exception e) {
                throw new PluginException("Failed to load plugins from " + pluginsDirectory + ".", e);
            }
        }

        return !urls.isEmpty() ?
                load(URLClassLoader.newInstance(urls.toArray(URL[]::new), loader)) :
                load(loader);
    }

    public PluginManager load(ClassLoader loader) {
        for (Plugin plugin : ServiceLoader.load(Plugin.class, loader)) {
            if ((exceptions != null
                    && exceptions.containsKey(plugin.getClass().getName()))
                    || plugins.containsKey(plugin.getClass().getName())) {
                continue;
            }

            try {
                register(plugin, false);
            } catch (PluginException e) {
                addException(plugin, "Failed to load the plugin " + plugin.getClass().getName() + ".", e);
            }
        }

        this.loader = loader;
        return this;
    }

    public PluginManager register(Plugin plugin) throws PluginException {
        return register(plugin, false);
    }

    public PluginManager register(Plugin plugin, boolean overwriteExisting) throws PluginException {
        if (!overwriteExisting && plugins.containsKey(plugin.getClass().getName())) {
            throw new PluginException("A plugin of type " + plugin.getClass().getName() +
                    " has already been registered.");
        }

        PluginMetadata metadata;
        try {
            metadata = JSON.parseObject(plugin.getClass().getResource("plugin.json"), PluginMetadata.class);
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin metadata from plugin.json.", e);
        }

        List<Class<? extends Extension>> types = plugin.getExtensions();
        if (types != null) {
            types.forEach(type -> extensions.put(type, new ExtensionInfo(
                    LazyInitializer.of(() -> {
                        try {
                            return type.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new PluginException("Failed to initialize extension " +
                                    type.getName() + ".", e);
                        }
                    }),
                    plugin
            )));
        }

        plugins.put(plugin.getClass().getName(), plugin.initialize(metadata));
        return this;
    }

    public ClassLoader getClassLoader() {
        return loader != null ? loader : Thread.currentThread().getContextClassLoader();
    }

    public List<Plugin> getPlugins() {
        return new ArrayList<>(plugins.values());
    }

    public Plugin getPlugin(Extension extension) {
        ExtensionInfo extensionInfo = extensions.get(extension.getClass());
        return extensionInfo != null ? extensionInfo.plugin : null;
    }

    public <T extends Extension> List<T> getExtensionsIfEnabled(Class<T> type) throws PluginException {
        return getExtensions(type, true);
    }

    public <T extends Extension> List<T> getAllExtensions(Class<T> type) throws PluginException {
        return getExtensions(type, false);
    }

    private <T extends Extension> List<T> getExtensions(Class<T> type, boolean onlyEnabled) throws PluginException {
        List<T> extensions = new ArrayList<>();
        for (Map.Entry<Class<? extends Extension>, ExtensionInfo> entry : this.extensions.entrySet()) {
            if ((!onlyEnabled || entry.getValue().plugin.isEnabled()) && type.isAssignableFrom(entry.getKey())) {
                extensions.add(type.cast(entry.getValue().extension.get()));
            }
        }

        return extensions;
    }

    public boolean hasExceptions() {
        return exceptions != null && !exceptions.isEmpty();
    }

    public Map<String, List<PluginException>> getExceptions() {
        return exceptions != null ? exceptions : Collections.emptyMap();
    }

    private void addException(Plugin plugin, String message, Exception cause) {
        if (exceptions == null) {
            exceptions = new HashMap<>();
        }

        exceptions.computeIfAbsent(plugin.getClass().getName(), v -> new ArrayList<>())
                .add(new PluginException(message, cause));
    }
}

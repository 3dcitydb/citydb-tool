/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin;

import com.alibaba.fastjson2.JSON;
import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.plugin.metadata.PluginMetadata;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class PluginManager implements AutoCloseable {
    private final PluginClassLoader loader;
    private final Map<String, Plugin> plugins = new HashMap<>();
    private final Map<Class<? extends Extension>, ExtensionInfo> extensions = new HashMap<>();

    private record ExtensionInfo(LazyCheckedInitializer<Extension, PluginException> extension, Plugin plugin) {
    }

    private PluginManager(ClassLoader parent) {
        loader = new PluginClassLoader(parent);
    }

    public static PluginManager newInstance() {
        return new PluginManager(Thread.currentThread().getContextClassLoader());
    }

    public static PluginManager of(ClassLoader parent) {
        return new PluginManager(parent);
    }

    public List<PluginException> load(Path pluginsDirectory) throws PluginException {
        loader.addPath(pluginsDirectory);
        List<PluginException> failures = new ArrayList<>();

        for (Plugin plugin : ServiceLoader.load(Plugin.class, loader)) {
            if (plugins.containsKey(plugin.getClass().getName())) {
                continue;
            }

            try {
                register(plugin, false);
            } catch (PluginException e) {
                failures.add(new PluginException("Failed to load plugin " +
                        plugin.getClass().getName() + ".", e));
            }
        }

        return failures;
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
                    LazyCheckedInitializer.of(() -> {
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
        return loader;
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

    @Override
    public void close() throws IOException {
        loader.close();
    }
}

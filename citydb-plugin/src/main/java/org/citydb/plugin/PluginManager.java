/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.plugin;

import com.alibaba.fastjson2.JSON;
import org.citydb.plugin.metadata.PluginMetadata;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PluginManager implements AutoCloseable {
    private final PluginClassLoader loader;
    private final Map<String, Plugin> plugins = new HashMap<>();
    private final Map<Class<? extends Extension>, ExtensionInfo> extensions = new HashMap<>();

    private record ExtensionInfo(Extension extension, Plugin plugin) {
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

    public void register(Plugin plugin) throws PluginException {
        register(plugin, false);
    }

    public void register(Plugin plugin, boolean overwriteExisting) throws PluginException {
        if (!overwriteExisting && plugins.containsKey(plugin.getClass().getName())) {
            throw new PluginException("A plugin of type " + plugin.getClass().getName() +
                    " has already been registered.");
        }

        deregisterPlugin(plugin);

        PluginMetadata metadata = loadOrCreateMetadata(plugin);
        plugin.initialize(metadata, plugin.getBasePath() == null
                ? findBasePath(plugin)
                : plugin.getBasePath());

        plugin.getExtensions().stream()
                .filter(Objects::nonNull)
                .forEach(extension -> extensions.put(extension.getClass(), new ExtensionInfo(extension, plugin)));

        plugins.put(plugin.getClass().getName(), plugin);
    }

    public void deregisterPlugin(Plugin plugin) {
        Plugin registered = plugins.remove(plugin.getClass().getName());
        if (registered != null) {
            extensions.entrySet().removeIf(e -> e.getValue().plugin() == registered);
        }
    }

    public ClassLoader getClassLoader() {
        return loader;
    }

    public List<Plugin> getPlugins() {
        return new ArrayList<>(plugins.values());
    }

    public Plugin getPlugin(Extension extension) {
        ExtensionInfo extensionInfo = extensions.get(extension.getClass());
        return extensionInfo != null ? extensionInfo.plugin() : null;
    }

    public <T extends Extension> List<T> getExtensionsIfEnabled(Class<T> type) {
        return getExtensions(type, true);
    }

    public <T extends Extension> List<T> getAllExtensions(Class<T> type) {
        return getExtensions(type, false);
    }

    private <T extends Extension> List<T> getExtensions(Class<T> type, boolean onlyEnabled) {
        List<T> extensions = new ArrayList<>();
        for (Map.Entry<Class<? extends Extension>, ExtensionInfo> entry : this.extensions.entrySet()) {
            if ((!onlyEnabled || entry.getValue().plugin().isEnabled()) && type.isAssignableFrom(entry.getKey())) {
                extensions.add(type.cast(entry.getValue().extension()));
            }
        }

        return extensions;
    }

    private PluginMetadata loadOrCreateMetadata(Plugin plugin) throws PluginException {
        Class<?> type = plugin.getClass();
        try {
            PluginMetadata metadata = JSON.parseObject(type.getResource("plugin.json"), PluginMetadata.class);
            if (metadata == null) {
                metadata = new PluginMetadata();
            }

            if (metadata.getName().isEmpty()) {
                metadata.setName(type.getName());
            }

            return metadata;
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin metadata from plugin.json.", e);
        }
    }

    private Path findBasePath(Plugin plugin) {
        Class<?> type = plugin.getClass();
        try {
            URL location = type.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (location != null) {
                Path path = Paths.get(location.toURI());
                return Files.isRegularFile(path) ? path.getParent() : path;
            }
        } catch (Exception e) {
            //
        }

        try {
            URL resource = type.getResource(type.getSimpleName() + ".class");
            if (resource != null) {
                String classFile = type.getName().replace('.', '/') + ".class";
                String location = resource.toString();
                if (!location.endsWith(classFile)) {
                    return null;
                }

                String base = location.substring(0, location.length() - classFile.length());
                if (base.startsWith("jar:")) {
                    int separator = base.indexOf("!/");
                    if (separator < 0) {
                        return null;
                    }

                    base = base.substring(4, separator);
                }

                Path path = Paths.get(new URL(base).toURI());
                return Files.isRegularFile(path) ? path.getParent() : path;
            }
        } catch (Exception e) {
            //
        }

        return null;
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }
}

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

package org.citydb.io.citygml;

import org.atteo.classindex.ClassIndex;
import org.citydb.io.IOAdapterException;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.annotation.DatabaseTypes;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Child;
import org.citydb.model.common.Name;
import org.citygml4j.core.ade.ADEException;
import org.citygml4j.core.ade.ADERegistry;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.CityGMLContextException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CityGMLAdapterContext {
    private final CityGMLContext context;
    private final Map<String, BuilderInfo> builders = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SerializerInfo>> serializers = new ConcurrentHashMap<>();

    CityGMLAdapterContext(ClassLoader loader) throws IOAdapterException {
        try {
            ADERegistry.getInstance().loadADEs(loader);
            context = CityGMLContext.newInstance(loader);
        } catch (ADEException | CityGMLContextException e) {
            throw new IOAdapterException("Failed to create CityGML context.", e);
        }

        loadBuilders(loader);
        loadSerializers(loader);
    }

    public CityGMLContext getCityGMLContext() {
        return context;
    }

    @SuppressWarnings("unchecked")
    public <T, R extends Child> ModelBuilder<T, R> getBuilder(Class<T> sourceType, Class<R> targetType) {
        BuilderInfo info = builders.get(sourceType.getName());
        return info != null && targetType.isAssignableFrom(info.targetType) ?
                (ModelBuilder<T, R>) info.builder :
                null;
    }

    public <T, R extends ModelBuilder<T, ? extends Child>> R getBuilderByType(Class<T> sourceType, Class<R> builderType) {
        BuilderInfo info = builders.get(sourceType.getName());
        return info != null && builderType.isInstance(info.builder) ?
                builderType.cast(info.builder) :
                null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Child, R> ModelSerializer<T, R> getSerializer(Name name, Class<T> sourceType, Class<R> targetType) {
        SerializerInfo info = serializers
                .getOrDefault(name.getNamespace(), Collections.emptyMap())
                .get(name.getLocalName());
        return info != null
                && sourceType.isAssignableFrom(info.sourceType)
                && targetType.isAssignableFrom(info.targetType) ?
                (ModelSerializer<T, R>) info.serializer :
                null;
    }

    public <T, R extends ModelSerializer<?, T>> R getSerializerByType(Name name, Class<T> sourceType, Class<R> serializerType) {
        SerializerInfo info = serializers
                .getOrDefault(name.getNamespace(), Collections.emptyMap())
                .get(name.getLocalName());
        return info != null
                && sourceType.isAssignableFrom(info.targetType)
                && serializerType.isInstance(info.serializer) ?
                serializerType.cast(info.serializer) :
                null;
    }

    @SuppressWarnings("rawtypes")
    private void loadBuilders(ClassLoader loader) throws IOAdapterException {
        for (Class<? extends ModelBuilder> type : ClassIndex.getSubclasses(ModelBuilder.class, loader).stream()
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .filter(c -> c.isAnnotationPresent(DatabaseType.class) || c.isAnnotationPresent(DatabaseTypes.class))
                .toList()) {
            boolean isSetType = type.isAnnotationPresent(DatabaseType.class);
            boolean isSetTypes = type.isAnnotationPresent(DatabaseTypes.class);

            if (isSetType && isSetTypes) {
                throw new IOAdapterException("The builder " + type.getName() + " uses both @DatabaseType " +
                        "and @DatabaseTypes.");
            }

            ModelBuilder<?, ?> builder;
            try {
                builder = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IOAdapterException("The builder " + type.getName() + " lacks a default constructor.", e);
            }

            registerBuilder(getBuilderInfo(builder));
        }
    }

    @SuppressWarnings("rawtypes")
    private void loadSerializers(ClassLoader loader) throws IOAdapterException {
        for (Class<? extends ModelSerializer> type : ClassIndex.getSubclasses(ModelSerializer.class, loader).stream()
                .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                .filter(c -> c.isAnnotationPresent(DatabaseType.class) || c.isAnnotationPresent(DatabaseTypes.class))
                .toList()) {
            boolean isSetType = type.isAnnotationPresent(DatabaseType.class);
            boolean isSetTypes = type.isAnnotationPresent(DatabaseTypes.class);

            if (isSetType && isSetTypes) {
                throw new IOAdapterException("The serializer " + type.getName() + " uses both @DatabaseType " +
                        "and @DatabaseTypes.");
            }

            ModelSerializer<?, ?> serializer;
            try {
                serializer = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IOAdapterException("The serializer " + type.getName() + " lacks a default constructor.", e);
            }

            SerializerInfo info = getSerializerInfo(serializer);
            if (isSetType) {
                DatabaseType databaseType = type.getAnnotation(DatabaseType.class);
                registerSerializer(info, databaseType.namespace(), databaseType.name());
            } else if (isSetTypes) {
                DatabaseTypes databaseTypes = type.getAnnotation(DatabaseTypes.class);
                for (DatabaseType databaseType : databaseTypes.value()) {
                    registerSerializer(info, databaseType.namespace(), databaseType.name());
                }
            }
        }
    }

    private void registerBuilder(BuilderInfo info) throws IOAdapterException {
        BuilderInfo current = builders.put(info.sourceType.getName(), info);
        if (current != null && current.builder != info.builder) {
            throw new IOAdapterException("Two builders are registered for the object type " +
                    info.sourceType.getName() + ": " +
                    info.builder.getClass().getName() + " and " + current.getClass().getName() + ".");
        }
    }

    private void registerSerializer(SerializerInfo info, String namespace, String name) throws IOAdapterException {
        SerializerInfo current = serializers.computeIfAbsent(namespace, v -> new HashMap<>()).put(name, info);
        if (current != null && current.serializer != info.serializer) {
            throw new IOAdapterException("Two serializers are registered for the " +
                    "database type {" + namespace + "}" + name + ": " +
                    info.serializer.getClass().getName() + " and " + current.serializer.getClass().getName() + ".");
        }
    }

    private BuilderInfo getBuilderInfo(ModelBuilder<?, ?> builder) throws IOAdapterException {
        Class<?> type = builder.getClass();
        Class<?> sourceType = null;
        Class<? extends Child> targetType = null;

        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && Modifier.isPublic(method.getModifiers())) {
                Class<?>[] parameters;
                switch (method.getName()) {
                    case "createModel":
                        parameters = method.getParameterTypes();
                        if (parameters.length == 1
                                && Child.class.isAssignableFrom(method.getReturnType())) {
                            sourceType = parameters[0];
                            targetType = method.getReturnType().asSubclass(Child.class);
                        }
                        break;
                    case "build":
                        parameters = method.getParameterTypes();
                        if (parameters.length == 3
                                && Child.class.isAssignableFrom(parameters[1])
                                && parameters[2] == ModelBuilderHelper.class) {
                            sourceType = parameters[0];
                            targetType = parameters[1].asSubclass(Child.class);
                        }
                        break;
                }
            }
        }

        if (sourceType != null) {
            return new BuilderInfo(builder, sourceType, targetType);
        } else {
            throw new IOAdapterException("The builder " + type.getName() + " lacks the build method.");
        }
    }

    private SerializerInfo getSerializerInfo(ModelSerializer<?, ?> serializer) throws IOAdapterException {
        Class<?> type = serializer.getClass();
        Class<? extends Child> sourceType = null;
        Class<?> targetType = null;

        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && Modifier.isPublic(method.getModifiers())) {
                Class<?>[] parameters;
                switch (method.getName()) {
                    case "createObject":
                        parameters = method.getParameterTypes();
                        if (parameters.length == 1
                                && Child.class.isAssignableFrom(parameters[0])) {
                            sourceType = parameters[0].asSubclass(Child.class);
                            targetType = method.getReturnType();
                        }
                        break;
                    case "serialize":
                        parameters = method.getParameterTypes();
                        if (parameters.length == 3
                                && Child.class.isAssignableFrom(parameters[0])
                                && parameters[2] == ModelSerializerHelper.class) {
                            sourceType = parameters[0].asSubclass(Child.class);
                            targetType = parameters[1];
                        }
                        break;
                }
            }
        }

        if (targetType != null) {
            return new SerializerInfo(serializer, sourceType, targetType);
        } else {
            throw new IOAdapterException("The serializer " + type.getName() + " lacks the createObject " +
                    "or serialize method.");
        }
    }

    private static class BuilderInfo {
        final ModelBuilder<?, ?> builder;
        final Class<?> sourceType;
        final Class<? extends Child> targetType;

        BuilderInfo(ModelBuilder<?, ?> builder, Class<?> sourceType, Class<? extends Child> targetType) {
            this.builder = builder;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }
    }

    private static class SerializerInfo {
        final ModelSerializer<?, ?> serializer;
        final Class<? extends Child> sourceType;
        final Class<?> targetType;

        SerializerInfo(ModelSerializer<?, ?> serializer, Class<? extends Child> sourceType, Class<?> targetType) {
            this.serializer = serializer;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }
    }
}

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

package org.citydb.io.citygml.writer;

import org.apache.logging.log4j.Logger;
import org.citydb.config.ConfigException;
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.file.OutputFile;
import org.citydb.io.citygml.CityGMLAdapterContext;
import org.citydb.io.citygml.writer.util.GlobalFeatureWriter;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class CityJSONWriter implements FeatureWriter, GlobalFeatureWriter {
    private final Logger logger = LoggerManager.getInstance().getLogger(CityJSONWriter.class);
    private final AbstractCityJSONWriter<?> writer;
    private final PersistentMapStore store;
    private final ExecutorService service;
    private final ThreadLocal<ModelSerializerHelper> helpers;
    private final CountLatch countLatch;

    private volatile boolean shouldRun = true;
    private Throwable exception;

    public CityJSONWriter(OutputFile file, WriteOptions options, CityGMLAdapterContext adapterContext, CityJSONContext cityJSONContext) throws WriteException {
        Objects.requireNonNull(file, "The output file must not be null.");
        Objects.requireNonNull(options, "The write options must not be null.");
        Objects.requireNonNull(adapterContext, "CityGML adapter context must not be null.");
        Objects.requireNonNull(cityJSONContext, "CityJSON context must not be null.");

        CityJSONFormatOptions formatOptions;
        try {
            formatOptions = options.getFormatOptions()
                    .getOrElse(CityJSONFormatOptions.class, CityJSONFormatOptions::new);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get CityJSON format options from config.", e);
        }

        writer = CityJSONWriterFactory.newInstance(cityJSONContext, options, formatOptions)
                .createWriter(file);

        try {
            store = PersistentMapStore.builder()
                    .tempDirectory(options.getTempDirectory())
                    .build();
            logger.debug("Initialized CityJSON writer cache at {}.", store.getBackingFile());
        } catch (IOException e) {
            throw new WriteException("Failed to initialize local cache.", e);
        }

        service = ExecutorHelper.newFixedAndBlockingThreadPool(1, 100);
        helpers = ThreadLocal.withInitial(() -> new ModelSerializerHelper(this, store, adapterContext)
                .initialize(options, formatOptions));
        countLatch = new CountLatch();

        processGlobalTemplates(formatOptions.consumeGlobalTemplates());
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        if (shouldRun) {
            try {
                AbstractFeature converted = helpers.get().getTopLevelFeature(feature);
                return writeCityObject(converted);
            } catch (Throwable e) {
                shouldRun = false;
                throw new WriteException("Failed to write feature.", e);
            }
        } else {
            return new CompletableFuture<>();
        }
    }

    @Override
    public void write(AbstractFeature feature) throws WriteException {
        writeCityObject(feature);
    }

    private CompletableFuture<Boolean> writeCityObject(AbstractFeature feature) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (shouldRun) {
            countLatch.increment();
            service.execute(() -> {
                try {
                    if (feature != null) {
                        writer.writeCityObject(feature);
                        result.complete(true);
                    } else {
                        result.complete(false);
                    }
                } catch (Throwable e) {
                    shouldRun = false;
                    result.completeExceptionally(new WriteException("Failed to write feature.", e));
                } finally {
                    countLatch.decrement();
                }
            });
        } else {
            result.cancel(true);
        }

        return result;
    }

    private void processGlobalTemplates(List<ImplicitGeometry> globalTemplates) throws WriteException {
        if (!globalTemplates.isEmpty()) {
            Map<AbstractGeometry, Number> geometries = Collections.synchronizedMap(new IdentityHashMap<>());
            List<Appearance> appearances = Collections.synchronizedList(new ArrayList<>());

            for (Iterator<ImplicitGeometry> iterator = globalTemplates.iterator(); shouldRun && iterator.hasNext(); ) {
                ImplicitGeometry template = iterator.next();
                iterator.remove();

                countLatch.increment();
                service.execute(() -> {
                    try {
                        org.citygml4j.core.model.core.ImplicitGeometry implicitGeometry = helpers.get()
                                .getImplicitGeometry(template);
                        if (implicitGeometry != null) {
                            if (implicitGeometry.getRelativeGeometry() != null
                                    && implicitGeometry.getRelativeGeometry().isSetInlineObject()) {
                                geometries.put(implicitGeometry.getRelativeGeometry().getObject(),
                                        template.getUserProperties().getOrDefault(
                                                CityJSONFormatOptions.TEMPLATE_LOD_PROPERTY, Number.class, () -> 0));
                            }

                            if (implicitGeometry.isSetAppearances()) {
                                implicitGeometry.getAppearances().stream()
                                        .map(AbstractAppearanceProperty::getObject)
                                        .filter(Appearance.class::isInstance)
                                        .map(Appearance.class::cast)
                                        .forEach(appearances::add);
                            }
                        }
                    } catch (Throwable e) {
                        shouldRun = false;
                        exception = e;
                    } finally {
                        countLatch.decrement();
                    }
                });
            }

            countLatch.await();
            if (exception == null) {
                geometries.forEach(writer::withGlobalTemplateGeometry);
                appearances.forEach(writer::withGlobalAppearance);
            } else {
                throw new WriteException("Failed to preprocess global template geometries.", exception);
            }
        }
    }

    @Override
    public void cancel() {
        shouldRun = false;
    }

    @Override
    public void close() throws WriteException {
        try {
            countLatch.await();
            store.close();
            writer.close();
        } catch (Exception e) {
            throw new WriteException("Failed to close CityJSON writer.", e);
        } finally {
            service.shutdown();
        }
    }
}

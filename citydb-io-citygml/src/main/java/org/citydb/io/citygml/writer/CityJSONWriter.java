/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.file.OutputFile;
import org.citydb.io.citygml.CityGMLAdapterContext;
import org.citydb.io.citygml.writer.util.GlobalFeatureWriter;
import org.citydb.io.util.FormatOptions;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class CityJSONWriter implements FeatureWriter, GlobalFeatureWriter {
    private final Logger logger = LoggerManager.getInstance().getLogger();
    private final CityGMLAdapterContext adapterContext;
    private final CityJSONContext cityJSONContext;

    private AbstractCityJSONWriter<?> writer;
    private PersistentMapStore store;
    private ExecutorService service;
    private ThreadLocal<ModelSerializerHelper> helpers;
    private CountLatch countLatch;
    private Throwable exception;

    private volatile boolean isInitialized;
    private volatile boolean shouldRun;

    public CityJSONWriter(CityGMLAdapterContext adapterContext, CityJSONContext cityJSONContext) {
        this.adapterContext = Objects.requireNonNull(adapterContext, "CityGML adapter context must not be null.");
        this.cityJSONContext = Objects.requireNonNull(cityJSONContext, "CityJSON context must not be null.");
    }

    @Override
    public void initialize(OutputFile file, WriteOptions options) throws WriteException {
        CityJSONFormatOptions formatOptions = FormatOptions.parseElseGet(options.getFormatOptions(),
                CityJSONFormatOptions.class, CityJSONFormatOptions::new);

        writer = CityJSONWriterFactory.newInstance(cityJSONContext, options, formatOptions)
                .createWriter(file);

        try {
            store = PersistentMapStore.newInstance();
            logger.debug("Initialized local cache at " + store.getBackingFile() + ".");
        } catch (IOException e) {
            throw new WriteException("Failed to initialize local cache.", e);
        }

        service = ExecutorHelper.newFixedAndBlockingThreadPool(1, options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
        helpers = ThreadLocal.withInitial(() -> new ModelSerializerHelper(this, store, adapterContext)
                .initialize(options, formatOptions));
        countLatch = new CountLatch();

        shouldRun = true;
        processGlobalTemplates(formatOptions.consumeGlobalTemplates());
        isInitialized = true;
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        if (!isInitialized) {
            throw new WriteException("Illegal to write data when writer has not been initialized.");
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (shouldRun) {
            countLatch.increment();
            service.execute(() -> {
                try {
                    writer.writeCityObject(helpers.get().getTopLevelFeature(feature));
                    result.complete(true);
                } catch (Throwable e) {
                    shouldRun = false;
                    result.completeExceptionally(new WriteException("Failed to write feature.", e));
                } finally {
                    countLatch.decrement();
                }
            });
        }

        return result;
    }

    @Override
    public void write(AbstractFeature feature) throws WriteException {
        try {
            writer.writeCityObject(feature);
        } catch (CityJSONWriteException e) {
            throw new WriteException("Failed to write feature.", e);
        }
    }

    private void processGlobalTemplates(List<ImplicitGeometry> globalTemplates) throws WriteException {
        if (!globalTemplates.isEmpty()) {
            ReferenceResolver resolver = DefaultReferenceResolver.newInstance().storeRefereesWithReferencedObject(true);
            Map<AbstractGeometry, Number> geometries = Collections.synchronizedMap(new IdentityHashMap<>());

            for (Iterator<ImplicitGeometry> iterator = globalTemplates.iterator(); shouldRun && iterator.hasNext(); ) {
                ImplicitGeometry template = iterator.next();
                iterator.remove();

                countLatch.increment();
                service.execute(() -> {
                    try {
                        org.citygml4j.core.model.core.ImplicitGeometry implicitGeometry = helpers.get()
                                .getImplicitGeometry(template);
                        if (implicitGeometry != null
                                && implicitGeometry.getRelativeGeometry() != null
                                && implicitGeometry.getRelativeGeometry().isSetInlineObject()) {
                            resolver.resolveReferences(implicitGeometry);
                            geometries.put(implicitGeometry.getRelativeGeometry().getObject(),
                                    template.getUserProperties().getOrDefault(
                                            CityJSONFormatOptions.TEMPLATE_LOD_PROPERTY, Number.class, () -> 0));
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
            } else {
                throw new WriteException("Failed to process global template geometries.", exception);
            }
        }
    }

    @Override
    public void cancel() {
        shouldRun = false;
    }

    @Override
    public void close() throws WriteException {
        if (isInitialized) {
            try {
                countLatch.await();
                store.close();
                writer.close();
            } catch (Exception e) {
                throw new WriteException("Failed to close CityJSON writer.", e);
            } finally {
                service.shutdown();
                isInitialized = false;
            }
        }
    }
}

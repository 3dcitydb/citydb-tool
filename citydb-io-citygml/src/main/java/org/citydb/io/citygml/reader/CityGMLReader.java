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

package org.citydb.io.citygml.reader;

import org.apache.logging.log4j.Logger;
import org.citydb.core.cache.PersistentMapStore;
import org.citydb.core.concurrent.CountLatch;
import org.citydb.core.concurrent.ExecutorHelper;
import org.citydb.core.file.InputFile;
import org.citydb.io.citygml.CityGMLAdapterContext;
import org.citydb.io.citygml.reader.preprocess.Preprocessor;
import org.citydb.io.citygml.reader.util.FileMetadata;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.xml.reader.CityGMLChunk;
import org.citygml4j.xml.reader.CityGMLInputFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class CityGMLReader implements FeatureReader {
    private final Logger logger = LoggerManager.getInstance().getLogger();
    private final CityGMLAdapterContext context;
    private final CityGMLReaderFactory factory;

    private InputFile file;
    private ReadOptions options;
    private CityGMLFormatOptions formatOptions;
    private Preprocessor preprocessor;
    private PersistentMapStore store;
    private Throwable exception;

    private volatile boolean isInitialized;
    private volatile boolean shouldRun;

    public CityGMLReader(CityGMLAdapterContext context) {
        this.context = Objects.requireNonNull(context, "CityGML adapter context must not be null.");
        factory = CityGMLReaderFactory.newInstance(context.getCityGMLContext());
    }

    @Override
    public void initialize(InputFile file, ReadOptions options) throws ReadException {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.options = Objects.requireNonNull(options, "The reader options must not be null.");
        factory.setReadOptions(options);

        formatOptions = options.getFormatOptions().getOrElse(CityGMLFormatOptions.class, CityGMLFormatOptions::new);

        try {
            store = PersistentMapStore.newInstance();
            logger.debug("Initialized local cache at " + store.getBackingFile() + ".");
        } catch (IOException e) {
            throw new ReadException("Failed to initialize local cache.", e);
        }

        // set preprocessing options
        preprocessor = new Preprocessor()
                .resolveGeometryReferences(formatOptions.isResolveGeometryReferences())
                .resolveCrossLodReferences(formatOptions.isResolveCrossLodReferences())
                .createCityObjectRelations(formatOptions.isCreateCityObjectRelations())
                .useLod4AsLod3(formatOptions.isUseLod4AsLod3())
                .mapLod0RoofEdge(formatOptions.isMapLod0RoofEdge())
                .mapLod1MultiSurfaces(formatOptions.isMapLod1MultiSurfaces())
                .setNumberOfThreads(options.getNumberOfThreads());

        logger.debug("Reading global objects and resolving global references.");
        preprocessor.processGlobalObjects(file, factory);

        isInitialized = true;
        shouldRun = true;
    }

    @Override
    public void read(Consumer<Feature> consumer) throws ReadException {
        if (!isInitialized) {
            throw new ReadException("Illegal to read data when reader has not been initialized.");
        }

        CityGMLInputFactory inputFactory = factory.createInputFactory();
        ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        CountLatch countLatch = new CountLatch();

        try (org.citygml4j.xml.reader.CityGMLReader reader = factory.createReader(file, inputFactory)) {
            int featureId = 0;
            FileMetadata metadata = FileMetadata.of(reader);
            ThreadLocal<ModelBuilderHelper> helpers = ThreadLocal.withInitial(() ->
                    new ModelBuilderHelper(file, store, context).initialize(metadata, options, formatOptions));

            while (shouldRun && reader.hasNext()) {
                CityGMLChunk chunk = reader.nextChunk();
                chunk.getLocalProperties().set("featureId", featureId++);

                countLatch.increment();
                service.execute(() -> {
                    try {
                        AbstractFeature feature = chunk.build();
                        if (preprocessor.process(feature, (int) chunk.getLocalProperties().get("featureId"))) {
                            process(feature, consumer, helpers.get());
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
            preprocessor.postprocess();

            Iterator<CityObjectGroup> iterator = preprocessor.getCityObjectGroups().iterator();
            while (shouldRun && iterator.hasNext()) {
                CityObjectGroup group = iterator.next();
                countLatch.increment();
                service.execute(() -> {
                    try {
                        process(group, consumer, helpers.get());
                    } catch (Throwable e) {
                        shouldRun = false;
                        exception = e;
                    } finally {
                        countLatch.decrement();
                    }
                });
            }

            countLatch.await();
            if (exception != null) {
                throw exception;
            }
        } catch (Throwable e) {
            throw new ReadException("Failed to read input file.", e);
        } finally {
            service.shutdown();
        }
    }

    private void process(AbstractFeature feature, Consumer<Feature> consumer, ModelBuilderHelper helper) throws Exception {
        Feature object = helper.getTopLevelFeature(feature);
        if (object != null) {
            consumer.accept(object);
        }
    }

    @Override
    public void cancel() {
        shouldRun = false;
        if (isInitialized) {
            preprocessor.cancel();
        }
    }

    @Override
    public void close() {
        if (isInitialized) {
            store.close();
            preprocessor = null;
            exception = null;
            isInitialized = false;
        }
    }
}

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

package org.citydb.io.citygml.reader;

import org.apache.logging.log4j.Logger;
import org.citydb.config.ConfigException;
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
import org.citydb.io.reader.filter.Filter;
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
    private final Logger logger = LoggerManager.getInstance().getLogger(CityGMLReader.class);
    private final InputFile file;
    private final ReadOptions options;
    private final CityGMLAdapterContext context;
    private final CityGMLReaderFactory factory;
    private final CityGMLFormatOptions formatOptions;
    private final PersistentMapStore store;
    private final Filter filter;

    private volatile boolean isPreprocessed;
    private volatile boolean shouldRun = true;
    private Preprocessor preprocessor;
    private Throwable exception;

    public CityGMLReader(InputFile file, ReadOptions options, CityGMLAdapterContext context) throws ReadException {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.options = Objects.requireNonNull(options, "The read options must not be null.");
        this.context = Objects.requireNonNull(context, "CityGML adapter context must not be null.");

        try {
            formatOptions = options.getFormatOptions()
                    .getOrElse(CityGMLFormatOptions.class, CityGMLFormatOptions::new);
        } catch (ConfigException e) {
            throw new ReadException("Failed to get CityGML format options from config.", e);
        }

        try {
            store = PersistentMapStore.builder()
                    .tempDirectory(options.getTempDirectory())
                    .build();
            logger.debug("Initialized CityGML reader cache at {}.", store.getBackingFile());
        } catch (IOException e) {
            throw new ReadException("Failed to initialize local cache.", e);
        }

        factory = CityGMLReaderFactory.newInstance(context.getCityGMLContext(), options);
        filter = options.getFilter().orElseGet(Filter::acceptAll);
        preprocessor = new Preprocessor()
                .resolveGeometryReferences(formatOptions.isResolveGeometryReferences())
                .resolveCrossLodReferences(formatOptions.isResolveCrossLodReferences())
                .createCityObjectRelations(formatOptions.isCreateCityObjectRelations())
                .useLod4AsLod3(formatOptions.isUseLod4AsLod3())
                .mapLod0RoofEdge(formatOptions.isMapLod0RoofEdge())
                .mapLod1MultiSurfaces(formatOptions.isMapLod1MultiSurfaces())
                .setNumberOfThreads(options.getNumberOfThreads());
    }

    @Override
    public void read(Consumer<Feature> consumer) throws ReadException {
        if (!isPreprocessed) {
            preprocess();
        }

        CityGMLInputFactory inputFactory = factory.createInputFactory();
        int threads = filter.needsSequentialProcessing() ? 1 :
                options.getNumberOfThreads() > 0 ?
                        options.getNumberOfThreads() :
                        Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(threads);
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
            Filter.Result result = filter.test(object);
            if (result == Filter.Result.ACCEPT) {
                consumer.accept(object);
            } else if (result == Filter.Result.STOP) {
                shouldRun = false;
            }
        }
    }

    private void preprocess() throws ReadException {
        logger.debug("Reading global objects and resolving global references...");
        preprocessor.processGlobalObjects(file, factory);
        isPreprocessed = true;
    }

    @Override
    public void cancel() {
        shouldRun = false;
        preprocessor.cancel();
    }

    @Override
    public void close() {
        store.close();
        preprocessor = null;
        exception = null;
    }
}

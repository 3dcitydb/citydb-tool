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
import org.citydb.io.citygml.reader.util.FileMetadata;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citydb.logging.LoggerManager;
import org.citydb.model.feature.Feature;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class CityJSONReader implements FeatureReader {
    private final Logger logger = LoggerManager.getInstance().getLogger(CityJSONReader.class);
    private final CityGMLAdapterContext adapterContext;
    private final CityJSONContext cityJSONContext;

    private InputFile file;
    private ReadOptions options;
    private CityJSONReaderFactory factory;
    private PersistentMapStore store;
    private Throwable exception;

    private volatile boolean isInitialized;
    private volatile boolean shouldRun;

    public CityJSONReader(CityGMLAdapterContext adapterContext, CityJSONContext cityJSONContext) {
        this.adapterContext = Objects.requireNonNull(adapterContext, "CityGML adapter context must not be null.");
        this.cityJSONContext = Objects.requireNonNull(cityJSONContext, "CityJSON context must not be null.");
    }

    @Override
    public void initialize(InputFile file, ReadOptions options) throws ReadException {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.options = Objects.requireNonNull(options, "The reader options must not be null.");

        try {
            store = PersistentMapStore.builder()
                    .tempDirectory(options.getTempDirectory())
                    .build();
            logger.debug("Initialized local cache at {}.", store.getBackingFile());
        } catch (IOException e) {
            throw new ReadException("Failed to initialize local cache.", e);
        }

        CityJSONFormatOptions formatOptions;
        try {
            formatOptions = options.getFormatOptions().get(CityJSONFormatOptions.class);
        } catch (ConfigException e) {
            throw new ReadException("Failed to get CityJSON format options from config.", e);
        }

        factory = CityJSONReaderFactory.newInstance(cityJSONContext)
                .setReadOptions(options)
                .setFormatOptions(formatOptions);

        isInitialized = true;
        shouldRun = true;
    }

    @Override
    public void read(Consumer<Feature> consumer) throws ReadException {
        if (!isInitialized) {
            throw new ReadException("Illegal to read data when reader has not been initialized.");
        }

        ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        CountLatch countLatch = new CountLatch();

        try (org.citygml4j.cityjson.reader.CityJSONReader reader = factory.createReader(file)) {
            FileMetadata metadata = FileMetadata.of(reader);
            ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();
            ThreadLocal<ModelBuilderHelper> helpers = ThreadLocal.withInitial(() ->
                    new ModelBuilderHelper(file, store, adapterContext).initialize(metadata, options));

            while (shouldRun && reader.hasNext()) {
                AbstractFeature feature = reader.next();

                countLatch.increment();
                service.execute(() -> {
                    try {
                        referenceResolver.resolveReferences(feature);
                        Feature object = helpers.get().getTopLevelFeature(feature);
                        if (object != null) {
                            consumer.accept(object);
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
            if (exception != null) {
                throw exception;
            }
        } catch (Throwable e) {
            throw new ReadException("Failed to read input file.", e);
        } finally {
            service.shutdown();
        }
    }

    @Override
    public void cancel() {
        shouldRun = false;
    }

    @Override
    public void close() {
        if (isInitialized) {
            store.close();
            exception = null;
            isInitialized = false;
        }
    }
}

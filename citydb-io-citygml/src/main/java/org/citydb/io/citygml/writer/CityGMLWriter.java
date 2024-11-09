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
import org.citygml4j.core.model.core.AbstractFeature;
import org.xmlobjects.util.xml.SAXBuffer;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class CityGMLWriter implements FeatureWriter, GlobalFeatureWriter {
    private final Logger logger = LoggerManager.getInstance().getLogger(CityGMLWriter.class);
    private final CityGMLAdapterContext context;

    private CityGMLChunkWriter writer;
    private PersistentMapStore store;
    private ExecutorService service;
    private ThreadLocal<ModelSerializerHelper> helpers;
    private CountLatch countLatch;

    private volatile boolean isInitialized;
    private volatile boolean shouldRun;

    public CityGMLWriter(CityGMLAdapterContext context) {
        this.context = Objects.requireNonNull(context, "CityGML adapter context must not be null.");
    }

    @Override
    public void initialize(OutputFile file, WriteOptions options) throws WriteException {
        Objects.requireNonNull(file, "The output file must not be null.");
        Objects.requireNonNull(options, "The write options must not be null.");

        CityGMLFormatOptions formatOptions;
        try {
            formatOptions = options.getFormatOptions()
                    .getOrElse(CityGMLFormatOptions.class, CityGMLFormatOptions::new);
        } catch (ConfigException e) {
            throw new WriteException("Failed to get CityGML format options from config.", e);
        }

        writer = CityGMLWriterFactory.newInstance(context.getCityGMLContext(), options, formatOptions)
                .createWriter(file);

        try {
            store = PersistentMapStore.builder()
                    .tempDirectory(options.getTempDirectory())
                    .build();
            logger.debug("Initialized CityGML writer cache at {}.", store.getBackingFile());
        } catch (IOException e) {
            throw new WriteException("Failed to initialize local cache.", e);
        }

        service = ExecutorHelper.newFixedAndBlockingThreadPool(1, (options.getNumberOfThreads() > 0 ?
                options.getNumberOfThreads() :
                Math.max(2, Runtime.getRuntime().availableProcessors())) * 2);
        helpers = ThreadLocal.withInitial(() -> new ModelSerializerHelper(this, store, context)
                .initialize(options, formatOptions));
        countLatch = new CountLatch();

        isInitialized = true;
        shouldRun = true;
    }

    @Override
    public CompletableFuture<Boolean> write(Feature feature) throws WriteException {
        if (shouldRun) {
            try {
                AbstractFeature converted = helpers.get().getTopLevelFeature(feature);
                return write(writer.bufferMember(converted));
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
        write(writer.bufferMember(feature));
    }

    private CompletableFuture<Boolean> write(SAXBuffer buffer) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (shouldRun) {
            if (isInitialized) {
                countLatch.increment();
                service.execute(() -> {
                    try {
                        if (buffer != null && !buffer.isEmpty()) {
                            buffer.send(writer.getContentHandler(), false);
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
                result.completeExceptionally(
                        new WriteException("Illegal to write data when writer has not been initialized."));
            }
        }

        return result;
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
                throw new WriteException("Failed to close CityGML writer.", e);
            } finally {
                service.shutdown();
                isInitialized = false;
            }
        }
    }
}

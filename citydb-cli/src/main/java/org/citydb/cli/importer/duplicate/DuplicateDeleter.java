/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.duplicate;

import org.apache.logging.log4j.Logger;
import org.citydb.cli.ExecutionException;
import org.citydb.cli.importer.ImportOptions;
import org.citydb.cli.importer.options.ImportMode;
import org.citydb.cli.logging.LoggerManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.operation.deleter.DeleteOptions;
import org.citydb.operation.deleter.Deleter;
import org.citydb.operation.deleter.options.DeleteMode;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class DuplicateDeleter {
    private final Logger logger = LoggerManager.getInstance().getLogger(DuplicateDeleter.class);
    private final ImportOptions options;
    private final DatabaseAdapter adapter;
    private final boolean preview;
    private volatile boolean shouldRun = true;
    private Throwable exception;

    DuplicateDeleter(ImportOptions options, DatabaseAdapter adapter, boolean preview) {
        this.options = options;
        this.adapter = adapter;
        this.preview = preview;
    }

    void deleteDuplicates(Map<Long, Boolean> databaseIds) throws ExecutionException {
        AtomicLong counter = new AtomicLong();
        Deleter deleter = Deleter.newInstance()
                .setTransactionMode(preview ?
                        Deleter.TransactionMode.AUTO_ROLLBACK :
                        Deleter.TransactionMode.AUTO_COMMIT);

        try {
            try {
                deleter.startSession(adapter, new DeleteOptions()
                        .setReasonForUpdate("Duplicate deletion")
                        .setMode(options.getMode() == ImportMode.TERMINATE_EXISTING ?
                                DeleteMode.TERMINATE :
                                DeleteMode.DELETE));

                Iterator<Long> iterator = databaseIds.keySet().iterator();
                while (shouldRun && iterator.hasNext()) {
                    long id = iterator.next();
                    deleter.deleteFeature(id).whenComplete((success, t) -> {
                        if (success == Boolean.TRUE) {
                            long count = counter.incrementAndGet();
                            if (count % 1000 == 0) {
                                logger.info("{} duplicates processed.", count);
                            }
                        } else {
                            shouldRun = false;
                            exception = t;
                            logger.error("Failed to delete duplicate feature (ID: {}).", id);
                        }
                    });
                }

                if (exception != null) {
                    throw exception;
                }
            } finally {
                if (shouldRun && !preview && deleter.wasSuccessful()) {
                    deleter.commitSession();
                } else {
                    deleter.abortSession();
                }
            }
        } catch (Throwable e) {
            throw new ExecutionException("Failed to delete duplicate features.", e);
        }

        if (!shouldRun || !deleter.wasSuccessful()) {
            throw new ExecutionException("Failed to delete duplicate features.");
        }
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.pipeline;

import org.citydb.vis.writer.VisExportException;

import java.util.List;

/**
 * Drives a sequence of {@link Stage stages} against a shared
 * {@link PipelineContext}. Stages run in declared order; the first one to
 * throw aborts the run.
 */
public final class ExportPipeline {
    private final List<Stage> stages;

    public ExportPipeline(Stage... stages) {
        this.stages = List.of(stages);
    }

    public void run(PipelineContext ctx) throws VisExportException {
        for (Stage stage : stages) {
            stage.execute(ctx);
        }
    }
}

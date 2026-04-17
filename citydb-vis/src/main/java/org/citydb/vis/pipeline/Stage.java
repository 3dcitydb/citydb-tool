/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.pipeline;

import org.citydb.vis.writer.VisExportException;

/**
 * A single step in the visualization export pipeline. Stages read and mutate
 * the shared {@link PipelineContext}; they are expected to be executed in the
 * order defined by {@link ExportPipeline}.
 */
@FunctionalInterface
public interface Stage {
    void execute(PipelineContext ctx) throws VisExportException;
}

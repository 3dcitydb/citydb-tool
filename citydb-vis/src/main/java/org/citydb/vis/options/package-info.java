/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

/**
 * User-facing export options: {@link org.citydb.vis.options.VisFormatOptions}
 * and its per-format subclasses, plus the option enums they carry.
 * <p>
 * <strong>This is not a low-level configuration layer.</strong> It is the
 * project-wide {@code *Options} pattern — the same role
 * {@code org.citydb.operation.exporter.ExportOptions} and
 * {@code org.citydb.io.writer.WriteOptions} play in their modules: a top-layer
 * aggregate, consumed only by {@code pipeline} and {@code writer}, that is free
 * to name the domain types it aggregates ({@code appearance} atlas enums,
 * {@code attribute.AttributeProjection}, {@code styling.ObjectStyleRegistry}).
 * The genuinely low-level, dependency-free configuration layer is the separate
 * {@code citydb-config} module ({@code SerializableConfig}, {@code ConfigObject},
 * {@code SrsReference}) — domain options never belong there. Do not "fix" this
 * package's outgoing edges by pushing it down the layer stack; the algorithm
 * packages ({@code appearance}, {@code geometry}, {@code store}, {@code scene})
 * deliberately import nothing from here and take plain values as parameters
 * instead.
 * <p>
 * Elsewhere in the project these classes would live beside their consumer
 * (as {@code operation.exporter.ExportOptions} sits beside {@code Exporter}).
 * Here they are a sibling package instead, because {@code pipeline} also reads
 * them: while they lived in {@code writer}, {@code pipeline → writer} met
 * {@code writer → pipeline.PipelineContext} and formed a real cycle. That is
 * the only reason this package is standalone.
 */
package org.citydb.vis.options;

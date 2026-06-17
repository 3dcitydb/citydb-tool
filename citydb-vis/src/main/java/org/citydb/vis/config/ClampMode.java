/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReader;
import com.alibaba.fastjson2.writer.ObjectWriter;

import java.lang.reflect.Type;

/**
 * How the exporter places each feature vertically before tiling.
 * <p>
 * The default (no {@code --clamp-to-ground}) is <em>no clamping</em>: features
 * keep the absolute height that comes out of the database. This enum only
 * describes the two <em>active</em> clamp targets; the "do nothing" state is
 * modelled by a {@code null} {@code ClampMode} on the format options, not by a
 * constant here.
 * <ul>
 *   <li>{@link #ELLIPSOID} — shift each mesh so its lowest vertex sits on the
 *       WGS84 ellipsoid (height 0). Useful when the viewer has no terrain
 *       loaded. Purely local, no network access.</li>
 *   <li>{@link #CESIUM_WORLD_TERRAIN} — sample the Cesium World Terrain height
 *       at the feature's centroid (via a Cesium ion token) at export time and
 *       shift each mesh so its lowest vertex sits on that terrain height. The
 *       baked offsets make the export line up with Cesium World Terrain in the
 *       viewer even when the source heights are unreliable or relative.</li>
 * </ul>
 */
public enum ClampMode {
    ELLIPSOID("ellipsoid"),
    CESIUM_WORLD_TERRAIN("cesium-world-terrain");

    private final String value;

    ClampMode(String value) {
        this.value = value;
    }

    /**
     * The CLI / config spelling of this mode (kebab-case), e.g.
     * {@code cesium-world-terrain}.
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolve a CLI / config token (case-insensitive, kebab-case) to a mode.
     *
     * @throws IllegalArgumentException if the token matches no mode
     */
    public static ClampMode fromValue(String value) {
        if (value != null) {
            for (ClampMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("unknown clamp mode '" + value +
                "' (expected one of: ellipsoid, cesium-world-terrain)");
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * fastjson2 serializer that writes a {@code ClampMode} as its kebab-case
     * {@link #getValue() value} (e.g. {@code "cesium-world-terrain"}) instead of
     * the enum constant name, so the JSON config spelling matches the CLI
     * {@code --clamp-to-ground} spelling exactly. Wired up via
     * {@code @JSONField(serializeUsing = ...)} on the format-options field.
     */
    public static class JsonWriter implements ObjectWriter<ClampMode> {
        @Override
        public void write(JSONWriter jsonWriter, Object object, Object fieldName,
                          Type fieldType, long features) {
            if (object == null) {
                jsonWriter.writeNull();
            } else {
                jsonWriter.writeString(((ClampMode) object).value);
            }
        }
    }

    /**
     * fastjson2 deserializer that reads a {@code ClampMode} from its kebab-case
     * {@link #getValue() value}, the counterpart of {@link JsonWriter}. Wired up
     * via {@code @JSONField(deserializeUsing = ...)} on the format-options field.
     */
    public static class JsonReader implements ObjectReader<ClampMode> {
        @Override
        public ClampMode readObject(JSONReader jsonReader, Type fieldType,
                                    Object fieldName, long features) {
            String value = jsonReader.readString();
            return value != null ? fromValue(value) : null;
        }
    }
}

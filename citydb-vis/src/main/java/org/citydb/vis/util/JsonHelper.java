/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared JSON serialization utility for visualization format encoders.
 */
public class JsonHelper {
    public static void writePojo(Path file, Object pojo) throws IOException {
        Files.writeString(file, JSON.toJSONString(pojo,
                        JSONWriter.Feature.FieldBased,
                        JSONWriter.Feature.PrettyFormatWith2Space),
                StandardCharsets.UTF_8);
    }
}

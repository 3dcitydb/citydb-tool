/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.cli.common;

import org.citydb.model.common.Matrix3x4;
import picocli.CommandLine;

import java.util.Arrays;

public class TransformOptions implements Option {
    @CommandLine.Option(names = "--transform", paramLabel = "<m0,m1,...,m11|swap-xy>",
            description = "Transform coordinates using a 3x4 matrix in row-major order. Use 'swap-xy' as a shortcut.")
    private String transform;

    private Matrix3x4 transformationMatrix;

    public Matrix3x4 getTransformationMatrix() {
        return transformationMatrix;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (transform != null) {
            if (transform.equalsIgnoreCase("swap-xy")) {
                transformationMatrix = Matrix3x4.ofRowMajor(
                        0, 1, 0, 0,
                        1, 0, 0, 0,
                        0, 0, 1, 0);
            } else {
                String[] values = transform.split(",");
                if (values.length == 12) {
                    try {
                        transformationMatrix = Matrix3x4.ofRowMajor(Arrays.stream(values)
                                .map(Double::parseDouble)
                                .toList());
                    } catch (NumberFormatException e) {
                        throw new CommandLine.ParameterException(commandLine,
                                "Error: The elements of a 3x4 matrix must be floating point numbers but were '" +
                                        String.join(",", values) + "'");
                    }
                } else {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: A 3x4 matrix must be in M0,M1,...,M11 format but was '" + transform + "'");
                }
            }
        }
    }
}

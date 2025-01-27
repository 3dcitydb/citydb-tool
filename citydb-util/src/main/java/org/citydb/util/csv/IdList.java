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

package org.citydb.util.csv;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class IdList implements AutoCloseable {
    private final CSVParser parser;
    private final int columnIndex;
    private final IdListOptions options;
    private final Iterator<CSVRecord> iterator;

    private IdList(CSVParser parser, int columnIndex, IdListOptions options) {
        this.parser = parser;
        this.options = options;
        this.columnIndex = columnIndex;
        iterator = parser.iterator();
    }

    public static IdList of(Path file, IdListOptions options) throws IdListException, IOException {
        Objects.requireNonNull(file, "The input file must not be null.");
        Objects.requireNonNull(options, "The ID list options must not be null.");

        CSVParser parser = options.toCSVFormat()
                .parse(Files.newBufferedReader(file, Charset.forName(options.getEncoding())));

        Integer columnIndex = options.getColumnIndex().orElse(null);
        String columnName = options.getColumnName().orElse(null);
        if (columnIndex == null && columnName == null) {
            throw new IdListException("No column index or name provided.");
        } else if (columnIndex == null) {
            List<String> headerNames = parser.getHeaderNames();
            columnIndex = IntStream.range(0, headerNames.size())
                    .filter(i -> columnName.equalsIgnoreCase(headerNames.get(i)))
                    .findFirst()
                    .orElseThrow(() -> new IdListException("The column name '" + columnName + "' is not contained " +
                            "in header '" + String.join(",", headerNames) + "'."));
        } else if (columnIndex < 0) {
            throw new IdListException("The column index must be a non-negative integer but was " + columnIndex + ".");
        }

        return new IdList(parser, columnIndex, options);
    }

    public IdListOptions getOptions() {
        return options;
    }

    public List<String> getHeader() {
        return parser.getHeaderNames();
    }

    public long getCurrentLineNumber() {
        return parser.getCurrentLineNumber();
    }

    public long getCurrentRecordNumber() {
        return parser.getRecordNumber();
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public String nextId() throws IdListException {
        try {
            return iterator.next().get(columnIndex);
        } catch (Exception e) {
            throw new IdListException("Failed to get next ID value.", e);
        }
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}

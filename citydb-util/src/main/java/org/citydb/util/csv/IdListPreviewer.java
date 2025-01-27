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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IdListPreviewer {

    private IdListPreviewer() {
    }

    public static IdListPreviewer newInstance() {
        return new IdListPreviewer();
    }

    public int preview(Path file, IdListOptions options, Consumer<String> consumer) throws IOException, IdListException {
        return preview(file, options, consumer, 20);
    }

    public int preview(Path file, IdListOptions options, Consumer<String> consumer, int numberOfRecords) throws IOException, IdListException {
        Objects.requireNonNull(file, "The input file must not be null.");
        Objects.requireNonNull(options, "The ID list options must not be null.");
        Objects.requireNonNull(consumer, "The preview consumer must not be null.");
        if (numberOfRecords <= 0) {
            return 0;
        }

        List<String[]> records = new ArrayList<>();
        List<Long> lineNumbers = new ArrayList<>();
        int header = 0;

        try (CSVParser parser = options.toCSVFormat()
                .parse(Files.newBufferedReader(file, Charset.forName(options.getEncoding())))) {
            if (options.requiresHeader()) {
                List<String> headerNames = parser.getHeaderNames();
                if (!headerNames.isEmpty()) {
                    records.add(headerNames.toArray(String[]::new));
                    lineNumbers.add(parser.getCurrentLineNumber());
                    header = 1;
                }
            }

            Iterator<CSVRecord> iterator = parser.iterator();
            while (records.size() < numberOfRecords + header && iterator.hasNext()) {
                try {
                    records.add(iterator.next().values());
                    lineNumbers.add(parser.getCurrentLineNumber());
                } catch (Exception e) {
                    throw new IdListException("Failed to read record from ID list.", e);
                }
            }
        }

        if (records.size() - header > 0) {
            int columns = records.stream().mapToInt(record -> record.length).max().orElse(0);
            int[] widths = computeColumnWidths(columns, records, lineNumbers);
            int selectedIndex = getSelectedIndex(options, header == 1 ? records.get(0) : null);

            consumer.accept(" ".repeat(widths[0]) + IntStream.range(0, columns)
                    .mapToObj(i -> center(getColumnIndex(i, selectedIndex == i), widths[i + 1], "-"))
                    .collect(Collectors.joining("|")));

            if (header > 0) {
                consumer.accept(" ".repeat(widths[0]) + IntStream.range(0, columns)
                        .mapToObj(i -> center(getValue(records.get(0), i), widths[i + 1], " "))
                        .collect(Collectors.joining("|")));
                consumer.accept(" ".repeat(widths[0]) + IntStream.range(0, columns)
                        .mapToObj(i -> "-".repeat(widths[i + 1]))
                        .collect(Collectors.joining("+")));
            }

            for (int i = header; i < records.size(); i++) {
                String[] record = records.get(i);
                consumer.accept(String.format("%-" + widths[0] + "s", lineNumbers.get(i)) +
                        IntStream.range(0, columns)
                                .mapToObj(j -> String.format("%-" + widths[j + 1] + "s", " " + getValue(record, j)))
                                .collect(Collectors.joining("|")));
            }
        }

        return records.size() - header;
    }

    private int[] computeColumnWidths(int columns, List<String[]> records, List<Long> lineNumbers) {
        int[] widths = new int[columns + 1];
        widths[0] = lineNumbers.stream()
                .mapToInt(number -> String.valueOf(number).length() + 1)
                .max().orElse(1);

        IntStream.range(0, columns).forEach(i -> widths[i + 1] = Math.max(5, String.valueOf(i).length() + 2));
        records.forEach(record -> IntStream.range(0, record.length)
                .forEach(i -> widths[i + 1] = Math.max(widths[i + 1], record[i].length() + 2)));

        return widths;
    }

    private int getSelectedIndex(IdListOptions format, String[] header) {
        int selectedIndex = format.getColumnIndex().orElse(-1);
        if (selectedIndex == -1 && header != null && format.getColumnName().isPresent()) {
            String columnName = format.getColumnName().get();
            selectedIndex = IntStream.range(0, header.length)
                    .filter(i -> columnName.equalsIgnoreCase(header[i]))
                    .findFirst().orElse(-1);
        }

        return selectedIndex;
    }

    private String getColumnIndex(int index, boolean isSelected) {
        return isSelected ? ">" + index + "<" : "[" + index + "]";
    }

    private String getValue(String[] record, int index) {
        return index < record.length ? record[index] : "n/a";
    }

    private String center(String value, int width, String padding) {
        int left = Math.max(0, (width - value.length()) / 2);
        int right = Math.max(0, width - value.length() - left);
        return padding.repeat(left) + value + padding.repeat(right);
    }
}

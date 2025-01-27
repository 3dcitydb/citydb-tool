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

import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class IdListOptions {
    public static final String DEFAULT_DELIMITER = ",";
    public static final String DEFAULT_QUOTE_CHARACTER = "\"";
    public static final String DEFAULT_ESCAPE_CHARACTER = "\\";
    public static final String DEFAULT_COMMENT_MARKER = "#";
    public static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private Integer columnIndex;
    private String columnName;
    private String delimiter;
    private String quote;
    private String escape;
    private String commentMarker;
    private Boolean header;
    private String encoding;

    public static IdListOptions newInstance() {
        return new IdListOptions();
    }

    public Optional<Integer> getColumnIndex() {
        return Optional.ofNullable(columnIndex);
    }

    public IdListOptions setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
        columnName = null;
        return this;
    }

    public Optional<String> getColumnName() {
        return Optional.ofNullable(columnName);
    }

    public IdListOptions setColumnName(String columnName) {
        this.columnName = columnName;
        columnIndex = null;
        return this;
    }

    public String getDelimiter() {
        return delimiter != null && !delimiter.isEmpty() ? delimiter : DEFAULT_DELIMITER;
    }

    public IdListOptions setDelimiter(String delimiter) {
        this.delimiter = delimiter;
        return this;
    }

    public String getQuote() {
        return quote != null && !quote.isEmpty() ? quote : DEFAULT_QUOTE_CHARACTER;
    }

    public IdListOptions setQuote(String quote) {
        this.quote = quote;
        return this;
    }

    public String getEscape() {
        return escape != null && !escape.isEmpty() ? escape : DEFAULT_ESCAPE_CHARACTER;
    }

    public IdListOptions setEscape(String escape) {
        this.escape = escape;
        return this;
    }

    public String getCommentMarker() {
        return commentMarker != null && !commentMarker.isEmpty() ? commentMarker : DEFAULT_COMMENT_MARKER;
    }

    public IdListOptions setCommentMarker(String commentMarker) {
        this.commentMarker = commentMarker;
        return this;
    }

    public boolean hasHeader() {
        return header != null ? header : true;
    }

    public IdListOptions setHeader(Boolean header) {
        this.header = header;
        return this;
    }

    boolean requiresHeader() {
        return hasHeader() || columnName != null;
    }

    public String getEncoding() {
        return encoding != null && !encoding.isEmpty() ? encoding : DEFAULT_ENCODING;
    }

    public IdListOptions setEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }

    public IdList parse(Path file) throws IdListException, IOException {
        return IdList.of(file, this);
    }

    public int preview(Path file, Consumer<String> consumer) throws IdListException, IOException {
        return IdListPreviewer.newInstance().preview(file, this, consumer);
    }

    public int preview(Path file, Consumer<String> consumer, int numberOfRecords) throws IdListException, IOException {
        return IdListPreviewer.newInstance().preview(file, this, consumer, numberOfRecords);
    }

    CSVFormat toCSVFormat() {
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder()
                .setDelimiter(getDelimiter())
                .setQuote(getQuote().charAt(0))
                .setEscape(getEscape().charAt(0))
                .setCommentMarker(getCommentMarker().charAt(0))
                .setIgnoreEmptyLines(true);

        if (hasHeader() || columnName != null) {
            builder.setHeader()
                    .setSkipHeaderRecord(true);
        }

        return builder.get();
    }
}

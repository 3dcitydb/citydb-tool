package org.citydb.database.metadata;

import org.citydb.database.schema.Table;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DatabaseSize {
    public static final long UNKNOWN_SIZE = -1;
    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
    private static final int UNIT_STEP = 1024;

    private final long databaseSize;
    private final Map<Table, Long> tableSizes;

    private DatabaseSize(long databaseSize, Map<Table, Long> tableSizes) {
        Objects.requireNonNull(tableSizes, "The table sizes cannot be null.");
        this.databaseSize = Math.max(UNKNOWN_SIZE, databaseSize);
        this.tableSizes = Collections.unmodifiableMap(tableSizes.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (e.getValue() == null || e.getValue() < UNKNOWN_SIZE) ? UNKNOWN_SIZE : e.getValue())));
    }

    public static DatabaseSize of(long databaseSize, Map<Table, Long> tableSizes) {
        return new DatabaseSize(databaseSize, tableSizes);
    }

    public static DatabaseSize of(Map<Table, Long> tableSizes) {
        return new DatabaseSize(UNKNOWN_SIZE, tableSizes);
    }

    public long getDatabaseSize() {
        return databaseSize;
    }

    public String getFormattedDatabaseSize() {
        return formatSize(databaseSize);
    }

    public Map<Table, Long> getTableSizes() {
        return tableSizes;
    }

    public long getTableSize(Table table) {
        return tableSizes.getOrDefault(table, UNKNOWN_SIZE);
    }

    public String getFormattedTableSize(Table table) {
        return formatSize(getTableSize(table));
    }

    public long getSchemaSize() {
        return tableSizes.values().stream()
                .filter(size -> size > UNKNOWN_SIZE)
                .mapToLong(Long::longValue)
                .sum();
    }

    public String getFormattedSchemaSize() {
        return formatSize(getSchemaSize());
    }

    public String formatSize(long bytes) {
        if (bytes < 0) {
            return "unknown";
        } else if (bytes == 0) {
            return "0 " + UNITS[0];
        }

        int unitIndex = 0;
        double size = bytes;

        while (size >= UNIT_STEP && unitIndex < UNITS.length - 1) {
            size /= UNIT_STEP;
            unitIndex++;
        }

        if (size < 10 && unitIndex > 0) {
            size *= UNIT_STEP;
            unitIndex--;
        }

        return String.format(Locale.ENGLISH, "%d %s", Math.round(size), UNITS[unitIndex]);
    }
}

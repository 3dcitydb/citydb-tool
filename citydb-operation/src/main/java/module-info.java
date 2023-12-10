module org.citydb.operation {
    requires transitive org.citydb.database;

    exports org.citydb.operation.deleter;
    exports org.citydb.operation.deleter.common;
    exports org.citydb.operation.deleter.feature;
    exports org.citydb.operation.deleter.options;
    exports org.citydb.operation.deleter.util;
    exports org.citydb.operation.exporter;
    exports org.citydb.operation.exporter.address;
    exports org.citydb.operation.exporter.appearance;
    exports org.citydb.operation.exporter.common;
    exports org.citydb.operation.exporter.feature;
    exports org.citydb.operation.exporter.geometry;
    exports org.citydb.operation.exporter.hierarchy;
    exports org.citydb.operation.exporter.property;
    exports org.citydb.operation.exporter.util;
    exports org.citydb.operation.importer;
    exports org.citydb.operation.importer.address;
    exports org.citydb.operation.importer.appearance;
    exports org.citydb.operation.importer.common;
    exports org.citydb.operation.importer.feature;
    exports org.citydb.operation.importer.geometry;
    exports org.citydb.operation.importer.property;
    exports org.citydb.operation.importer.reference;
    exports org.citydb.operation.importer.util;
    exports org.citydb.operation.util;
}
module org.citydb.cli {
    requires org.citydb.config;
    requires org.citygml4j.core;
    requires org.citydb.io.citygml;
    requires org.citydb.logging;
    requires transitive org.citydb.database;
    requires transitive org.citydb.io;
    requires transitive org.citydb.operation;
    requires transitive org.citydb.plugin;
    requires transitive org.citydb.query;
    requires transitive org.citydb.util;
    requires transitive info.picocli;

    exports org.citydb.cli;
    exports org.citydb.cli.common;
    exports org.citydb.cli.deleter;
    exports org.citydb.cli.deleter.options;
    exports org.citydb.cli.exporter;
    exports org.citydb.cli.exporter.citygml;
    exports org.citydb.cli.exporter.cityjson;
    exports org.citydb.cli.exporter.options;
    exports org.citydb.cli.exporter.util;
    exports org.citydb.cli.extension;
    exports org.citydb.cli.importer;
    exports org.citydb.cli.importer.citygml;
    exports org.citydb.cli.importer.cityjson;
    exports org.citydb.cli.importer.duplicate;
    exports org.citydb.cli.importer.filter;
    exports org.citydb.cli.importer.options;
    exports org.citydb.cli.info;
    exports org.citydb.cli.util;

    opens org.citydb.cli to info.picocli;
    opens org.citydb.cli.common to info.picocli;
    opens org.citydb.cli.deleter to info.picocli;
    opens org.citydb.cli.deleter.options to info.picocli;
    opens org.citydb.cli.exporter to info.picocli;
    opens org.citydb.cli.exporter.citygml to info.picocli;
    opens org.citydb.cli.exporter.cityjson to info.picocli;
    opens org.citydb.cli.exporter.options to info.picocli;
    opens org.citydb.cli.importer to info.picocli;
    opens org.citydb.cli.importer.citygml to info.picocli;
    opens org.citydb.cli.importer.cityjson to info.picocli;
    opens org.citydb.cli.importer.options to info.picocli;
    opens org.citydb.cli.index to info.picocli;
    opens org.citydb.cli.info to info.picocli;
}
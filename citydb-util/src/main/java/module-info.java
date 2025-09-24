module org.citydb.util {
    requires org.citydb.config;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;
    requires transitive org.apache.commons.csv;

    exports org.citydb.util.changelog;
    exports org.citydb.util.changelog.options;
    exports org.citydb.util.changelog.query;
    exports org.citydb.util.csv;
    exports org.citydb.util.process;
    exports org.citydb.util.report;
    exports org.citydb.util.report.options;
    exports org.citydb.util.tiling;
    exports org.citydb.util.tiling.options;
}
module org.citydb.util {
    requires org.citydb.config;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;
    requires transitive org.apache.commons.csv;

    exports org.citydb.util.csv;
    exports org.citydb.util.report;
    exports org.citydb.util.tiling;
    exports org.citydb.util.tiling.options;
}
module org.citydb.tiling {
    requires org.citydb.config;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;

    exports org.citydb.tiling;
    exports org.citydb.tiling.options;
}
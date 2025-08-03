@SuppressWarnings("requires-transitive-automatic")
module org.citydb.database {
    requires org.apache.tomcat.jdbc;
    requires transitive org.citydb.core;
    requires transitive org.citydb.config;
    requires transitive org.citydb.model;
    requires transitive org.citydb.sqlbuilder;
    requires transitive org.geotools.api;
    requires transitive org.geotools.metadata;
    requires transitive org.geotools.referencing;
    requires transitive java.sql;
    requires transitive si.uom.units;
    requires transitive systems.uom.common;

    exports org.citydb.database;
    exports org.citydb.database.adapter;
    exports org.citydb.database.connection;
    exports org.citydb.database.geometry;
    exports org.citydb.database.metadata;
    exports org.citydb.database.schema;
    exports org.citydb.database.srs;
    exports org.citydb.database.util;

    uses org.citydb.database.adapter.DatabaseAdapter;
}
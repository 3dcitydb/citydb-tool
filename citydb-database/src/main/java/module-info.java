import org.citydb.database.adapter.DatabaseAdapter;

module org.citydb.database {
    requires transitive org.citydb.core;
    requires transitive org.citydb.logging;
    requires transitive org.citydb.model;
    requires transitive java.sql;
    requires org.apache.tomcat.jdbc;
    requires java.management;

    uses DatabaseAdapter;

    exports org.citydb.database;
    exports org.citydb.database.adapter;
    exports org.citydb.database.connection;
    exports org.citydb.database.geometry;
    exports org.citydb.database.metadata;
    exports org.citydb.database.schema;
}
module org.citydb.database.oracle {
    requires com.oracle.database.jdbc;
    requires com.oracle.spatial.geometry;
    requires com.oracle.spatial.util;
    requires org.citydb.database;
    requires org.locationtech.jts;

    exports org.citydb.database.oracle;

    provides org.citydb.database.adapter.DatabaseAdapter with org.citydb.database.oracle.OracleAdapter;
}
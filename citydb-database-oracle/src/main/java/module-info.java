module org.citydb.database.oracle {
    requires org.citydb.database;
    requires com.oracle.database.jdbc;
    requires sdoapi;
    requires sdoutl;

    exports org.citydb.database.oracle;

    provides org.citydb.database.adapter.DatabaseAdapter with org.citydb.database.oracle.OracleAdapter;
}
module org.citydb.database.postgres {
    requires org.citydb.database;
    requires org.postgresql.jdbc;

    exports org.citydb.database.postgres;

    provides org.citydb.database.adapter.DatabaseAdapter with org.citydb.database.postgres.PostgresqlAdapter;
}
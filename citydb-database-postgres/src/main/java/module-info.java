import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.postgres.PostgresqlAdapter;

module org.citydb.database.postgres {
    requires org.citydb.database;
    requires org.postgresql.jdbc;

    provides DatabaseAdapter with PostgresqlAdapter;

    exports org.citydb.database.postgres;
}
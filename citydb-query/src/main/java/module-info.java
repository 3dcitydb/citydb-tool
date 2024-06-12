module org.citydb.query {
    requires org.citydb.config;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;
    requires transitive org.citydb.sqlbuilder;

    exports org.citydb.query;
    exports org.citydb.query.filter;
    exports org.citydb.query.filter.common;
    exports org.citydb.query.filter.encoding;
    exports org.citydb.query.filter.function;
    exports org.citydb.query.filter.literal;
    exports org.citydb.query.filter.operation;
}
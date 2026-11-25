module org.citydb.query {
    requires org.citydb.config;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;
    requires transitive org.citydb.sqlbuilder;

    exports org.citydb.query;
    exports org.citydb.query.builder;
    exports org.citydb.query.builder.common;
    exports org.citydb.query.builder.schema;
    exports org.citydb.query.builder.sql;
    exports org.citydb.query.executor;
    exports org.citydb.query.filter;
    exports org.citydb.query.filter.common;
    exports org.citydb.query.filter.encoding;
    exports org.citydb.query.filter.function;
    exports org.citydb.query.filter.literal;
    exports org.citydb.query.filter.operation;
    exports org.citydb.query.lod;
    exports org.citydb.query.sorting;
    exports org.citydb.query.temptable;

    opens org.citydb.query to tools.jackson.databind;
    opens org.citydb.query.lod to tools.jackson.databind;
    opens org.citydb.query.sorting to tools.jackson.databind;
}
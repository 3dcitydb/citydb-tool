module org.citydb.query {
    requires org.citydb.config;
    requires si.uom.units;
    requires systems.uom.common;
    requires transitive org.citydb.database;
    requires transitive org.citydb.model;
    requires transitive org.citydb.sqlbuilder;
    requires transitive java.measure;

    exports org.citydb.query;
    exports org.citydb.query.builder;
    exports org.citydb.query.builder.common;
    exports org.citydb.query.builder.schema;
    exports org.citydb.query.builder.sql;
    exports org.citydb.query.feature;
    exports org.citydb.query.filter;
    exports org.citydb.query.filter.common;
    exports org.citydb.query.filter.encoding;
    exports org.citydb.query.filter.function;
    exports org.citydb.query.filter.literal;
    exports org.citydb.query.filter.operation;
}
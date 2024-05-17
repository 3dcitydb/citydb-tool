module org.citydb.web {
    requires org.citydb.config;
    requires org.citygml4j.core;
    requires org.citydb.io;
    requires org.citydb.io.citygml;
    requires org.citydb.logging;
    requires org.citydb.database;
    requires org.citydb.operation;
    requires io.swagger.v3.core;
    requires io.swagger.v3.oas.annotations;
    requires io.swagger.v3.oas.models;
    requires spring.context;
    requires spring.beans;
    requires spring.web;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.core;
    requires org.apache.logging.log4j;
    requires org.citydb.cli;
    requires spring.data.redis;
    requires spring.aop;

    exports org.citydb.web;
    exports org.citydb.web.swagger;
    exports org.citydb.web.controller;
    exports org.citydb.web.schema;
    exports org.citydb.web.util;
    exports org.citydb.web.management;
    exports org.citydb.web.listener;
    exports org.citydb.web.cache;
    exports org.citydb.web.operation;

    opens org.citydb.web to spring.core;
    opens org.citydb.web.swagger to spring.core;
    opens org.citydb.web.controller to spring.core;
    opens org.citydb.web.schema to spring.core;
    opens org.citydb.web.listener to spring.core;
    opens org.citydb.web.cache to spring.core;
    opens org.citydb.web.operation to spring.core, spring.beans;
    exports org.citydb.web.schema.geojson;
    opens org.citydb.web.schema.geojson to spring.core;
}
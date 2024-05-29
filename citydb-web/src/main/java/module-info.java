module org.citydb.web {
    requires org.citydb.config;
    requires org.citygml4j.core;
    requires org.citydb.io;
    requires org.citydb.io.citygml;
    requires org.citydb.logging;
    requires org.citydb.database;
    requires org.citydb.operation;
    requires org.citydb.cli;
    requires org.citydb.database.postgres;

    requires spring.context;
    requires spring.beans;
    requires spring.web;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.core;
    requires spring.aop;
    requires io.swagger.v3.core;
    requires io.swagger.v3.oas.annotations;
    requires io.swagger.v3.oas.models;
    requires jakarta.servlet;
    requires org.apache.logging.log4j;

    exports org.citydb.web;
    exports org.citydb.web.swagger;
    exports org.citydb.web.controller;
    exports org.citydb.web.schema;
    exports org.citydb.web.util;
    exports org.citydb.web.config;
    exports org.citydb.web.config.feature;
    exports org.citydb.web.listener;
    exports org.citydb.web.service;

    opens org.citydb.web to spring.core;
    opens org.citydb.web.config to spring.core;
    opens org.citydb.web.swagger to spring.core;
    opens org.citydb.web.controller to spring.core;
    opens org.citydb.web.listener to spring.core;
    opens org.citydb.web.service to spring.core, spring.beans;
    opens org.citydb.web.schema.geojson to spring.core;

    exports org.citydb.web.schema.geojson;
    exports org.citydb.web.command;
    opens org.citydb.web.command to info.picocli, spring.core;
    exports org.citydb.web.exception;
    opens org.citydb.web.exception to spring.beans, spring.core;
    opens org.citydb.web.schema to spring.beans, spring.core;
}
module org.citydb.web {
    requires org.citydb.config;
    requires org.citygml4j.core;
    requires org.citydb.io;
    requires org.citydb.io.citygml;
    requires org.citydb.logging;
    requires org.citydb.database;
    requires org.citydb.operation;
    requires io.swagger.v3.oas.models;
    requires spring.context;
    requires spring.beans;
    requires spring.web;
    requires spring.boot.autoconfigure;
    requires spring.boot;
    requires spring.core;

    exports org.citydb.web;
    exports org.citydb.web.config;
    exports org.citydb.web.controller;
    exports org.citydb.web.model;

    opens org.citydb.web to spring.core;
    opens org.citydb.web.config to spring.core;
    opens org.citydb.web.controller to spring.core;
    opens org.citydb.web.model to spring.core;
}
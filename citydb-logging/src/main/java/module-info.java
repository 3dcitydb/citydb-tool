module org.citydb.logging {
    requires org.apache.logging.log4j.core;
    requires transitive org.apache.logging.log4j;

    exports org.citydb.logging;
}
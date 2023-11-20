@SuppressWarnings("requires-transitive-automatic")
module org.citydb.logging {
    requires transitive org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.slf4j;

    exports org.citydb.logging;
}
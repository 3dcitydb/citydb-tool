module org.citydb.io.citygml {
    requires org.citydb.io;
    requires transitive org.citygml4j.xml;
    requires transitive org.citygml4j.cityjson;

    exports org.citydb.io.citygml;
    exports org.citydb.io.citygml.adapter.address;
    exports org.citydb.io.citygml.adapter.appearance;
    exports org.citydb.io.citygml.adapter.appearance.builder;
    exports org.citydb.io.citygml.adapter.bridge;
    exports org.citydb.io.citygml.adapter.building;
    exports org.citydb.io.citygml.adapter.cityfurniture;
    exports org.citydb.io.citygml.adapter.cityobjectgroup;
    exports org.citydb.io.citygml.adapter.construction;
    exports org.citydb.io.citygml.adapter.core;
    exports org.citydb.io.citygml.adapter.dynamizer;
    exports org.citydb.io.citygml.adapter.generics;
    exports org.citydb.io.citygml.adapter.geometry.builder;
    exports org.citydb.io.citygml.adapter.geometry.serializer;
    exports org.citydb.io.citygml.adapter.gml;
    exports org.citydb.io.citygml.adapter.landuse;
    exports org.citydb.io.citygml.adapter.relief;
    exports org.citydb.io.citygml.adapter.transportation;
    exports org.citydb.io.citygml.adapter.tunnel;
    exports org.citydb.io.citygml.adapter.vegetation;
    exports org.citydb.io.citygml.adapter.waterbody;
    exports org.citydb.io.citygml.annotation;
    exports org.citydb.io.citygml.builder;
    exports org.citydb.io.citygml.reader;
    exports org.citydb.io.citygml.reader.options;
    exports org.citydb.io.citygml.reader.preprocess;
    exports org.citydb.io.citygml.reader.util;
    exports org.citydb.io.citygml.serializer;
    exports org.citydb.io.citygml.writer;
    exports org.citydb.io.citygml.writer.options;
    exports org.citydb.io.citygml.writer.preprocess;
    exports org.citydb.io.citygml.writer.util;

    provides org.citydb.io.IOAdapter with org.citydb.io.citygml.CityGMLAdapter,
            org.citydb.io.citygml.CityJSONAdapter;
}
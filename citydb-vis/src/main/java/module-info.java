module org.citydb.vis {
    requires org.citydb.io;
    requires org.citydb.database;
    requires com.alibaba.fastjson2;
    requires org.slf4j;
    requires texture.atlas.creator;
    requires java.desktop;
    requires java.net.http;

    // Public API surface. The module's contract with the outside world is the
    // IOAdapter service below plus the option types a caller needs to
    // configure an export; everything else (writer, pipeline, scene, store,
    // encoder, model, util, attribute) is implementation detail and stays
    // encapsulated so it can be refactored without breaking consumers.
    exports org.citydb.vis;
    exports org.citydb.vis.appearance;
    exports org.citydb.vis.config;
    exports org.citydb.vis.geometry;
    exports org.citydb.vis.styling;

    // The output-format POJOs are serialized by fastjson2 with
    // JSONWriter.Feature.FieldBased (see JsonHelper.writePojo), which reads
    // private fields reflectively — several of these classes have no getters
    // at all. That needs the packages open, not exported: `opens` grants the
    // deep reflective access without making the types part of the module's
    // compile-time API. Both object graphs are closed over their own package
    // (AttrField/AttrType are constructor inputs, not serialized fields).
    //
    // The `opens` are deliberately unqualified. fastjson2 does not reflect
    // from its own module: it ASM-generates one ObjectWriter class per POJO
    // (OWG_2_3_HeightModelInfo etc.) and defines it in the UNNAMED module, so
    // `opens ... to com.alibaba.fastjson2` fails at runtime with
    // IllegalAccessError on the first serialized node — and only on the module
    // path, which the test source set does not exercise. Verified 2026-07-29
    // against the packaged distribution.
    opens org.citydb.vis.model.i3s;
    opens org.citydb.vis.model.tiles3d;

    provides org.citydb.io.IOAdapter with
            org.citydb.vis.I3SAdapter,
            org.citydb.vis.Tiles3DAdapter;
}

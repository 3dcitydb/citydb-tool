module org.citydb.vis {
    requires org.citydb.io;
    requires com.alibaba.fastjson2;
    requires org.slf4j;
    requires drako;
    requires texture.atlas.creator;
    requires java.desktop;

    exports org.citydb.vis;
    exports org.citydb.vis.appearance;
    exports org.citydb.vis.encoder;
    exports org.citydb.vis.encoder.i3s;
    exports org.citydb.vis.encoder.tiles3d;
    exports org.citydb.vis.geometry;
    exports org.citydb.vis.scene;
    exports org.citydb.vis.store;
    exports org.citydb.vis.styling;
    exports org.citydb.vis.util;
    exports org.citydb.vis.writer;
    exports org.citydb.vis.writer.i3s;
    exports org.citydb.vis.writer.tiles3d;
    exports org.citydb.vis.model;
    exports org.citydb.vis.model.i3s;
    exports org.citydb.vis.model.tiles3d;

    provides org.citydb.io.IOAdapter with
            org.citydb.vis.I3SAdapter,
            org.citydb.vis.Tiles3DAdapter;
}

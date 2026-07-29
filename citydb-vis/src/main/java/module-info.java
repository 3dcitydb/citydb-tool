module org.citydb.vis {
    requires org.citydb.io;
    requires org.citydb.database;
    requires com.alibaba.fastjson2;
    requires org.slf4j;
    requires texture.atlas.creator;
    requires java.desktop;
    requires java.net.http;

    exports org.citydb.vis;
    exports org.citydb.vis.appearance;
    exports org.citydb.vis.geometry;
    exports org.citydb.vis.options;
    exports org.citydb.vis.styling;

    opens org.citydb.vis.model.i3s;
    opens org.citydb.vis.model.tiles3d;

    provides org.citydb.io.IOAdapter with
            org.citydb.vis.I3SAdapter,
            org.citydb.vis.Tiles3DAdapter;
}

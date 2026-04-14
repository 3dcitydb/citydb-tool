/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identify/popup configuration for the I3S scene layer.
 *
 * <p>ArcGIS Pro requires {@code fieldInfos} to appear <em>both</em> at the
 * popupInfo root (for schema/metadata purposes) <em>and</em> inline inside
 * the {@code {type:"fields"}} popup element (for actual rendering). Dropping
 * either copy leaves the popup body empty on local SLPK loads even when the
 * attribute buffers are correct. An empty {@code mediaInfos} list is also
 * emitted to match the shape of reference layers (e.g. NYC Attributed).
 */
@JSONType(alphabetic = false)
public class PopupInfo {
    private String title;
    private List<FieldInfo> fieldInfos;
    private List<Map<String, Object>> popupElements;
    private List<Object> mediaInfos;

    public static PopupInfo of(List<AttrField> attrFields) {
        PopupInfo info = new PopupInfo();
        info.title = "{OBJECTID}";

        List<FieldInfo> fieldInfos = new ArrayList<>(attrFields.size());
        for (AttrField field : attrFields) {
            fieldInfos.add(new FieldInfo(field.name(), field.name(), true));
        }
        info.fieldInfos = fieldInfos;

        Map<String, Object> fieldsElement = new LinkedHashMap<>();
        fieldsElement.put("type", "fields");
        fieldsElement.put("fieldInfos", fieldInfos);

        info.popupElements = List.of(fieldsElement);
        info.mediaInfos = List.of();
        return info;
    }

    @JSONType(alphabetic = false)
    public record FieldInfo(String fieldName, String label, boolean visible) {
    }
}

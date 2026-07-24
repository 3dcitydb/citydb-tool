/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

import java.util.List;

public class AttributeOptions implements Option {
    @CommandLine.Option(names = "--attributes", split = ",", paramLabel = "<col:src>",
            description = "Declarative whitelist of columns to emit on the per-feature " +
                    "attribute table. Each comma-separated entry has the form " +
                    "'<output_col>:<source>'. <source> is '<TABLE>/[<AGG>]<col_path>' where " +
                    "TABLE is FEATURE, ATTRIBUTES, or ADDRESS; AGG (optional, defaults to FIRST) " +
                    "is FIRST/LAST/COUNT/ALL. For FEATURE, <col_path> is a single field name. " +
                    "For ADDRESS, <col_path> is a single field optionally followed by " +
                    "'[<field>=<value>]' to filter address rows. For ATTRIBUTES, <col_path> is a " +
                    "dotted chain of localName segments where any segment may carry its own " +
                    "'[<field>=<value>]' predicate keeping only nodes whose child <field> equals " +
                    "<value>. Predicate <value> is a quoted string ('Munich'), integer, decimal, " +
                    "or boolean (true/false). ATTRIBUTES paths may end with '::<type>' to force " +
                    "the leaf value extraction to a specific Attribute value type — one of " +
                    "'int', 'double', 'string', 'timestamp', 'uri', 'array' (\"; \"-joined " +
                    "ArrayValue elements), or one of the metadata-side casts: 'code' " +
                    "(codeSpace URI of a CityGML coded value), 'uom' (unit of measure), " +
                    "'content' (generic content blob), 'mimeType' (mime type of the generic " +
                    "content). Without a cast the encoder auto-detects the present " +
                    "value type (int → double → string → timestamp → uri, with fallback into " +
                    "a 'value' sub-attribute). Examples: " +
                    "'OBJECTID:FEATURE/objectid', 'HEIGHT:ATTRIBUTES/measuredHeight', " +
                    "'CITY:ADDRESS/[FIRST]city', 'STREET:ADDRESS/[FIRST]street', " +
                    "'HOUSE_NUMBER:ADDRESS/[FIRST]houseNumber', " +
                    "'TARGET_RESOURCE:ATTRIBUTES/[FIRST]externalReference::uri'. " +
                    "Use picocli's '@file' syntax for long lists, e.g. --attributes @cols.txt " +
                    "(each non-blank line of <cols.txt> is one entry; '#' comments are NOT " +
                    "supported in @file mode). Case rules: TABLE, AGG, the '::type' cast, and " +
                    "FEATURE / ADDRESS field names are all case-insensitive; ATTRIBUTES path " +
                    "segments are case-sensitive (they match XML localNames literally). " +
                    "Without this option, every top-level attribute is exported " +
                    "(default behaviour).")
    private List<String> attributes;

    /**
     * Raw mapping tokens from {@code --attributes}, already comma-split
     * by picocli. The controller hands these straight to
     * {@link org.citydb.vis.attribute.AttributeProjection#parse}. Empty
     * list when the option was not provided.
     */
    public List<String> getAttributes() {
        return attributes != null ? attributes : List.of();
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.util;

import java.util.UUID;

public class IdCreator {
    private static final IdCreator instance = new IdCreator();
    private String prefix;

    private IdCreator() {
        prefix = getDefaultPrefix();
    }

    public static IdCreator getInstance() {
        return instance;
    }

    public static IdCreator newInstance(String prefix) {
        return new IdCreator().withPrefix(prefix);
    }

    public String getDefaultPrefix() {
        return "ID_";
    }

    public String getPrefix() {
        return prefix;
    }

    public IdCreator withPrefix(String prefix) {
        this.prefix = prefix != null ? prefix : getDefaultPrefix();
        return this;
    }

    public String createId() {
        return prefix + UUID.randomUUID();
    }
}

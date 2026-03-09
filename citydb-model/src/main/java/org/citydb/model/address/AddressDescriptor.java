/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.address;

import org.citydb.model.common.DatabaseDescriptor;

public class AddressDescriptor extends DatabaseDescriptor {

    private AddressDescriptor(long id) {
        super(id);
    }

    public static AddressDescriptor of(long id) {
        return new AddressDescriptor(id);
    }
}

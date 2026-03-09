/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.changelog.options;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.encoding.DateTimeReader;

import java.time.OffsetDateTime;
import java.util.Optional;

public class TransactionDate {
    @JSONField(deserializeUsing = DateTimeReader.class)
    private OffsetDateTime after;
    @JSONField(deserializeUsing = DateTimeReader.class)
    private OffsetDateTime until;

    public static TransactionDate after(OffsetDateTime after) {
        return new TransactionDate().setAfter(after);
    }

    public static TransactionDate until(OffsetDateTime until) {
        return new TransactionDate().setUntil(until);
    }

    public static TransactionDate range(OffsetDateTime after, OffsetDateTime until) {
        return new TransactionDate().setAfter(after).setUntil(until);
    }

    public Optional<OffsetDateTime> getAfter() {
        return Optional.ofNullable(after);
    }

    public TransactionDate setAfter(OffsetDateTime after) {
        this.after = after;
        return this;
    }

    public Optional<OffsetDateTime> getUntil() {
        return Optional.ofNullable(until);
    }

    public TransactionDate setUntil(OffsetDateTime until) {
        this.until = until;
        return this;
    }
}

/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

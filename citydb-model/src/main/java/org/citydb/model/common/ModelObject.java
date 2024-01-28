/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.model.common;

import java.time.OffsetDateTime;
import java.util.Optional;

public abstract class ModelObject<T extends ModelObject<?>> extends Child implements Identifiable, Visitable {
    private String objectId;
    private String identifier;
    private String identifierCodeSpace;
    private OffsetDateTime creationDate;
    private OffsetDateTime terminationDate;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;

    protected abstract T self();

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public T setObjectId(String objectId) {
        this.objectId = objectId;
        return self();
    }

    @Override
    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    @Override
    public T setIdentifier(String identifier) {
        this.identifier = identifier;
        return self();
    }

    @Override
    public Optional<String> getIdentifierCodeSpace() {
        return Optional.ofNullable(identifierCodeSpace);
    }

    @Override
    public T setIdentifierCodeSpace(String identifierCodeSpace) {
        this.identifierCodeSpace = identifierCodeSpace;
        return self();
    }

    public Optional<OffsetDateTime> getCreationDate() {
        return Optional.ofNullable(creationDate);
    }

    public T setCreationDate(OffsetDateTime creationDate) {
        this.creationDate = creationDate;
        return self();
    }

    public Optional<OffsetDateTime> getTerminationDate() {
        return Optional.ofNullable(terminationDate);
    }

    public T setTerminationDate(OffsetDateTime terminationDate) {
        this.terminationDate = terminationDate;
        return self();
    }

    public Optional<OffsetDateTime> getValidFrom() {
        return Optional.ofNullable(validFrom);
    }

    public T setValidFrom(OffsetDateTime validFrom) {
        this.validFrom = validFrom;
        return self();
    }

    public Optional<OffsetDateTime> getValidTo() {
        return Optional.ofNullable(validTo);
    }

    public T setValidTo(OffsetDateTime validTo) {
        this.validTo = validTo;
        return self();
    }
}

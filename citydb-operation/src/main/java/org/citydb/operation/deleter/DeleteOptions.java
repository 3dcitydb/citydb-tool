/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.operation.deleter;

import org.citydb.operation.deleter.options.DeleteMode;

import java.time.OffsetDateTime;

public class DeleteOptions {
    private int numberOfThreads;
    private DeleteMode mode;
    private String updatingPerson;
    private String reasonForUpdate;
    private String lineage;
    private OffsetDateTime terminationDate;

    private DeleteOptions() {
    }

    public static DeleteOptions defaults() {
        return new DeleteOptions();
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public DeleteOptions setNumberOfThreads(int numberOfThreads) {
        if (numberOfThreads > 0) {
            this.numberOfThreads = numberOfThreads;
        }

        return this;
    }

    public DeleteMode getMode() {
        return mode != null ? mode : DeleteMode.DELETE;
    }

    public DeleteOptions setMode(DeleteMode mode) {
        this.mode = mode;
        return this;
    }

    public String getUpdatingPerson() {
        return updatingPerson;
    }

    public DeleteOptions setUpdatingPerson(String updatingPerson) {
        this.updatingPerson = updatingPerson;
        return this;
    }

    public String getReasonForUpdate() {
        return reasonForUpdate;
    }

    public DeleteOptions setReasonForUpdate(String reasonForUpdate) {
        this.reasonForUpdate = reasonForUpdate;
        return this;
    }

    public String getLineage() {
        return lineage;
    }

    public DeleteOptions setLineage(String lineage) {
        this.lineage = lineage;
        return this;
    }

    public OffsetDateTime getTerminationDate() {
        return terminationDate;
    }

    public DeleteOptions setTerminationDate(OffsetDateTime terminationDate) {
        this.terminationDate = terminationDate;
        return this;
    }
}

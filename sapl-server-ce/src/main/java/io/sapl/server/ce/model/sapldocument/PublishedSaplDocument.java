/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.server.ce.model.sapldocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Setter
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Table(name = "PublishedSaplDocument")
public class PublishedSaplDocument {
    @Id
    @Column(name = "saplDocumentId", nullable = false)
    private Long saplDocumentId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(length = 250, unique = true, nullable = false)
    private String documentName;

    @Column(length = SaplDocumentVersion.MAX_DOCUMENT_SIZE, nullable = false)
    private String document;

    public void importSaplDocumentVersion(@NonNull SaplDocumentVersion saplDocumentVersion) {
        setSaplDocumentId(saplDocumentVersion.getSaplDocument().getId());
        setVersion(saplDocumentVersion.getVersionNumber());
        setDocumentName(saplDocumentVersion.getName());
        setDocument(saplDocumentVersion.getDocumentContent());
    }
}

/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.SaplVersion;
import io.sapl.interpreter.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A SAPL document.
 */
@Getter
@Setter
@ToString
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Table(name = "SaplDocument")
public class SaplDocument implements Serializable {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    /**
     * The unique identifier of the SAPL document.
     */
    @Id
    @GeneratedValue
    @Column(name = "Id", nullable = false)
    private Long id;

    /**
     * The number of the current version.
     */
    @Column
    private int currentVersionNumber;

    /**
     * The published version. The value is <b>null</b>, if no version of the SAPL
     * document is published.
     */
    @OneToOne
    private SaplDocumentVersion publishedVersion;

    /**
     * The version.
     */
    @Column
    private String lastModified;

    /**
     * The name of the current version of the document.
     */
    @Column
    private String name;

    /**
     * The {@link DocumentType}.
     */
    @Column
    private DocumentType type;

    @ToString.Exclude
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "saplDocument")
    private List<SaplDocumentVersion> versions = new ArrayList<>();

    /**
     * Gets the current version of the SAPL document as {@link SaplDocumentVersion}.
     * If no version is available, <b>null</b> will be returned.
     *
     * @return the current version
     */
    public SaplDocumentVersion getCurrentVersion() {
        Optional<SaplDocumentVersion> versionWithHighestVersion = versions.stream()
                .max(Comparator.comparingInt(SaplDocumentVersion::getVersionNumber));
        return versionWithHighestVersion.orElse(null);
    }

    /**
     * Gets a specific {@link SaplDocumentVersion} by its version number.
     *
     * @param version the version number
     * @return {@link SaplDocumentVersion}
     * @exception IllegalArgumentException the version was not found
     */
    public SaplDocumentVersion getVersion(int version) {
        Optional<SaplDocumentVersion> versionAsOptional = getVersions().stream()
                .filter(currentSaplDocumentVersion -> currentSaplDocumentVersion.getVersionNumber() == version)
                .findFirst();

        if (versionAsOptional.isPresent()) {
            return versionAsOptional.get();
        }

        throw new IllegalArgumentException(String.format("version %d was not found", version));
    }

    /**
     * Gets a {@link String} representation of the published version number.
     *
     * @return the {@link String} representation
     */
    public String getPublishedVersionNumberAsString() {
        SaplDocumentVersion publishedDocumentVersion = getPublishedVersion();
        if (publishedDocumentVersion != null) {
            return Integer.toString(publishedDocumentVersion.getVersionNumber());
        } else {
            return "-";
        }
    }

    /**
     * Gets a {@link String} representation of the {@link DocumentType}.
     *
     * @return the {@link String} representation
     */
    public String getTypeAsString() {
        return switch (type) {
        case POLICY     -> "Policy";
        case POLICY_SET -> "Policy Set";
        case INVALID    -> "Invalid Document";
        };
    }
}

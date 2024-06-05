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

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Entity for a version of a SAPL document.
 */
@Getter
@Setter
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Table(name = "SaplDocumentVersion")
public class SaplDocumentVersion implements Serializable {

    private static final long serialVersionUID = -1486363140560030675L;

    public static final int MAX_DOCUMENT_SIZE = 64000;

    /**
     * The unique identifier of the SAPL document version.
     */
    @Id
    @GeneratedValue
    @Column(name = "VersionId", nullable = false)
    private Long versionId;

    /**
     * The {@link SaplDocument} this version belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saplDocument_fk")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SaplDocument saplDocument;

    /**
     * The version number.
     */
    @Column
    private int versionNumber;

    /**
     * The value / text of the SAPL document version.
     */
    @Column(length = MAX_DOCUMENT_SIZE)
    private String documentContent;

    /**
     * The name included in the value / text of the SAPL document version
     * (redundancy for better query performance).
     */
    @Column(length = 1024)
    private String name;
}

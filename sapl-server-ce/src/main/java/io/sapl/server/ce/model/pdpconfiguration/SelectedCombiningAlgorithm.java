/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.model.pdpconfiguration;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import jakarta.persistence.*;
import lombok.*;

/**
 * The selected combining algorithm, stored as three separate components.
 */
@Getter
@Setter
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "SelectedCombiningAlgorithm")
public class SelectedCombiningAlgorithm {

    /**
     * Creates entity from a CombiningAlgorithm record.
     */
    public SelectedCombiningAlgorithm(@NonNull CombiningAlgorithm algorithm) {
        this.votingMode      = algorithm.votingMode();
        this.defaultDecision = algorithm.defaultDecision();
        this.errorHandling   = algorithm.errorHandling();
    }

    /**
     * The unique identifier.
     */
    @Id
    @GeneratedValue
    @Column(name = "Id", nullable = false)
    private Long id;

    /**
     * The voting mode component.
     */
    @Column
    @Enumerated(EnumType.STRING)
    private VotingMode votingMode;

    /**
     * The default decision component.
     */
    @Column
    @Enumerated(EnumType.STRING)
    private DefaultDecision defaultDecision;

    /**
     * The error handling component.
     */
    @Column
    @Enumerated(EnumType.STRING)
    private ErrorHandling errorHandling;

    /**
     * Builds the CombiningAlgorithm record from the stored components.
     */
    public CombiningAlgorithm toCombiningAlgorithm() {
        return new CombiningAlgorithm(votingMode, defaultDecision, errorHandling);
    }
}

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
package io.sapl.pdp.configuration;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a PDP's operational status, including configuration
 * metadata and load history.
 *
 * @param state the current operational state
 * @param configurationId identifier of the active configuration, or null if
 * ERROR
 * @param combiningAlgorithm canonical string of the combining algorithm, or
 * null if ERROR
 * @param documentCount number of SAPL documents in the active configuration
 * @param lastSuccessfulLoad timestamp of the last successful configuration
 * load, or null if never loaded
 * @param lastFailedLoad timestamp of the last failed configuration load, or
 * null if no failure occurred
 * @param lastError formatted error message from the last failed load, or null
 * if no failure occurred
 */
public record PdpStatus(
        PdpState state,
        @Nullable String configurationId,
        @Nullable String combiningAlgorithm,
        int documentCount,
        @Nullable Instant lastSuccessfulLoad,
        @Nullable Instant lastFailedLoad,
        @Nullable String lastError) {

    /**
     * Creates an initial error status with no history. Before any successful
     * load, the PDP is in an error state as it cannot serve valid decisions.
     *
     * @return a new initial PDP status
     */
    public static PdpStatus initial() {
        return new PdpStatus(PdpState.ERROR, null, null, 0, null, null, null);
    }

}

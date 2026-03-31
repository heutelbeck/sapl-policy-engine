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
package io.sapl.api.pdp;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.sapl.api.SaplVersion;

/**
 * Immutable configuration for a Policy Decision Point.
 *
 * @param pdpId the PDP identifier
 * @param configurationId the configuration version identifier
 * @param combiningAlgorithm the policy combining algorithm
 * @param indexing the policy index strategy; defaults to
 * {@link IndexingStrategy#AUTO} when not specified in pdp.json
 * @param saplDocuments the SAPL document source strings
 * @param data PDP-level variables and secrets
 */
public record PDPConfiguration(
        String pdpId,
        String configurationId,
        CombiningAlgorithm combiningAlgorithm,
        IndexingStrategy indexing,
        List<String> saplDocuments,
        PdpData data) implements Serializable {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Convenience constructor defaulting indexing to {@link IndexingStrategy#AUTO}.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            List<String> saplDocuments,
            PdpData data) {
        this(pdpId, configurationId, combiningAlgorithm, IndexingStrategy.AUTO, saplDocuments, data);
    }
}

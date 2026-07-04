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
package io.sapl.api.pdp.configuration;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;

/**
 * Immutable configuration for a Policy Decision Point.
 *
 * @param pdpId the PDP identifier
 * @param configurationId the configuration version identifier
 * @param combiningAlgorithm the policy combining algorithm
 * @param compilerOptions opaque compiler options (indexing strategy,
 * tuning parameters). Each consumer reads the keys it understands.
 * @param saplDocuments the SAPL document source strings
 * @param data PDP-level variables and secrets
 * @param extensions bundle-carried extension data, keyed by extension name
 * (cleartext)
 * @param extensionSecrets bundle-carried extension secrets, keyed by extension
 * name (sealed in transit, unsealed by the recipient)
 * @param criticalExtensions names of extensions the consumer must be able to
 * process, else the configuration is rejected
 */
public record PDPConfiguration(
        String pdpId,
        String configurationId,
        CombiningAlgorithm combiningAlgorithm,
        ObjectValue compilerOptions,
        List<String> saplDocuments,
        PdpData data,
        Map<String, Value> extensions,
        Map<String, Value> extensionSecrets,
        Set<String> criticalExtensions) implements Serializable {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public PDPConfiguration {
        extensions         = extensions == null ? Map.of() : Map.copyOf(extensions);
        extensionSecrets   = extensionSecrets == null ? Map.of() : Map.copyOf(extensionSecrets);
        criticalExtensions = criticalExtensions == null ? Set.of() : Set.copyOf(criticalExtensions);
    }

    /**
     * Convenience constructor defaulting extension data to empty.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            ObjectValue compilerOptions,
            List<String> saplDocuments,
            PdpData data) {
        this(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data, Map.of(), Map.of(),
                Set.of());
    }

    /**
     * Convenience constructor defaulting compiler options and extension data to
     * empty.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            List<String> saplDocuments,
            PdpData data) {
        this(pdpId, configurationId, combiningAlgorithm, Value.EMPTY_OBJECT, saplDocuments, data);
    }

    /**
     * Returns a copy of this configuration with the given extension data,
     * preserving the current critical extension set.
     *
     * @param extensions the cleartext extension data, keyed by extension name
     * @param extensionSecrets the extension secrets, keyed by extension name
     *
     * @return a copy carrying the extension data
     */
    public PDPConfiguration withExtensions(Map<String, Value> extensions, Map<String, Value> extensionSecrets) {
        return withExtensions(extensions, extensionSecrets, criticalExtensions);
    }

    /**
     * Returns a copy of this configuration with the given extension data and
     * critical extension set.
     *
     * @param extensions the cleartext extension data, keyed by extension name
     * @param extensionSecrets the extension secrets, keyed by extension name
     * @param criticalExtensions names of extensions the consumer must be able to
     * process
     *
     * @return a copy carrying the extension data
     */
    public PDPConfiguration withExtensions(Map<String, Value> extensions, Map<String, Value> extensionSecrets,
            Set<String> criticalExtensions) {
        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data,
                extensions, extensionSecrets, criticalExtensions);
    }

    @Override
    public String toString() {
        return "PDPConfiguration[pdpId=" + pdpId + ", configurationId=" + configurationId + ", combiningAlgorithm="
                + combiningAlgorithm + ", compilerOptions=" + compilerOptions + ", saplDocuments=" + saplDocuments
                + ", data=" + data + ", extensions=" + extensions.keySet() + ", extensionSecrets="
                + (extensionSecrets.isEmpty() ? "NO EXTENSION SECRETS" : "EXTENSION SECRETS REDACTED")
                + ", criticalExtensions=" + criticalExtensions + "]";
    }
}

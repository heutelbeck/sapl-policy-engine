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

import static org.semver4j.range.RangeListFactory.create;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.semver4j.processor.HyphenProcessor;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;

import lombok.val;

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
 * @param requiredExtensions names of extensions the consumer must be able to
 * process, else the configuration is rejected
 * @param provenance where this configuration originated. Descriptive metadata
 * only: it never influences policy decisions and never enters the
 * content-derived configuration id
 * @param requiredEngineVersion optional semantic version range for the engine,
 * or null if no engine version requirement is declared
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
        Set<String> requiredExtensions,
        ConfigurationProvenance provenance,
        @Nullable String requiredEngineVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String ERROR_INVALID_ENGINE_VERSION_RANGE = "Invalid requiredEngineVersion range: '%s'.";
    private static final HyphenProcessor ENGINE_VERSION_RANGE_PROCESSOR = new HyphenProcessor();

    /**
     * Creates a configuration with a validated engine requirement and immutable extension data.
     *
     * @throws IllegalArgumentException if a declared engine version range is blank
     * or syntactically invalid
     */
    public PDPConfiguration {
        if (requiredEngineVersion != null && !isValidEngineVersionRange(requiredEngineVersion)) {
            throw new IllegalArgumentException(ERROR_INVALID_ENGINE_VERSION_RANGE.formatted(requiredEngineVersion));
        }
        extensions         = extensions == null ? Map.of() : Map.copyOf(extensions);
        extensionSecrets   = extensionSecrets == null ? Map.of() : Map.copyOf(extensionSecrets);
        requiredExtensions = requiredExtensions == null ? Set.of() : Set.copyOf(requiredExtensions);
        provenance         = provenance == null ? UnknownProvenance.INSTANCE : provenance;
    }

    /**
     * Convenience constructor without an engine version requirement.
     *
     * @param pdpId the PDP identifier
     * @param configurationId the configuration version identifier
     * @param combiningAlgorithm the policy combining algorithm
     * @param compilerOptions the compiler options
     * @param saplDocuments the SAPL document source strings
     * @param data the PDP variables and secrets
     * @param extensions the cleartext extension data
     * @param extensionSecrets the extension secrets
     * @param requiredExtensions the required extension names
     * @param provenance the configuration origin metadata
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            ObjectValue compilerOptions,
            List<String> saplDocuments,
            PdpData data,
            Map<String, Value> extensions,
            Map<String, Value> extensionSecrets,
            Set<String> requiredExtensions,
            ConfigurationProvenance provenance) {
        this(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data, extensions,
                extensionSecrets, requiredExtensions, provenance, null);
    }

    /**
     * Convenience constructor defaulting the provenance to
     * {@link UnknownProvenance#INSTANCE}.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            ObjectValue compilerOptions,
            List<String> saplDocuments,
            PdpData data,
            Map<String, Value> extensions,
            Map<String, Value> extensionSecrets,
            Set<String> requiredExtensions) {
        this(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data, extensions,
                extensionSecrets, requiredExtensions, UnknownProvenance.INSTANCE);
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
     * preserving the current required extension set.
     *
     * @param extensions the cleartext extension data, keyed by extension name
     * @param extensionSecrets the extension secrets, keyed by extension name
     *
     * @return a copy carrying the extension data
     */
    public PDPConfiguration withExtensions(Map<String, Value> extensions, Map<String, Value> extensionSecrets) {
        return withExtensions(extensions, extensionSecrets, requiredExtensions);
    }

    /**
     * Returns a copy of this configuration with the given extension data and
     * required extension set.
     *
     * @param extensions the cleartext extension data, keyed by extension name
     * @param extensionSecrets the extension secrets, keyed by extension name
     * @param requiredExtensions names of extensions the consumer must be able to
     * process
     *
     * @return a copy carrying the extension data
     */
    public PDPConfiguration withExtensions(Map<String, Value> extensions, Map<String, Value> extensionSecrets,
            Set<String> requiredExtensions) {
        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data,
                extensions, extensionSecrets, requiredExtensions, provenance, requiredEngineVersion);
    }

    @Override
    public String toString() {
        return "PDPConfiguration[pdpId=" + pdpId + ", configurationId=" + configurationId + ", combiningAlgorithm="
                + combiningAlgorithm + ", compilerOptions=" + compilerOptions + ", saplDocuments=" + saplDocuments
                + ", data=" + data + ", extensions=" + extensions.keySet() + ", extensionSecrets="
                + (extensionSecrets.isEmpty() ? "NO EXTENSION SECRETS" : "EXTENSION SECRETS REDACTED")
                + ", requiredExtensions=" + requiredExtensions + ", provenance=" + provenance
                + ", requiredEngineVersion=" + requiredEngineVersion + "]";
    }

    private static boolean isValidEngineVersionRange(String expression) {
        if (expression.isBlank()) {
            return false;
        }
        try {
            for (val section : expression.split("\\|\\|", -1)) {
                if (!isValidEngineVersionRangeSection(section)) {
                    return false;
                }
            }
            return !create(expression).get().isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isValidEngineVersionRangeSection(String section) {
        if (section.isBlank()) {
            return false;
        }
        val normalized = section.strip().replaceAll("([<>]=?|=)\\s+", "$1");
        val expanded   = ENGINE_VERSION_RANGE_PROCESSOR.process(normalized, false);
        // Semver4j otherwise silently ignores unrecognized comparator tokens.
        for (val token : (expanded == null ? normalized : expanded).split("\\s+")) {
            if (create(token).get().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}

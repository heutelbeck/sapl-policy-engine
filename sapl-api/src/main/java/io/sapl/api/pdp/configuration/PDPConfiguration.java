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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.plugins.PluginRequirement;
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
 * @param criticalExtensions names of extensions the consumer must be able to
 * process, else the configuration is rejected
 * @param pluginRequirements the plugins enabled for this PDP, each with the
 * range of versions the PDP accepts, at most one requirement per plugin. Only
 * these plugins, in an accepted version, contribute function libraries, policy
 * information points and decision interceptors to this PDP. Empty means no
 * plugins
 * @param provenance where this configuration originated. Descriptive metadata
 * only: it never influences policy decisions and never enters the
 * content-derived configuration id
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
        Set<String> criticalExtensions,
        Set<PluginRequirement> pluginRequirements,
        ConfigurationProvenance provenance) implements Serializable {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String ERROR_DUPLICATE_PLUGIN_ID = "PDPConfiguration must contain at most one plugin requirement per plugin, found duplicate plugin id: %s.";

    public PDPConfiguration {
        extensions         = extensions == null ? Map.of() : Map.copyOf(extensions);
        extensionSecrets   = extensionSecrets == null ? Map.of() : Map.copyOf(extensionSecrets);
        criticalExtensions = criticalExtensions == null ? Set.of() : Set.copyOf(criticalExtensions);
        pluginRequirements = pluginRequirements == null ? Set.of() : Set.copyOf(pluginRequirements);
        provenance         = provenance == null ? UnknownProvenance.INSTANCE : provenance;

        val pluginIds = new HashSet<String>();
        for (val requirement : pluginRequirements) {
            if (!pluginIds.add(requirement.pluginId())) {
                throw new IllegalArgumentException(ERROR_DUPLICATE_PLUGIN_ID.formatted(requirement.pluginId()));
            }
        }
    }

    /**
     * Convenience constructor defaulting the plugins to empty.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            ObjectValue compilerOptions,
            List<String> saplDocuments,
            PdpData data,
            Map<String, Value> extensions,
            Map<String, Value> extensionSecrets,
            Set<String> criticalExtensions,
            ConfigurationProvenance provenance) {
        this(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data, extensions,
                extensionSecrets, criticalExtensions, Set.of(), provenance);
    }

    /**
     * Convenience constructor defaulting the plugins to empty and the provenance
     * to {@link UnknownProvenance#INSTANCE}.
     */
    public PDPConfiguration(String pdpId,
            String configurationId,
            CombiningAlgorithm combiningAlgorithm,
            ObjectValue compilerOptions,
            List<String> saplDocuments,
            PdpData data,
            Map<String, Value> extensions,
            Map<String, Value> extensionSecrets,
            Set<String> criticalExtensions) {
        this(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data, extensions,
                extensionSecrets, criticalExtensions, UnknownProvenance.INSTANCE);
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
                extensions, extensionSecrets, criticalExtensions, pluginRequirements, provenance);
    }

    /**
     * Returns a copy of this configuration with the given set of enabled plugins.
     *
     * @param pluginRequirements the plugins enabled for this PDP, each with the
     * range of versions the PDP accepts, at most one requirement per plugin
     *
     * @return a copy carrying the plugin requirements
     *
     * @throws IllegalArgumentException if two requirements name the same plugin
     */
    public PDPConfiguration withPlugins(Set<PluginRequirement> pluginRequirements) {
        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data,
                extensions, extensionSecrets, criticalExtensions, pluginRequirements, provenance);
    }

    /**
     * Returns a copy of this configuration with the given provenance.
     *
     * @param provenance where this configuration originated
     *
     * @return a copy carrying the provenance
     */
    public PDPConfiguration withProvenance(ConfigurationProvenance provenance) {
        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data,
                extensions, extensionSecrets, criticalExtensions, pluginRequirements, provenance);
    }

    /**
     * Returns a copy of this configuration with the given PDP data and extension
     * secrets, for example after unsealing.
     *
     * @param data PDP-level variables and secrets
     * @param extensionSecrets extension secrets, keyed by extension name
     *
     * @return a copy carrying the given data and extension secrets
     */
    public PDPConfiguration withSecrets(PdpData data, Map<String, Value> extensionSecrets) {
        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, compilerOptions, saplDocuments, data,
                extensions, extensionSecrets, criticalExtensions, pluginRequirements, provenance);
    }

    @Override
    public String toString() {
        return "PDPConfiguration[pdpId=" + pdpId + ", configurationId=" + configurationId + ", combiningAlgorithm="
                + combiningAlgorithm + ", compilerOptions=" + compilerOptions + ", saplDocuments=" + saplDocuments
                + ", data=" + data + ", extensions=" + extensions.keySet() + ", extensionSecrets="
                + (extensionSecrets.isEmpty() ? "NO EXTENSION SECRETS" : "EXTENSION SECRETS REDACTED")
                + ", criticalExtensions=" + criticalExtensions + ", pluginRequirements=" + pluginRequirements
                + ", provenance=" + provenance + "]";
    }
}

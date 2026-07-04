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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared conventions for extension artifacts carried alongside a PDP configuration. Extensions are supplementary named
 * JSON files that consumers other than the PDP read, for example to configure upstream connections. They are carried
 * identically whether they sit loose in a policy directory or zipped in a bundle, so both sources use these helpers to
 * recognize the files, parse the critical set, and validate structural integrity.
 * <p>
 * The files are:
 * <ul>
 * <li>{@code ext-<name>.json}: cleartext extension configuration.</li>
 * <li>{@code ext-<name>-secrets.json}: sealed extension secrets.</li>
 * <li>{@code critical-extensions.json}: a JSON array of extension names the consumer must be able to process.</li>
 * </ul>
 */
@UtilityClass
public class ExtensionFiles {

    /**
     * Filename prefix shared by both extension file kinds.
     */
    public static final String EXTENSION_PREFIX = "ext-";

    /**
     * Filename suffix of a cleartext extension configuration file.
     */
    public static final String EXTENSION_SUFFIX = ".json";

    /**
     * Filename suffix of a sealed extension secrets file.
     */
    public static final String EXTENSION_SECRETS_SUFFIX = "-secrets.json";

    /**
     * Name of the file declaring the critical extension set.
     */
    public static final String CRITICAL_EXTENSIONS_FILE = "critical-extensions.json";

    private static final String ERROR_CRITICAL_WITHOUT_CONFIG       = "Critical extension '%s' is declared but its 'ext-%s.json' configuration is missing.";
    private static final String ERROR_MALFORMED_CRITICAL_EXTENSIONS = "The 'critical-extensions.json' file must be a JSON array of extension name strings.";
    private static final String ERROR_SECRETS_WITHOUT_CONFIG        = "Extension '%s' has sealed secrets but its 'ext-%s.json' configuration is missing.";

    /**
     * @param fileName
     * a root-level file name
     *
     * @return true if the name is a sealed extension secrets file
     */
    public static boolean isExtensionSecretsFile(String fileName) {
        return fileName.startsWith(EXTENSION_PREFIX) && fileName.endsWith(EXTENSION_SECRETS_SUFFIX);
    }

    /**
     * @param fileName
     * a root-level file name
     *
     * @return true if the name is a cleartext extension configuration file
     */
    public static boolean isExtensionFile(String fileName) {
        return fileName.startsWith(EXTENSION_PREFIX) && fileName.endsWith(EXTENSION_SUFFIX)
                && !isExtensionSecretsFile(fileName);
    }

    /**
     * @param fileName
     * a cleartext extension file name
     *
     * @return the extension name with the prefix and suffix stripped
     */
    public static String extensionNameOf(String fileName) {
        return fileName.substring(EXTENSION_PREFIX.length(), fileName.length() - EXTENSION_SUFFIX.length());
    }

    /**
     * @param fileName
     * a sealed extension secrets file name
     *
     * @return the extension name with the prefix and secrets suffix stripped
     */
    public static String extensionSecretsNameOf(String fileName) {
        return fileName.substring(EXTENSION_PREFIX.length(), fileName.length() - EXTENSION_SECRETS_SUFFIX.length());
    }

    /**
     * Parses the content of a {@code critical-extensions.json} file.
     *
     * @param json
     * the file content, or null or blank when absent
     *
     * @return the critical extension names, empty when the content is absent
     *
     * @throws PDPConfigurationException
     * if the content is not a JSON array of strings
     */
    public static Set<String> parseCriticalExtensions(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        Value parsed;
        try {
            parsed = Value.ofJson(json);
        } catch (RuntimeException e) {
            throw new PDPConfigurationException(ERROR_MALFORMED_CRITICAL_EXTENSIONS, e);
        }
        if (!(parsed instanceof ArrayValue array)) {
            throw new PDPConfigurationException(ERROR_MALFORMED_CRITICAL_EXTENSIONS);
        }
        val names = new LinkedHashSet<String>();
        for (val element : array) {
            if (!(element instanceof TextValue(var name))) {
                throw new PDPConfigurationException(ERROR_MALFORMED_CRITICAL_EXTENSIONS);
            }
            names.add(name);
        }
        return names;
    }

    /**
     * Serializes a critical extension set to canonical {@code critical-extensions.json} content. Names are sorted so
     * the output is deterministic and the signed bytes are stable.
     *
     * @param criticalExtensionNames
     * the critical extension names
     *
     * @return the JSON array content
     */
    public static String toJson(Collection<String> criticalExtensionNames) {
        val array = ArrayValue.builder();
        for (val name : new TreeSet<>(criticalExtensionNames)) {
            array.add(Value.of(name));
        }
        return ValueJsonMarshaller.toJsonString(array.build());
    }

    /**
     * Enforces the structural integrity rules that make an extension set well-formed. Both rules fail closed with a
     * {@link PDPConfigurationException}.
     *
     * @param criticalExtensions
     * the declared critical extension names
     * @param extensionNames
     * the names with a cleartext configuration present
     * @param extensionSecretNames
     * the names with sealed secrets present
     *
     * @throws PDPConfigurationException
     * if a critical extension has no configuration, or sealed secrets have no configuration
     */
    public static void validateIntegrity(Set<String> criticalExtensions, Set<String> extensionNames,
            Set<String> extensionSecretNames) {
        for (val critical : criticalExtensions) {
            if (!extensionNames.contains(critical)) {
                throw new PDPConfigurationException(ERROR_CRITICAL_WITHOUT_CONFIG.formatted(critical, critical));
            }
        }
        for (val secret : extensionSecretNames) {
            if (!extensionNames.contains(secret)) {
                throw new PDPConfigurationException(ERROR_SECRETS_WITHOUT_CONFIG.formatted(secret, secret));
            }
        }
    }

}

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

import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Set;

/**
 * Shared conventions for the supplementary files carried alongside a PDP configuration. Extensions are named JSON
 * files that consumers other than the PDP read, for example to configure upstream connections. Secrets files carry
 * their sealing state in the filename: a {@code .sealed.json} name holds sealed content, a plain {@code .json} name
 * holds cleartext. All files are carried identically whether they sit loose in a policy directory or zipped in a
 * bundle, so both sources use these helpers to recognize the files and validate structural
 * integrity.
 * <p>
 * The files are:
 * <ul>
 * <li>{@code secrets.json} / {@code secrets.sealed.json}: PDP-level secrets, cleartext or sealed.</li>
 * <li>{@code ext-<name>.json}: cleartext extension configuration.</li>
 * <li>{@code ext-<name>-secrets.json} / {@code ext-<name>-secrets.sealed.json}: extension secrets, cleartext or
 * sealed.</li>
 * </ul>
 * An artifact never mixes cleartext and sealed secrets files: either every secrets file is sealed or none is.
 */
@UtilityClass
public class ExtensionFiles {

    /**
     * Filename prefix shared by all extension file kinds.
     */
    public static final String EXTENSION_PREFIX = "ext-";

    /**
     * Filename suffix of a cleartext extension configuration file.
     */
    public static final String EXTENSION_SUFFIX = ".json";

    /**
     * Filename suffix of a cleartext extension secrets file.
     */
    public static final String EXTENSION_SECRETS_SUFFIX = "-secrets.json";

    /**
     * Filename suffix of a sealed extension secrets file.
     */
    public static final String SEALED_EXTENSION_SECRETS_SUFFIX = "-secrets.sealed.json";

    /**
     * Name of the cleartext PDP-level secrets file.
     */
    public static final String SECRETS_FILE = "secrets.json";

    /**
     * Name of the sealed PDP-level secrets file.
     */
    public static final String SEALED_SECRETS_FILE = "secrets.sealed.json";

    private static final String ERROR_REQUIRED_WITHOUT_PAYLOAD = "Required extension '%1$s' has no configuration or secrets payload. Provide 'ext-%1$s.json', 'ext-%1$s-secrets.json' or 'ext-%1$s-secrets.sealed.json'.";

    /**
     * @param fileName
     * a root-level file name
     *
     * @return true if the name is a sealed extension secrets file
     */
    public static boolean isSealedExtensionSecretsFile(String fileName) {
        return fileName.startsWith(EXTENSION_PREFIX) && fileName.endsWith(SEALED_EXTENSION_SECRETS_SUFFIX);
    }

    /**
     * @param fileName
     * a root-level file name
     *
     * @return true if the name is a cleartext extension secrets file
     */
    public static boolean isExtensionSecretsFile(String fileName) {
        return fileName.startsWith(EXTENSION_PREFIX) && fileName.endsWith(EXTENSION_SECRETS_SUFFIX)
                && !isSealedExtensionSecretsFile(fileName);
    }

    /**
     * @param fileName
     * a root-level file name
     *
     * @return true if the name is a cleartext extension configuration file
     */
    public static boolean isExtensionFile(String fileName) {
        return fileName.startsWith(EXTENSION_PREFIX) && fileName.endsWith(EXTENSION_SUFFIX)
                && !isExtensionSecretsFile(fileName) && !isSealedExtensionSecretsFile(fileName);
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
     * a cleartext extension secrets file name
     *
     * @return the extension name with the prefix and secrets suffix stripped
     */
    public static String extensionSecretsNameOf(String fileName) {
        return fileName.substring(EXTENSION_PREFIX.length(), fileName.length() - EXTENSION_SECRETS_SUFFIX.length());
    }

    /**
     * @param fileName
     * a sealed extension secrets file name
     *
     * @return the extension name with the prefix and sealed secrets suffix stripped
     */
    public static String sealedExtensionSecretsNameOf(String fileName) {
        return fileName.substring(EXTENSION_PREFIX.length(),
                fileName.length() - SEALED_EXTENSION_SECRETS_SUFFIX.length());
    }

    /**
     * Enforces the structural integrity rule that makes an extension set well-formed: every declared required
     * extension must have a payload present, a cleartext configuration or sealed secrets or both. A required
     * extension declared without any payload would let the consumer believe it is honored while it silently runs on
     * nothing. The check fails closed with a {@link PDPConfigurationException}.
     *
     * @param requiredExtensions
     * the declared required extension names
     * @param extensionNames
     * the names with a cleartext configuration present
     * @param extensionSecretNames
     * the names with sealed secrets present
     *
     * @throws PDPConfigurationException
     * if a required extension has neither configuration nor secrets
     */
    public static void validateIntegrity(Set<String> requiredExtensions, Set<String> extensionNames,
            Set<String> extensionSecretNames) {
        for (val extensionName : requiredExtensions) {
            if (!extensionNames.contains(extensionName) && !extensionSecretNames.contains(extensionName)) {
                throw new PDPConfigurationException(ERROR_REQUIRED_WITHOUT_PAYLOAD.formatted(extensionName));
            }
        }
    }

}

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
package io.sapl.pdp.configuration.bundle;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.ExtensionFiles;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import lombok.val;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Intermediate representation of extracted bundle content.
 *
 * @param pdpJson the pdp.json content, or null if not present
 * @param secretsJson the secrets file content, or null if not present
 * @param secretsSealed whether the secrets came from the sealed-named file
 * @param saplDocuments map of SAPL document file names to content
 * @param extensions cleartext extension files keyed by extension name
 * @param extensionSecrets cleartext-named extension secrets keyed by extension
 * name
 * @param sealedExtensionSecrets sealed-named extension secrets keyed by
 * extension name
 * @param criticalExtensionsJson the critical-extensions.json content, or null if
 * not present
 * @param manifest the bundle manifest, or null when the archive carries none
 * (rejected fail-closed during conversion)
 */
record Bundle(
        String pdpJson,
        String secretsJson,
        boolean secretsSealed,
        Map<String, String> saplDocuments,
        Map<String, String> extensions,
        Map<String, String> extensionSecrets,
        Map<String, String> sealedExtensionSecrets,
        String criticalExtensionsJson,
        BundleManifest manifest) {
    private static final String ERROR_AUDIENCE_WITHOUT_SEALED_CONTENT = "Bundle manifest names an audience.sealingRecipient but the bundle carries no sealed content. The audience block is required exactly when sealed content is present.";
    private static final String ERROR_MANIFEST_REQUIRED = "Bundle has no .sapl-manifest.json. Since SAPL 4.2.0 the manifest carries the required configurationId; rebuild the bundle with BundleBuilder or 'sapl bundle create'.";
    private static final String ERROR_SEALED_CONTENT_REQUIRES_AUDIENCE = "Bundle carries sealed content but its manifest names no audience.sealingRecipient. Rebuild the bundle so the manifest records the sealing recipient key id.";
    private static final String ERROR_SEALING_RECIPIENT_NOT_HELD = "Bundle's sealed content is sealed to recipient key '%s', but the local deployment only holds sealing keys %s. The sealed content cannot be unsealed here.";
    private static final String ERROR_SECURITY_POLICY_REQUIRED = "Security policy is required. Use BundleSecurityPolicy.builder(publicKey).build() for production or explicitly disable verification with risk acceptance for development.";

    private static final String PDP_JSON = "pdp.json";

    PDPConfiguration toPDPConfiguration(String pdpId, BundleSecurityPolicy securityPolicy) {
        verifySignature(pdpId, securityPolicy);
        if (manifest == null) {
            throw new PDPConfigurationException(ERROR_MANIFEST_REQUIRED);
        }
        checkAudience(securityPolicy);
        val allSecretNames = new TreeSet<>(extensionSecrets.keySet());
        allSecretNames.addAll(sealedExtensionSecrets.keySet());
        val criticalExtensions = ExtensionFiles.parseCriticalExtensions(criticalExtensionsJson);
        ExtensionFiles.validateIntegrity(criticalExtensions, extensions.keySet(), allSecretNames);
        val configuration = PDPConfigurationLoader.loadFromBundle(pdpJson, secretsJson, saplDocuments, pdpId,
                manifest.configurationId());
        val allSecrets    = toValues(extensionSecrets);
        allSecrets.putAll(toValues(sealedExtensionSecrets));
        return configuration.withExtensions(toValues(extensions), allSecrets, criticalExtensions);
    }

    private void checkAudience(BundleSecurityPolicy securityPolicy) {
        val hasSealedContent = secretsSealed || !sealedExtensionSecrets.isEmpty();
        val audience         = manifest.audience();
        if (!hasSealedContent) {
            if (audience != null) {
                throw new PDPConfigurationException(ERROR_AUDIENCE_WITHOUT_SEALED_CONTENT);
            }
            return;
        }
        if (audience == null || audience.sealingRecipient() == null || audience.sealingRecipient().isBlank()) {
            throw new PDPConfigurationException(ERROR_SEALED_CONTENT_REQUIRES_AUDIENCE);
        }
        val heldKeyIds = securityPolicy.sealingKeyIds();
        if (!heldKeyIds.isEmpty() && !heldKeyIds.contains(audience.sealingRecipient())) {
            throw new PDPConfigurationException(
                    ERROR_SEALING_RECIPIENT_NOT_HELD.formatted(audience.sealingRecipient(), heldKeyIds));
        }
    }

    private static Map<String, Value> toValues(Map<String, String> jsonByName) {
        val values = new LinkedHashMap<String, Value>();
        for (val entry : jsonByName.entrySet()) {
            values.put(entry.getKey(), Value.ofJson(entry.getValue()));
        }
        return values;
    }

    private void verifySignature(String pdpId, BundleSecurityPolicy securityPolicy) {
        if (securityPolicy == null) {
            throw new BundleSignatureException(ERROR_SECURITY_POLICY_REQUIRED);
        }

        val isSigned = manifest != null && BundleSigner.isSigned(manifest);

        if (!isSigned) {
            securityPolicy.checkUnsignedBundleAllowed(pdpId);
            return;
        }

        if (!securityPolicy.signatureRequired()) {
            // Verification disabled, so accept the signed bundle without demanding a key.
            return;
        }

        val keyId                = manifest.signature().keyId();
        val publicKey            = securityPolicy.resolvePublicKey(pdpId, keyId);
        val filesForVerification = buildVerificationMap();

        BundleSigner.verify(manifest, filesForVerification, publicKey);
    }

    private Map<String, String> buildVerificationMap() {
        val files = new TreeMap<String, String>();

        if (pdpJson != null) {
            files.put(PDP_JSON, pdpJson);
        }

        files.putAll(saplDocuments);
        if (secretsJson != null) {
            files.put(secretsSealed ? ExtensionFiles.SEALED_SECRETS_FILE : ExtensionFiles.SECRETS_FILE, secretsJson);
        }
        for (val entry : extensions.entrySet()) {
            files.put(ExtensionFiles.EXTENSION_PREFIX + entry.getKey() + ExtensionFiles.EXTENSION_SUFFIX,
                    entry.getValue());
        }
        for (val entry : extensionSecrets.entrySet()) {
            files.put(ExtensionFiles.EXTENSION_PREFIX + entry.getKey() + ExtensionFiles.EXTENSION_SECRETS_SUFFIX,
                    entry.getValue());
        }
        for (val entry : sealedExtensionSecrets.entrySet()) {
            files.put(ExtensionFiles.EXTENSION_PREFIX + entry.getKey() + ExtensionFiles.SEALED_EXTENSION_SECRETS_SUFFIX,
                    entry.getValue());
        }
        if (criticalExtensionsJson != null) {
            files.put(ExtensionFiles.CRITICAL_EXTENSIONS_FILE, criticalExtensionsJson);
        }
        return files;
    }
}

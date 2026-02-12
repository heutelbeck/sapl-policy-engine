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

import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import lombok.val;

import java.util.Map;
import java.util.TreeMap;

/**
 * Intermediate representation of extracted bundle content.
 *
 * @param pdpJson the pdp.json content, or null if not present
 * @param saplDocuments map of SAPL document file names to content
 * @param manifest the bundle manifest, or null if bundle is unsigned
 */
record Bundle(String pdpJson, Map<String, String> saplDocuments, BundleManifest manifest) {
    private static final String ERROR_SECURITY_POLICY_REQUIRED = "Security policy is required. Use BundleSecurityPolicy.builder(publicKey).build() for production or explicitly disable verification with risk acceptance for development.";

    private static final String PDP_JSON = "pdp.json";

    PDPConfiguration toPDPConfiguration(String pdpId, BundleSecurityPolicy securityPolicy) {
        verifySignature(pdpId, securityPolicy);
        return PDPConfigurationLoader.loadFromBundle(pdpJson, saplDocuments, pdpId);
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
        return files;
    }
}

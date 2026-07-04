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
package io.sapl.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.secrets.SecretSealing;

import lombok.val;

@DisplayName("SecretsUnsealing")
class SecretsUnsealingTests {

    private static final String PDP_JSON_WITH_SECRETS = """
            { "configurationId": "test", "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" }, "secrets": { "http": { "api": { "headers": { "X-API-Key": "TOP-SECRET-VALUE" } } } } }
            """;

    @Test
    @DisplayName("a bundle's sealed secrets are restored with the recipient private key while structure is preserved")
    void whenUnsealingSealedConfigurationThenSecretsRestored() {
        val recipient = SecretSealing.generateRecipientKey();
        val bundle    = BundleBuilder.create().withPdpJson(PDP_JSON_WITH_SECRETS)
                .sealSecretsWith(recipient.toPublicJWK()).build();
        val sealed    = BundleParser.parse(bundle, "pdp",
                BundleSecurityPolicy.builder().disableSignatureVerification().build());
        assertThat(ValueJsonMarshaller.toJsonString(sealed.data().secrets())).contains("ENC[")
                .doesNotContain("TOP-SECRET-VALUE");

        val unsealed = SecretsUnsealing.process(recipient, false, sealed);
        assertThat(ValueJsonMarshaller.toJsonString(unsealed.data().secrets()))
                .contains("TOP-SECRET-VALUE", "http", "X-API-Key").doesNotContain("ENC[");
        assertThat(unsealed.configurationId()).isEqualTo(sealed.configurationId());
    }

    @Test
    @DisplayName("extension secrets are exposed sealed and unsealed with the recipient key, cleartext extensions pass through")
    void whenBundleHasExtensionsThenSecretsUnsealedAndCleartextPreserved() {
        val recipient = SecretSealing.generateRecipientKey();
        val bundle    = BundleBuilder.create().withPdpJson(PDP_JSON_WITH_SECRETS)
                .sealSecretsWith(recipient.toPublicJWK()).withExtension("paratron-gateway", """
                        { "route": "/api" }""").withExtensionSecrets("paratron-gateway", """
                        { "apiKey": "EXT-SECRET-VALUE" }""").build();
        val sealed    = BundleParser.parse(bundle, "pdp",
                BundleSecurityPolicy.builder().disableSignatureVerification().build());
        assertThat(sealed.extensions()).containsKey("paratron-gateway");
        assertThat(ValueJsonMarshaller.toJsonString(sealed.extensionSecrets().get("paratron-gateway"))).contains("ENC[")
                .doesNotContain("EXT-SECRET-VALUE");

        val unsealed = SecretsUnsealing.process(recipient, false, sealed);
        assertThat(ValueJsonMarshaller.toJsonString(unsealed.extensionSecrets().get("paratron-gateway")))
                .contains("EXT-SECRET-VALUE").doesNotContain("ENC[");
        assertThat(ValueJsonMarshaller.toJsonString(unsealed.extensions().get("paratron-gateway"))).contains("route");
    }
}

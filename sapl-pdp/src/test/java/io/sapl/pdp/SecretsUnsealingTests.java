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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.secrets.Keyring;
import io.sapl.secrets.SecretSealing;
import io.sapl.secrets.ValueSealer;

import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Set;

@DisplayName("SecretsUnsealing")
class SecretsUnsealingTests {

    private static final String PDP_JSON = """
            { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
            """;

    private static final String SECRETS_JSON = """
            { "http": { "api": { "headers": { "X-API-Key": "TOP-SECRET-VALUE" } } } }
            """;

    @Test
    @DisplayName("a bundle's sealed secrets are restored with the recipient private key while structure is preserved")
    void whenUnsealingSealedConfigurationThenSecretsRestored() {
        val recipient = SecretSealing.generateRecipientKey();
        val bundle    = BundleBuilder.create().withPdpJson(PDP_JSON).withSecrets(SECRETS_JSON)
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
        val bundle    = BundleBuilder.create().withPdpJson(PDP_JSON).withSecrets(SECRETS_JSON)
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

    @Test
    @DisplayName("a rotation keyring unseals PDP and extension secrets addressed to different accepted keys")
    void whenConfigurationUsesRotationKeysThenEachLeafIsRoutedByKeyId() {
        val previous         = SecretSealing.generateRecipientKey("previous");
        val current          = SecretSealing.generateRecipientKey("current");
        val sealedPdpSecrets = ObjectValue.builder()
                .put("old", ValueSealer.seal(previous.toPublicJWK(), Value.of("old-secret")))
                .put("new", ValueSealer.seal(current.toPublicJWK(), Value.of("new-secret"))).build();
        val sealedExtension  = ValueSealer.seal(current.toPublicJWK(), Value.of("extension-secret"));
        val configuration    = new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT,
                List.of(), new PdpData(Value.EMPTY_OBJECT, sealedPdpSecrets), Map.of(),
                Map.of("gateway", sealedExtension), Set.of());

        val unsealed = SecretsUnsealing.process(Keyring.of(current, previous), false, configuration);

        assertThat(unsealed.data().secrets()).containsEntry("old", Value.of("old-secret")).containsEntry("new",
                Value.of("new-secret"));
        assertThat(unsealed.extensionSecrets()).containsEntry("gateway", Value.of("extension-secret"));
    }
}

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

import java.util.LinkedHashMap;

import com.nimbusds.jose.jwk.OctetKeyPair;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import io.sapl.secrets.ValueSealer;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Decrypts the secrets a PDP receives, with the recipient's X25519 private key.
 * <p>
 * This is the recipient (the PDP, or the cluster that shares the key) decrypting
 * a configuration a source has already verified. Structure and keys are
 * untouched; each {@code ENC[...]} leaf of {@code pdp.json} secrets and of every
 * extension-secret is restored to its original scalar. Unless
 * {@code acceptUnencryptedSecrets} is set, a configuration whose secrets are not
 * sealed is refused, so plaintext secrets cannot slip in where sealing was
 * expected. Both the embedded {@code PolicyDecisionPointBuilder} and the Spring
 * server wire their configuration ingestion through here.
 */
@UtilityClass
public class SecretsUnsealing {

    private static final String ERROR_UNENCRYPTED_SECRETS = "Refusing a configuration whose secrets are not sealed. Seal the secrets, or enable acceptUnencryptedSecrets.";

    /**
     * Processes a configuration event on ingestion: for a {@code Load}, applies
     * {@link #process} to its configuration; other events pass through unchanged.
     *
     * @param recipientPrivateKey
     * the X25519 recipient private key
     * @param acceptUnencryptedSecrets
     * whether to accept unsealed secrets instead of refusing them
     * @param event
     * the configuration event
     *
     * @return the event with its configuration processed
     */
    public static ConfigurationEvent processEvent(OctetKeyPair recipientPrivateKey, boolean acceptUnencryptedSecrets,
            ConfigurationEvent event) {
        if (event instanceof ConfigurationEvent.NewConfiguration(var configuration)) {
            return new ConfigurationEvent.NewConfiguration(
                    process(recipientPrivateKey, acceptUnencryptedSecrets, configuration));
        }
        return event;
    }

    /**
     * Enforces the sealed-secrets policy and unseals the configuration.
     *
     * @param recipientPrivateKey
     * the X25519 recipient private key
     * @param acceptUnencryptedSecrets
     * whether to accept unsealed secrets instead of refusing them
     * @param configuration
     * the configuration to process
     *
     * @return the configuration with its secrets unsealed
     *
     * @throws PDPConfigurationException
     * if secrets are not sealed and unsealed secrets are not accepted
     */
    public static PDPConfiguration process(OctetKeyPair recipientPrivateKey, boolean acceptUnencryptedSecrets,
            PDPConfiguration configuration) {
        if (!acceptUnencryptedSecrets) {
            requireSealed(configuration);
        }
        return unseal(recipientPrivateKey, configuration);
    }

    private static PDPConfiguration unseal(OctetKeyPair recipientPrivateKey, PDPConfiguration configuration) {
        val data            = configuration.data();
        val unsealedSecrets = ValueSealer.unseal(recipientPrivateKey, data.secrets());
        val unsealedExtras  = new LinkedHashMap<String, Value>();
        for (val entry : configuration.extensionSecrets().entrySet()) {
            unsealedExtras.put(entry.getKey(), ValueSealer.unseal(recipientPrivateKey, entry.getValue()));
        }
        return new PDPConfiguration(configuration.pdpId(), configuration.configurationId(),
                configuration.combiningAlgorithm(), configuration.compilerOptions(), configuration.saplDocuments(),
                new PdpData(data.variables(), unsealedSecrets), configuration.extensions(), unsealedExtras,
                configuration.criticalExtensions());
    }

    private static void requireSealed(PDPConfiguration configuration) {
        if (!ValueSealer.hasSealedShape(configuration.data().secrets())) {
            throw new PDPConfigurationException(ERROR_UNENCRYPTED_SECRETS);
        }
        for (val secret : configuration.extensionSecrets().values()) {
            if (!ValueSealer.hasSealedShape(secret)) {
                throw new PDPConfigurationException(ERROR_UNENCRYPTED_SECRETS);
            }
        }
    }
}

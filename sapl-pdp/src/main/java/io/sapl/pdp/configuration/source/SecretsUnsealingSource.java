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
package io.sapl.pdp.configuration.source;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.nimbusds.jose.jwk.OctetKeyPair;

import io.sapl.pdp.SecretsUnsealing;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.secrets.SecretSealingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Wraps another {@link PDPConfigurationSource} and unseals the secrets of every
 * event it emits before passing it on. The wrapped source verifies and delivers
 * configurations exactly as before, still holding only public verification keys,
 * while this decorator holds the recipient's private key and decrypts each
 * configuration as it passes through. Everything downstream, the compiler
 * included, therefore only ever sees cleartext, and the private key stays out of
 * the wire-facing source.
 * <p>
 * This is the single place a configuration is unsealed on the streaming path, so
 * the recipient key is applied in exactly one location. The constructor takes the
 * delegate source, the X25519 recipient private key that unseals the secrets, and
 * whether a configuration carrying secrets in cleartext is accepted rather than
 * rejected.
 * <p>
 * An unseal failure is caught so it can never crash the delegate's monitoring
 * thread, logged without ever revealing secret material, and turned into a
 * {@link ConfigurationEvent.ConfigurationError} so the definitively broken
 * configuration is reported rather than silently dropped. The catch is deliberately
 * broad so that no unseal fault, expected or not, escapes onto that thread.
 */
@Slf4j
@RequiredArgsConstructor
public final class SecretsUnsealingSource implements PDPConfigurationSource {

    private static final String WARN_UNSEAL_REJECTED = "Rejected a configuration for pdpId '{}' (configurationId '{}') because {}. The previously loaded configuration for this pdpId, if any, remains active.";

    private final PDPConfigurationSource delegate;
    private final OctetKeyPair           recipientPrivateKey;
    private final boolean                acceptUnencryptedSecrets;

    private final Map<Consumer<ConfigurationEvent>, Consumer<ConfigurationEvent>> wrappers = new ConcurrentHashMap<>();

    @Override
    public void subscribe(Consumer<ConfigurationEvent> listener) {
        val wrapper = wrappers.computeIfAbsent(listener, original -> event -> original.accept(unseal(event)));
        delegate.subscribe(wrapper);
    }

    @Override
    public void unsubscribe(Consumer<ConfigurationEvent> listener) {
        val wrapper = wrappers.remove(listener);
        if (wrapper != null) {
            delegate.unsubscribe(wrapper);
        }
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    private ConfigurationEvent unseal(ConfigurationEvent event) {
        try {
            return SecretsUnsealing.processEvent(recipientPrivateKey, acceptUnencryptedSecrets, event);
        } catch (RuntimeException failure) {
            // Immortal error path: an unseal failure must never crash the delegate's
            // monitoring thread. Only a NewConfiguration can fail to unseal (other events
            // pass through untouched). Report the definitively broken configuration as an
            // error event rather than dropping it, without revealing any secret material.
            if (!(event instanceof ConfigurationEvent.NewConfiguration(var configuration))) {
                return event;
            }
            val reason = reasonWithoutSecrets(failure);
            log.warn(WARN_UNSEAL_REJECTED, configuration.pdpId(), configuration.configurationId(), reason);
            return new ConfigurationEvent.ConfigurationError(configuration.pdpId(), reason);
        }
    }

    /**
     * A human-readable cause that names the failure category but never the secret
     * content, the ciphertext, or the key. Only the exception type steers the
     * wording, so no attacker-influenced or secret-bearing text is logged.
     */
    private static String reasonWithoutSecrets(RuntimeException failure) {
        if (failure instanceof PDPConfigurationException) {
            return "its secrets are not sealed and unsealed secrets are not accepted";
        }
        if (failure instanceof SecretSealingException) {
            return "its secrets could not be decrypted with the configured recipient key (wrong key or tampered data)";
        }
        return "of an unexpected error while unsealing (" + failure.getClass().getSimpleName() + ")";
    }
}

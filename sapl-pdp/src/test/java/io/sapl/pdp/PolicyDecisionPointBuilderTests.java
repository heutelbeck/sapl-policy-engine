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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import io.sapl.secrets.SecretSealing;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PolicyDecisionPointBuilder")
class PolicyDecisionPointBuilderTests {

    @Test
    @DisplayName("build fails fast with a clear error when the plugins source never delivers a bundle")
    void whenPluginsSourceNeverDeliversThenBuildFailsClosed() {
        val nonDeliveringSource = new PluginsSource() {
                                    @Override
                                    public void subscribe(Consumer<PluginsBundle> listener) {
                                        // Violates deliver-on-subscribe, leaving the voter source without a plugins
                                        // bundle.
                                    }

                                    @Override
                                    public void unsubscribe(Consumer<PluginsBundle> listener) {
                                        // no-op
                                    }

                                    @Override
                                    public void close() {
                                        // no-op
                                    }

                                    @Override
                                    public boolean isClosed() {
                                        return false;
                                    }
                                };
        val builder             = PolicyDecisionPointBuilder.withDefaults().withPluginsSource(nonDeliveringSource);
        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no plugins bundle is available");
    }

    @Test
    @DisplayName("a builder-created plugins source is closed by PDPComponents.close()")
    void whenBuilderOwnedPluginsSourceThenClosedOnClose() {
        val components    = PolicyDecisionPointBuilder.withDefaults().build();
        val pluginsSource = components.pluginsSource();

        assertThat(pluginsSource).isNotNull();
        assertThat(pluginsSource.isClosed()).isFalse();

        components.close();

        assertThat(pluginsSource.isClosed()).isTrue();
    }

    @Test
    @DisplayName("a builder-created default repository is closed by PDPComponents.close() so its scheduler does not leak")
    void whenBuilderOwnedRepositoryThenClosedOnClose() throws Exception {
        val components = PolicyDecisionPointBuilder.withDefaults().build();
        val repository = components.ownedRepository();

        assertThat(repository).isNotNull();
        assertThat(repositoryClosed(repository)).isFalse();

        components.close();

        assertThat(repositoryClosed(repository)).isTrue();
    }

    @Test
    @DisplayName("a caller-supplied repository is not owned and is left open by PDPComponents.close()")
    void whenExternalRepositoryThenNotOwnedAndLeftOpen() throws Exception {
        val external   = new InMemoryAttributeRepository();
        val components = PolicyDecisionPointBuilder.withDefaults().withRepository(external).build();

        assertThat(components.ownedRepository()).isNull();

        components.close();

        assertThat(repositoryClosed(external)).isFalse();
        external.close();
    }

    private static boolean repositoryClosed(AttributeRepository repository) throws Exception {
        val field = repository.getClass().getDeclaredField("closed");
        field.setAccessible(true);
        return (boolean) field.get(repository);
    }

    @Test
    @DisplayName("a failed build closes the activated configuration source so its background threads do not leak")
    void whenBuildFailsThenConfigurationSourceIsClosed() {
        val configurationSource  = new TrackingConfigurationSource();
        val nonDeliveringPlugins = new PluginsSource() {
                                     @Override
                                     public void subscribe(Consumer<PluginsBundle> listener) {
                                         // Never delivers, so build() fails.
                                     }

                                     @Override
                                     public void unsubscribe(Consumer<PluginsBundle> listener) {
                                         // no-op
                                     }

                                     @Override
                                     public void close() {
                                         // no-op
                                     }

                                     @Override
                                     public boolean isClosed() {
                                         return false;
                                     }
                                 };
        val builder              = PolicyDecisionPointBuilder.withDefaults()
                .withConfigurationSource(configurationSource).withPluginsSource(nonDeliveringPlugins);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
        assertThat(configurationSource.isClosed()).isTrue();
    }

    @Test
    @DisplayName("a valid X25519 private secrets decryption key is accepted")
    void whenValidSecretsDecryptionKeyThenAccepted() {
        val key     = SecretSealing.generateRecipientKey();
        val builder = PolicyDecisionPointBuilder.withDefaults().withSecretsDecryptionKey(key);
        assertThat(builder).isNotNull();
    }

    @MethodSource("invalidDecryptionKeys")
    @ParameterizedTest(name = "{0}")
    @DisplayName("an invalid secrets decryption key is rejected")
    void whenInvalidSecretsDecryptionKeyThenThrows(String description, OctetKeyPair key, String messageFragment) {
        val builder = PolicyDecisionPointBuilder.withDefaults();
        assertThatThrownBy(() -> builder.withSecretsDecryptionKey(key)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(messageFragment);
    }

    static Stream<Arguments> invalidDecryptionKeys() throws JOSEException {
        val ed25519    = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        val publicOnly = SecretSealing.generateRecipientKey().toPublicJWK();
        return Stream.of(arguments("null", null, "must not be null"), arguments("non-X25519 curve", ed25519, "X25519"),
                arguments("public key only", publicOnly, "private"));
    }

    @Test
    @DisplayName("a decryption key rejects a configuration whose secrets are unencrypted by default")
    void whenKeyAndUnencryptedSecretsThenBuildFails() {
        val key     = SecretSealing.generateRecipientKey();
        val builder = PolicyDecisionPointBuilder.withDefaults().withConfiguration(configWithPlaintextSecret())
                .withSecretsDecryptionKey(key);
        assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("not sealed");
    }

    @Test
    @DisplayName("an initial configuration that fails to compile fails the build fast")
    void whenInitialConfigurationBrokenThenBuildFails() {
        val builder = PolicyDecisionPointBuilder.withDefaults().withPolicy("this is not valid sapl");
        assertThatThrownBy(builder::build).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("failed to compile");
    }

    @Test
    @DisplayName("acceptUnencryptedSecrets allows a configuration whose secrets are unencrypted")
    void whenAcceptUnencryptedThenBuildSucceeds() {
        val key        = SecretSealing.generateRecipientKey();
        val components = PolicyDecisionPointBuilder.withDefaults().withConfiguration(configWithPlaintextSecret())
                .withSecretsDecryptionKey(key).acceptUnencryptedSecrets().build();
        assertThat(components).isNotNull();
        components.close();
    }

    private static PDPConfiguration configWithPlaintextSecret() {
        val secrets = ObjectValue.builder().put("apiKey", Value.of("PLAINTEXT")).build();
        return new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, List.of(),
                new PdpData(Value.EMPTY_OBJECT, secrets));
    }

    private static final class TrackingConfigurationSource implements PDPConfigurationSource {

        private boolean closed;

        @Override
        public void subscribe(Consumer<ConfigurationEvent> listener) {
            // Activated but emits nothing.
        }

        @Override
        public void unsubscribe(Consumer<ConfigurationEvent> listener) {
            // no-op
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}

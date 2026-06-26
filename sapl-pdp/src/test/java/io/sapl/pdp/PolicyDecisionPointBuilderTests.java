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

import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

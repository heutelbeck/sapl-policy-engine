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

import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

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
}

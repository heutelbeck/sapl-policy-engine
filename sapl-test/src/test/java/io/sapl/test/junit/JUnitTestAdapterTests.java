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
package io.sapl.test.junit;

import io.sapl.api.pdp.CombiningAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JUnitTestAdapter tests")
class JUnitTestAdapterTests {

    @Test
    @DisplayName("creates non-null adapter")
    void whenCreatingAdapter_thenNotNull() {
        var adapter = new JUnitTestAdapter();
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("returns empty fixture registrations by default")
    void whenGettingFixtureRegistrations_thenReturnsEmptyMapByDefault() {
        var adapter = new JUnitTestAdapter();
        assertThat(adapter.getFixtureRegistrations()).isEmpty();
    }

    @Test
    @DisplayName("returns DENY_OVERRIDES as default combining algorithm")
    void whenGettingDefaultCombiningAlgorithm_thenReturnsDenyOverrides() {
        var adapter = new JUnitTestAdapter();
        assertThat(adapter.getDefaultCombiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_OVERRIDES);
    }

    @Test
    @DisplayName("returns default policy directories")
    void whenGettingPolicyDirectories_thenReturnsDefaultDirectories() {
        var adapter = new JUnitTestAdapter();
        assertThat(adapter.getPolicyDirectories()).containsExactly("policies", "policiesIT");
    }

    @Nested
    @DisplayName("custom adapter tests")
    class CustomAdapterTests {

        @Test
        @DisplayName("subclass can override combining algorithm")
        void whenSubclassOverridesCombiningAlgorithm_thenReturnsCustomValue() {
            var adapter = new JUnitTestAdapter() {
                @Override
                protected CombiningAlgorithm getDefaultCombiningAlgorithm() {
                    return CombiningAlgorithm.PERMIT_OVERRIDES;
                }
            };

            assertThat(adapter.getDefaultCombiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        }

        @Test
        @DisplayName("subclass can override policy directories")
        void whenSubclassOverridesPolicyDirectories_thenReturnsCustomDirectories() {
            var adapter = new JUnitTestAdapter() {
                @Override
                protected List<String> getPolicyDirectories() {
                    return List.of("custom-policies", "integration-policies");
                }
            };

            assertThat(adapter.getPolicyDirectories()).containsExactly("custom-policies", "integration-policies");
        }

        @Test
        @DisplayName("subclass can provide fixture registrations")
        void whenSubclassProvidesFixtureRegistrations_thenReturnsCustomRegistrations() {
            var adapter = new JUnitTestAdapter() {
                @Override
                protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                    return Map.of(ImportType.STATIC_FUNCTION_LIBRARY, Map.of("testLib", Object.class));
                }
            };

            assertThat(adapter.getFixtureRegistrations()).containsKey(ImportType.STATIC_FUNCTION_LIBRARY)
                    .extractingByKey(ImportType.STATIC_FUNCTION_LIBRARY)
                    .satisfies(libs -> assertThat(libs).containsEntry("testLib", Object.class));
        }

        @Test
        @DisplayName("subclass can provide PIP registrations")
        void whenSubclassProvidesPipRegistrations_thenReturnsCustomRegistrations() {
            var pipInstance = new Object();
            var adapter     = new JUnitTestAdapter() {
                                @Override
                                protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                    return Map.of(ImportType.PIP, Map.of("testPip", pipInstance));
                                }
                            };

            assertThat(adapter.getFixtureRegistrations()).containsKey(ImportType.PIP).extractingByKey(ImportType.PIP)
                    .satisfies(pips -> assertThat(pips).containsEntry("testPip", pipInstance));
        }
    }
}

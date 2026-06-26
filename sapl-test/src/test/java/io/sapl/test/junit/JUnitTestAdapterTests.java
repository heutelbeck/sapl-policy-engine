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

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;

import io.sapl.api.pdp.configuration.CombiningAlgorithm;
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
    @DisplayName("returns priority deny or abstain errors propagate as default combining algorithm")
    void whenGettingDefaultCombiningAlgorithm_thenReturnsPriorityDenyAbstainPropagate() {
        var adapter = new JUnitTestAdapter();
        assertThat(adapter.getDefaultCombiningAlgorithm())
                .isEqualTo(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE));
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
                    return new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE);
                }
            };

            assertThat(adapter.getDefaultCombiningAlgorithm())
                    .isEqualTo(new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE));
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

    @Nested
    @DisplayName("fixture registration wiring tests")
    class FixtureRegistrationWiringTests {

        @Test
        @DisplayName("instance function library registrations reach the test configuration")
        void whenRegisteringInstanceFunctionLibrary_thenLibraryReachesConfiguration() {
            var library = new Object();
            var adapter = new JUnitTestAdapter() {
                            @Override
                            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                return Map.of(ImportType.FUNCTION_LIBRARY, Map.of("instanceLib", library));
                            }
                        };

            assertThat(adapter.createConfiguration().functionLibraries()).containsExactly(library);
        }

        @Test
        @DisplayName("static function library registrations reach the test configuration")
        void whenRegisteringStaticFunctionLibrary_thenLibraryReachesConfiguration() {
            var library = new Object();
            var adapter = new JUnitTestAdapter() {
                            @Override
                            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                return Map.of(ImportType.STATIC_FUNCTION_LIBRARY, Map.of("staticLib", library));
                            }
                        };

            assertThat(adapter.createConfiguration().functionLibraries()).containsExactly(library);
        }

        @Test
        @DisplayName("instance PIP registrations reach the test configuration")
        void whenRegisteringInstancePip_thenPipReachesConfiguration() {
            var pip     = new Object();
            var adapter = new JUnitTestAdapter() {
                            @Override
                            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                return Map.of(ImportType.PIP, Map.of("instancePip", pip));
                            }
                        };

            assertThat(adapter.createConfiguration().policyInformationPoints()).containsExactly(pip);
        }

        @Test
        @DisplayName("static PIP registrations reach the test configuration")
        void whenRegisteringStaticPip_thenPipReachesConfiguration() {
            var pip     = new Object();
            var adapter = new JUnitTestAdapter() {
                            @Override
                            protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                return Map.of(ImportType.STATIC_PIP, Map.of("staticPip", pip));
                            }
                        };

            assertThat(adapter.createConfiguration().policyInformationPoints()).containsExactly(pip);
        }

        @Test
        @DisplayName("all four import types are wired into the test configuration together")
        void whenRegisteringAllImportTypes_thenAllReachConfiguration() {
            var instanceLib = new Object();
            var staticLib   = new Object();
            var instancePip = new Object();
            var staticPip   = new Object();
            var adapter     = new JUnitTestAdapter() {
                                @Override
                                protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
                                    return Map.of(ImportType.FUNCTION_LIBRARY, Map.of("instanceLib", instanceLib),
                                            ImportType.STATIC_FUNCTION_LIBRARY, Map.of("staticLib", staticLib),
                                            ImportType.PIP, Map.of("instancePip", instancePip), ImportType.STATIC_PIP,
                                            Map.of("staticPip", staticPip));
                                }
                            };

            var configuration = adapter.createConfiguration();
            assertThat(configuration.functionLibraries()).containsExactlyInAnyOrder(instanceLib, staticLib);
            assertThat(configuration.policyInformationPoints()).containsExactlyInAnyOrder(instancePip, staticPip);
        }
    }
}

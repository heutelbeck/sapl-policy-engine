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
package io.sapl.pdp.plugins;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.configuration.EmptyExtensionsProcessor;
import io.sapl.pdp.configuration.PdpState;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.NonNull;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Wires the real attribute broker, voter source and decision point to a
 * snapshot of the plugin engine, so the plugins of each PDP are checked end to
 * end without plugin JARs or a directory watcher.
 */
@DisplayName("Plugins are scoped to the PDPs that enable them")
class TenantScopedPluginsTests {

    private static final String FIRST_PLUGIN  = "first-plugin";
    private static final String SECOND_PLUGIN = "second-plugin";
    private static final String ENABLING_PDP  = "enabling-pdp";
    private static final String OTHER_PDP     = "other-pdp";

    private static final String PLAIN_POLICY     = "policy \"plain\" permit";
    private static final String FUNCTION_POLICY  = "policy \"function\" permit first.value() == \"first\";";
    private static final String ATTRIBUTE_POLICY = "policy \"attribute\" permit <firstpip.value> == \"first-pip\";";

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    private final DecisionInterceptor firstInterceptor = mock(DecisionInterceptor.class);

    private PolicyInformationPointAttributeBroker broker;
    private PdpVoterSource                        voterSource;
    private BlockingPolicyDecisionPoint           pdp;

    @FunctionLibrary(name = "first", description = "Function library of the first test plugin")
    public static class FirstLibrary {
        @Function(docs = "Returns a constant")
        public static Value value() {
            return Value.of("first");
        }
    }

    @PolicyInformationPoint(name = "firstpip")
    public static class FirstPip {
        @EnvironmentAttribute
        public static Value value() {
            return Value.of("first-pip");
        }
    }

    /** Provides the same attribute as {@link FirstPip}. */
    @PolicyInformationPoint(name = "firstpip")
    public static class CollidingPip {
        @EnvironmentAttribute
        public static Value value() {
            return Value.of("colliding-pip");
        }
    }

    /** Publishes one fixed snapshot, as the plugin engine does after a reload. */
    private record FixedPluginsSource(PluginsSnapshot snapshot) implements PluginsSource {
        @Override
        public void subscribe(@NonNull Consumer<PluginsSnapshot> listener) {
            listener.accept(snapshot);
        }

        @Override
        public void unsubscribe(@NonNull Consumer<PluginsSnapshot> listener) {
            // nothing to release
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    @BeforeEach
    void setUp() {
        broker = new PolicyInformationPointAttributeBroker();
        broker.load(new FirstPip(), FIRST_PLUGIN);

        val plugins  = Map.of(FIRST_PLUGIN,
                new LoadedPlugin(FIRST_PLUGIN, List.of(new FirstLibrary()), List.of(new FirstPip()),
                        List.of(firstInterceptor)),
                SECOND_PLUGIN, new LoadedPlugin(SECOND_PLUGIN, List.of(), List.of(new CollidingPip()),
                        List.<DecisionInterceptor>of()));
        val snapshot = new PluginEngineSnapshot(plugins, List.of(), false, 0, List.of(), List.of());

        voterSource = new PdpVoterSource(new FixedPluginsSource(snapshot), Clock.systemUTC(),
                new EmptyExtensionsProcessor(), broker);
        pdp         = new BlockingPolicyDecisionPoint(voterSource, broker, new ThreadLocalRandomIdFactory());
    }

    @AfterEach
    void tearDown() {
        voterSource.close();
        broker.close();
    }

    private void load(String pdpId, String policy, Set<String> plugins) {
        voterSource.loadConfiguration(new PDPConfiguration(pdpId, pdpId + "-config", CombiningAlgorithm.DEFAULT,
                List.of(policy), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT)).withPlugins(plugins));
    }

    private void assertRejectedForUnknownFunction(String pdpId) {
        assertThat(voterSource.getPdpStatus(pdpId)).hasValueSatisfying(status -> {
            assertThat(status.state()).isEqualTo(PdpState.ERROR);
            assertThat(status.lastError()).contains("Unknown function 'first.value'");
        });
    }

    private Decision decide(String pdpId) {
        return pdp.decideOnce(SUBSCRIPTION, pdpId).decision();
    }

    @Nested
    @DisplayName("function libraries")
    class FunctionLibraries {

        @Test
        @DisplayName("a plugin function is available only to the PDP that enables the plugin")
        void whenPluginEnabledForOnePdpThenItsFunctionIsAvailableOnlyThere() {
            load(ENABLING_PDP, FUNCTION_POLICY, Set.of(FIRST_PLUGIN));
            load(OTHER_PDP, FUNCTION_POLICY, Set.of());

            assertThat(decide(ENABLING_PDP)).isEqualTo(Decision.PERMIT);
            assertRejectedForUnknownFunction(OTHER_PDP);
        }

        @Test
        @DisplayName("enabling a plugin in an updated configuration makes its function available")
        void whenPluginEnabledByUpdateThenItsFunctionBecomesAvailable() {
            load(ENABLING_PDP, FUNCTION_POLICY, Set.of());
            assertRejectedForUnknownFunction(ENABLING_PDP);

            load(ENABLING_PDP, FUNCTION_POLICY, Set.of(FIRST_PLUGIN));

            assertThat(decide(ENABLING_PDP)).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("policy information points")
    class PolicyInformationPoints {

        @Test
        @DisplayName("a plugin attribute is visible only to the PDP that enables the plugin")
        void whenPluginEnabledForOnePdpThenItsAttributeIsVisibleOnlyThere() {
            load(ENABLING_PDP, ATTRIBUTE_POLICY, Set.of(FIRST_PLUGIN));
            load(OTHER_PDP, ATTRIBUTE_POLICY, Set.of());

            assertThat(decide(ENABLING_PDP)).isEqualTo(Decision.PERMIT);
            assertThat(decide(OTHER_PDP)).isNotEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("enabling two plugins with a colliding attribute invalidates only that PDP")
        void whenCollidingPluginsEnabledThenOnlyThatPdpIsInvalid() {
            broker.load(new CollidingPip(), SECOND_PLUGIN);
            load(ENABLING_PDP, ATTRIBUTE_POLICY, Set.of(FIRST_PLUGIN));
            load(OTHER_PDP, ATTRIBUTE_POLICY, Set.of(FIRST_PLUGIN, SECOND_PLUGIN));

            assertThat(voterSource.getPdpStatus(OTHER_PDP)).hasValueSatisfying(status -> {
                assertThat(status.state()).isEqualTo(PdpState.ERROR);
                assertThat(status.lastError()).contains(FIRST_PLUGIN, SECOND_PLUGIN);
            });
            assertThat(decide(ENABLING_PDP)).isEqualTo(Decision.PERMIT);
        }
    }

    @Nested
    @DisplayName("decision interceptors")
    class DecisionInterceptors {

        @Test
        @DisplayName("a plugin interceptor sees no decisions of a PDP that does not enable the plugin")
        void whenPluginNotEnabledThenItsInterceptorSeesNoDecisions() {
            load(OTHER_PDP, PLAIN_POLICY, Set.of());

            decide(OTHER_PDP);

            verify(firstInterceptor, never()).onDecision(any(), any(), anyString(), any());
        }

        @Test
        @DisplayName("a plugin interceptor sees the decisions of a PDP that enables the plugin")
        void whenPluginEnabledThenItsInterceptorSeesDecisions() {
            load(ENABLING_PDP, PLAIN_POLICY, Set.of(FIRST_PLUGIN));

            decide(ENABLING_PDP);

            verify(firstInterceptor).onDecision(any(), any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("configuration without plugins")
    class WithoutPlugins {

        @Test
        @DisplayName("a configuration that lists no plugins gets none of them")
        void whenNoPluginsListedThenNoPluginContributesAnything() {
            load(OTHER_PDP, PLAIN_POLICY, Set.of());

            assertThat(voterSource.getPdpStatus(OTHER_PDP))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.LOADED));
            verify(firstInterceptor, never()).onDecision(any(), any(), anyString(), any());
        }

        @Test
        @DisplayName("listing a plugin that is not available invalidates the configuration")
        void whenUnavailablePluginListedThenConfigurationIsInvalid() {
            load(OTHER_PDP, PLAIN_POLICY, Set.of("missing-plugin"));

            assertThat(voterSource.getPdpStatus(OTHER_PDP)).hasValueSatisfying(status -> {
                assertThat(status.state()).isEqualTo(PdpState.ERROR);
                assertThat(status.lastError()).contains("missing-plugin");
            });
        }
    }
}

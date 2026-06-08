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
package io.sapl.pdp.configuration;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.StandardFunctionLibrary;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdpVoterSource dynamic recompile")
class PdpVoterSourceDynamicRecompileTests {

    private static final Clock   CLOCK    = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final PdpData EMPTY    = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    private static final String  PDP_ID   = "test-pdp";
    private static final String  CONFIG_A = "config-A";

    private static DefaultFunctionBroker brokerWithStandard() {
        var b = new DefaultFunctionBroker();
        b.load(new StandardFunctionLibrary());
        return b;
    }

    private static PluginsBundle pluginsOf(DefaultFunctionBroker broker) {
        return new PluginsBundle(broker, List.of(), List.of());
    }

    private static PDPConfiguration policyConfig(String configId) {
        return new PDPConfiguration(PDP_ID, configId, CombiningAlgorithm.DEFAULT, List.of("policy \"p\" permit"),
                EMPTY);
    }

    @Test
    @DisplayName("plugins push recompiles every retained configuration with the new broker")
    void whenPluginsPushedThenAllRetainedConfigurationsRecompileWithNewBroker() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val before = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();

            val newBroker = brokerWithStandard();
            pluginsSource.publish(pluginsOf(newBroker));

            val after = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();
            assertThat(after.plugins().functionBroker()).isSameAs(newBroker);
            assertThat(after).isNotSameAs(before);
        }
    }

    @Test
    @DisplayName("plugins push with new interceptors recompiles retained configurations atomically")
    void whenInterceptorsChangeThenRetainedConfigurationsCarryNewInterceptors() {
        val broker        = brokerWithStandard();
        val pluginsSource = new MutablePluginsSource(pluginsOf(broker));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val before = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();
            assertThat(before.plugins().decisionInterceptors()).isEmpty();

            val interceptor   = new RecordingDecisionInterceptor();
            val updatedBundle = new PluginsBundle(broker, List.of(interceptor), List.of());
            pluginsSource.publish(updatedBundle);

            val after = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();
            assertThat(after.plugins().decisionInterceptors()).containsExactly(interceptor);
            assertThat(after.plugins().functionBroker()).isSameAs(broker);
            assertThat(after).isNotSameAs(before);
        }
    }

    @Test
    @DisplayName("atomic plugins update changes broker and interceptors as one snapshot")
    void whenPluginsPushedThenBrokerAndInterceptorsApplyTogether() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);

            val newBroker      = brokerWithStandard();
            val newInterceptor = new RecordingDecisionInterceptor();
            val atomic         = new PluginsBundle(newBroker, List.of(newInterceptor), List.of());
            pluginsSource.publish(atomic);

            val after = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();
            assertThat(after.plugins().functionBroker()).isSameAs(newBroker);
            assertThat(after.plugins().decisionInterceptors()).containsExactly(newInterceptor);
        }
    }

    @Test
    @DisplayName("compiled artifact carries the plugins bundle that produced it")
    void whenLoadConfigurationThenCompiledPdpCarriesPlugins() {
        val broker        = brokerWithStandard();
        val bundle        = new PluginsBundle(broker, List.of(new RecordingDecisionInterceptor()), List.of());
        val pluginsSource = new MutablePluginsSource(bundle);
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val compiled = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();

            assertThat(compiled.plugins()).isEqualTo(bundle);
        }
    }

    @Test
    @DisplayName("loadConfiguration defers compilation and exposes AWAITING_PLUGINS until plugins arrive")
    void whenPluginsNotReadyThenLoadConfigurationDefersAndExposesAwaitingPluginsStatus() {
        val pluginsSource = new MutablePluginsSource();
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);

            assertThat(voterSource.getCurrentConfiguration(PDP_ID)).isEmpty();
            assertThat(voterSource.getPdpStatus(PDP_ID)).hasValueSatisfying(status -> {
                assertThat(status.state()).isEqualTo(PdpState.AWAITING_PLUGINS);
                assertThat(status.configurationId()).isEqualTo(CONFIG_A);
                assertThat(status.documentCount()).isEqualTo(1);
                assertThat(status.lastSuccessfulLoad()).isNull();
                assertThat(status.lastFailedLoad()).isNull();
                assertThat(status.lastError()).isNull();
            });

            pluginsSource.publish(pluginsOf(brokerWithStandard()));

            assertThat(voterSource.getCurrentConfiguration(PDP_ID)).isPresent();
            assertThat(voterSource.getPdpStatus(PDP_ID))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.LOADED));
        }
    }

    @Test
    @DisplayName("subscribe lifecycle listeners come from the bundle currently in effect")
    void whenLifecycleListenersChangeThenGetPluginsReturnsLatest() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val listener = new RecordingLifecycleListener();
            pluginsSource.publish(new PluginsBundle(brokerWithStandard(), List.of(), List.of(listener)));

            assertThat(voterSource.getPlugins().lifecycleListeners()).containsExactly(listener);
        }
    }

    static final class RecordingDecisionInterceptor implements DecisionInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void onDecision(TracedDecision decision, Instant timestamp, String subscriptionId,
                AuthorizationSubscription authorizationSubscription) {
            calls.incrementAndGet();
        }
    }

    static final class RecordingLifecycleListener implements SubscriptionLifecycleListener {
        final AtomicInteger subscribes   = new AtomicInteger();
        final AtomicInteger unsubscribes = new AtomicInteger();

        @Override
        public void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription,
                String pdpId) {
            subscribes.incrementAndGet();
        }

        @Override
        public void onUnsubscribe(String subscriptionId) {
            unsubscribes.incrementAndGet();
        }
    }
}

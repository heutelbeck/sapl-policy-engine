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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static PDPConfiguration brokenConfig() {
        return new PDPConfiguration(PDP_ID, "config-broken", CombiningAlgorithm.DEFAULT,
                List.of("this is not valid sapl"), EMPTY);
    }

    @Test
    @DisplayName("a fail-fast rejected configuration is not retained for later recompiles")
    void whenLoadFailsFastThenRejectedConfigurationIsNotRetained() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);

            val rejectedPdpId = "rejected-pdp";
            val badConfig     = new PDPConfiguration(rejectedPdpId, "config-bad", CombiningAlgorithm.DEFAULT,
                    List.of("this is not valid sapl"), EMPTY);
            assertThatThrownBy(() -> voterSource.loadConfiguration(badConfig, false))
                    .isInstanceOf(PDPConfigurationException.class);

            // A plugins push recompiles every retained configuration, so a rejected config
            // must not be retained.
            pluginsSource.publish(pluginsOf(brokerWithStandard()));

            assertThat(voterSource.getCurrentConfiguration(rejectedPdpId)).isEmpty();
        }
    }

    @Test
    @DisplayName("subscribing delivers the current configuration to the listener synchronously")
    void whenSubscribingThenCurrentConfigurationDeliveredImmediately() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val current = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();

            // State must be delivered under the source lock at subscription time, else a
            // concurrent update latches a stale configuration.
            val received = new ArrayList<PdpUpdateEvent>();
            voterSource.subscribeToUpdates(PDP_ID, received::add);

            assertThat(received).singleElement().isEqualTo(new PdpUpdateEvent.Voter(PDP_ID, current));
        }
    }

    @Test
    @DisplayName("a configuration removal then re-load keeps the live listener, the PDP never closes a subscription")
    void whenConfigurationRemovedThenReloadedThenTheSameListenerIsNotifiedAgain() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);

            val received = new ArrayList<PdpUpdateEvent>();
            voterSource.subscribeToUpdates(PDP_ID, received::add);
            voterSource.removeConfigurationForPdp(PDP_ID);
            voterSource.loadConfiguration(policyConfig("config-B"), false);

            // The PDP never closes a client subscription server-side.
            // Removal emits Removed and the consumer sees INDETERMINATE.
            // A later load re-notifies the same still-registered listener,
            // with no eviction and no client re-subscribe.
            assertThat(received).hasAtLeastOneElementOfType(PdpUpdateEvent.Removed.class).last()
                    .isInstanceOf(PdpUpdateEvent.Voter.class);
        }
    }

    @Test
    @DisplayName("the last subscriber leaving prunes the pdpId listener entry so transient pdpIds do not leak")
    void whenLastListenerUnsubscribesThenTheListenerMapEntryIsPruned() throws Exception {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            final Consumer<PdpUpdateEvent> listener = event -> {};
            voterSource.subscribeToUpdates(PDP_ID, listener);
            assertThat(listenerEntryPresent(voterSource, PDP_ID)).isTrue();

            voterSource.unsubscribeFromUpdates(PDP_ID, listener);

            assertThat(listenerEntryPresent(voterSource, PDP_ID)).isFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean listenerEntryPresent(PdpVoterSource source, String pdpId) throws Exception {
        val field = PdpVoterSource.class.getDeclaredField("updateListeners");
        field.setAccessible(true);
        return ((Map<String, ?>) field.get(source)).containsKey(pdpId);
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

    @Test
    @DisplayName("a plugins swap while STALE fails closed to ERROR and drops the voter bound to the retired bundle")
    void whenPluginsSwapWhileStaleThenFailsClosedToErrorAndOldVoterDropped() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            // LOADED against the first bundle.
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val lastGood = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();

            // A broken config update with keepOld leaves the pdpId STALE, still serving the
            // last-good voter, which is bound to the first bundle's broker.
            voterSource.loadConfiguration(brokenConfig(), true);
            assertThat(voterSource.getPdpStatus(PDP_ID))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));

            // A plugins swap recompiles the retained (still broken) config. Keeping the old
            // voter would serve one bound to the retired bundle, so it must fail closed.
            pluginsSource.publish(pluginsOf(brokerWithStandard()));

            assertThat(voterSource.getPdpStatus(PDP_ID))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.ERROR));
            assertThat(voterSource.getCurrentConfiguration(PDP_ID))
                    .hasValueSatisfying(after -> assertThat(after).isNotSameAs(lastGood));
        }
    }

    @Test
    @DisplayName("a config-update compile failure with keepOld still goes STALE and keeps serving the last-good voter")
    void whenConfigUpdateFailsWithKeepOldThenStaleKeepsServingLastGoodVoter() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            val good = voterSource.getCurrentConfiguration(PDP_ID).orElseThrow();

            voterSource.loadConfiguration(brokenConfig(), true);

            assertThat(voterSource.getPdpStatus(PDP_ID))
                    .hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(PdpState.STALE));
            assertThat(voterSource.getCurrentConfiguration(PDP_ID))
                    .hasValueSatisfying(after -> assertThat(after).isSameAs(good));
        }
    }

    @Test
    @DisplayName("a plugins push migrates every retained pdpId to the new bundle, not just the first")
    void whenPluginsPushedThenAllRetainedPdpIdsMigrateToNewBundle() {
        val pluginsSource = new MutablePluginsSource(pluginsOf(brokerWithStandard()));
        try (val voterSource = new PdpVoterSource(pluginsSource, CLOCK)) {
            voterSource.loadConfiguration(policyConfig(CONFIG_A), false);
            voterSource.loadConfiguration(new PDPConfiguration("pdp-2", "config-2", CombiningAlgorithm.DEFAULT,
                    List.of("policy \"p\" permit"), EMPTY), false);

            val newBroker = brokerWithStandard();
            pluginsSource.publish(pluginsOf(newBroker));

            assertThat(voterSource.getCurrentConfiguration(PDP_ID))
                    .hasValueSatisfying(v -> assertThat(v.plugins().functionBroker()).isSameAs(newBroker));
            assertThat(voterSource.getCurrentConfiguration("pdp-2"))
                    .hasValueSatisfying(v -> assertThat(v.plugins().functionBroker()).isSameAs(newBroker));
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

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

import static io.sapl.api.model.Value.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.configuration.PreparationResult.Accepted;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("Listener updates preserve the active policies, notifications and extensions")
class PdpVoterSourceListenerConsistencyTests {

    private static final Clock  CLOCK                = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"),
            ZoneOffset.UTC);
    private static final String PDP_ID               = "orders";
    private static final String EXTENSION_NAME       = "upstreams";
    private static final String ORIGINAL_ENDPOINT    = "https://original.example/mcp";
    private static final String REPLACEMENT_ENDPOINT = "https://replacement.example/mcp";
    private static final String PERMIT_POLICY        = """
            policy "access" permit""";
    private static final String DENY_POLICY          = """
            policy "access" deny""";

    @Mock
    private AttributeBroker                  attributeBroker;
    @Mock
    private ExtensionsProcessor              host;
    @Captor
    private ArgumentCaptor<PDPConfiguration> committed;

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Initial plugins deferred: {0}")
    @DisplayName("Every listener keeps the latest configuration after another listener replaces it")
    void whenListenerReplacesConfigurationThenAllListenersKeepLatestConfiguration(boolean deferred) {
        val events = pluginsSource(deferred);
        try (val source = new PdpVoterSource(events, CLOCK)) {
            val original             = configuration("original", PERMIT_POLICY);
            val replacement          = configuration("replacement", DENY_POLICY);
            val replacementRequested = new AtomicBoolean();
            val replaceOriginal      = replaceOnOriginalUpdate(source, replacement, replacementRequested);
            val observations         = List.of(new ArrayList<String>(), new ArrayList<String>());
            for (val received : observations) {
                source.subscribeToUpdates(PDP_ID, event -> {
                    if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)) {
                        received.add(voter.metadata().configurationId());
                    }
                    replaceOriginal.accept(event);
                });
            }

            activateOriginal(source, events, original, deferred);

            assertThat(replacementRequested).isTrue();
            assertThat(source.getCurrentConfiguration(PDP_ID)).get()
                    .satisfies(voter -> assertThat(voter.metadata().configurationId()).isEqualTo("replacement"));
            assertThat(decision(source)).isEqualTo(Decision.DENY);
            assertThat(observations).allSatisfy(received -> assertThat(received).last().isEqualTo("replacement"));
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Initial plugins deferred: {0}")
    @DisplayName("The superseded configuration cannot deploy its extensions after its replacement")
    void whenListenerReplacesConfigurationThenCommittedExtensionsMatchActiveConfiguration(boolean deferred) {
        val events = pluginsSource(deferred);
        try (val source = sourceWithExtensions(events)) {
            val original             = configuration("original", PERMIT_POLICY)
                    .withExtensions(Map.of(EXTENSION_NAME, of(ORIGINAL_ENDPOINT)), Map.of());
            val replacement          = configuration("replacement", DENY_POLICY)
                    .withExtensions(Map.of(EXTENSION_NAME, of(REPLACEMENT_ENDPOINT)), Map.of());
            val replacementRequested = new AtomicBoolean();
            source.subscribeToUpdates(PDP_ID, replaceOnOriginalUpdate(source, replacement, replacementRequested));

            activateOriginal(source, events, original, deferred);

            assertThat(replacementRequested).isTrue();
            assertThat(source.getCurrentConfiguration(PDP_ID)).get()
                    .satisfies(voter -> assertThat(voter.metadata().configurationId()).isEqualTo("replacement"));
            assertThat(decision(source)).isEqualTo(Decision.DENY);
            val configurations = committedConfigurations();
            assertSoftly(softly -> {
                softly.assertThat(configurations).last().returns("replacement", PDPConfiguration::configurationId);
                softly.assertThat(configurations.getLast().extensions()).containsEntry(EXTENSION_NAME,
                        of(REPLACEMENT_ENDPOINT));
            });
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Initial plugins deferred: {0}")
    @DisplayName("A rejected listener update preserves the just activated policies and their required extension commit")
    void whenListenerLoadsBrokenConfigurationThenActiveConfigurationKeepsItsExtensions(boolean deferred) {
        val events = pluginsSource(deferred);
        try (val source = sourceWithExtensions(events)) {
            val original           = configuration("original", PERMIT_POLICY)
                    .withExtensions(Map.of(EXTENSION_NAME, of(ORIGINAL_ENDPOINT)), Map.of());
            val rejected           = configuration("rejected", "not a SAPL document");
            val rejectionRequested = new AtomicBoolean();
            source.subscribeToUpdates(PDP_ID, replaceOnOriginalUpdate(source, rejected, rejectionRequested));

            activateOriginal(source, events, original, deferred);

            assertThat(rejectionRequested).isTrue();
            val status         = source.getPdpStatus(PDP_ID).orElseThrow();
            val served         = source.getCurrentConfiguration(PDP_ID).orElseThrow();
            val actualDecision = decision(source);
            val configurations = committedConfigurations();
            assertSoftly(softly -> {
                softly.assertThat(configurations).extracting(PDPConfiguration::configurationId).contains("original")
                        .doesNotContain("rejected");
                softly.assertThat(configurations.getLast().extensions()).containsEntry(EXTENSION_NAME,
                        of(ORIGINAL_ENDPOINT));
                softly.assertThat(status).returns(PdpState.STALE, PdpStatus::state).returns("original",
                        PdpStatus::configurationId);
                softly.assertThat(status.lastError()).contains("Configuration 'rejected'", PDP_ID);
                softly.assertThat(served.metadata().configurationId()).isEqualTo("original");
                softly.assertThat(actualDecision).isEqualTo(Decision.PERMIT);
            });
        }
    }

    private PdpVoterSource sourceWithExtensions(MutablePluginsSource events) {
        when(host.prepare(eq(PDP_ID), any(PDPConfiguration.class))).thenReturn(new Accepted());
        return new PdpVoterSource(events, CLOCK, host);
    }

    private List<PDPConfiguration> committedConfigurations() {
        verify(host, atLeastOnce()).commit(eq(PDP_ID), committed.capture());
        return committed.getAllValues();
    }

    private Decision decision(PdpVoterSource source) {
        val pdp          = new BlockingPolicyDecisionPoint(source, attributeBroker, new ThreadLocalRandomIdFactory());
        val subscription = AuthorizationSubscription.of("alice", "read", "archive");
        return pdp.decideOnce(subscription, PDP_ID).decision();
    }

    private static Consumer<PdpUpdateEvent> replaceOnOriginalUpdate(PdpVoterSource source, PDPConfiguration replacement,
            AtomicBoolean requested) {
        return event -> {
            if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)
                    && "original".equals(voter.metadata().configurationId()) && requested.compareAndSet(false, true)) {
                source.loadConfiguration(replacement);
            }
        };
    }

    private static void activateOriginal(PdpVoterSource source, MutablePluginsSource events, PDPConfiguration original,
            boolean deferred) {
        source.loadConfiguration(original);
        if (deferred) {
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.AWAITING_PLUGINS, PdpStatus::state);
            events.publish(plugins());
        }
    }

    private static MutablePluginsSource pluginsSource(boolean deferred) {
        if (deferred) {
            return new MutablePluginsSource();
        }
        return new MutablePluginsSource(plugins());
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    private static PDPConfiguration configuration(String id, String policy) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}

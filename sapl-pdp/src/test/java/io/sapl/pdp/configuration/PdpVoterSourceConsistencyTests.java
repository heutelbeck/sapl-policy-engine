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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;

import lombok.val;

@ExtendWith(MockitoExtension.class)
@DisplayName("PDP status and served policies remain consistent during plugin migration")
class PdpVoterSourceConsistencyTests {

    private static final String  PDP_ID        = "orders";
    private static final Instant NOW           = Instant.parse("2026-10-31T12:00:00Z");
    private static final String  PERMIT_POLICY = """
            policy "access" permit""";
    private static final String  DENY_POLICY   = """
            policy "access" deny""";

    @Mock
    Clock           clock;
    @Mock
    AttributeBroker attributeBroker;

    @Test
    @DisplayName("A timestamp failure cannot leave a permissive voter bound to retired plugins")
    void whenFailureTimestampCannotBeReadDuringPluginMigrationThenPdpFailsClosed() {
        when(clock.instant()).thenReturn(NOW);
        val initialPlugins     = plugins();
        val replacementPlugins = plugins();
        val events             = new MutablePluginsSource(initialPlugins);
        try (val source = new PdpVoterSource(events, clock)) {
            source.loadConfiguration(configuration("active", PERMIT_POLICY));
            val lastGood = source.getCurrentConfiguration(PDP_ID).orElseThrow();
            source.loadConfiguration(configuration("rejected", "not a SAPL document"));
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.STALE, PdpStatus::state);
            assertThat(source.getCurrentConfiguration(PDP_ID)).containsSame(lastGood);

            val timestampFailureReached = new AtomicBoolean();
            doAnswer(ignored -> {
                timestampFailureReached.set(true);
                throw new DateTimeException("Clock unavailable while recording compilation failure");
            }).doReturn(NOW).when(clock).instant();

            events.publish(replacementPlugins);

            assertThat(timestampFailureReached).isTrue();
            assertThat(source.getPlugins()).isSameAs(replacementPlugins);
            val status   = source.getPdpStatus(PDP_ID).orElseThrow();
            val served   = source.getCurrentConfiguration(PDP_ID);
            val decision = pdp(source).decideOnce(subscription(), PDP_ID);
            assertSoftly(softly -> {
                softly.assertThat(status.state()).as("A failed migration must be reported as ERROR")
                        .isEqualTo(PdpState.ERROR);
                softly.assertThat(served.filter(compiled -> compiled.plugins() == initialPlugins))
                        .as("No voter may remain bound to the retired plugins").isEmpty();
                softly.assertThat(decision.decision()).as("A failed migration must not keep granting access")
                        .isEqualTo(Decision.INDETERMINATE);
            });
        }
    }

    @Test
    @DisplayName("A configuration loaded by an update listener must remain the active status after migration")
    void whenListenerLoadsNewConfigurationDuringPluginMigrationThenStatusMatchesServedConfiguration() {
        val replacementPlugins = plugins();
        val events             = new MutablePluginsSource(plugins());
        try (val source = new PdpVoterSource(events, Clock.fixed(NOW, ZoneOffset.UTC))) {
            source.loadConfiguration(configuration("original", PERMIT_POLICY));
            val replacement = configuration("replacement", DENY_POLICY);
            source.subscribeToUpdates(PDP_ID, event -> {
                if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var compiled)
                        && compiled.plugins() == replacementPlugins
                        && "original".equals(compiled.metadata().configurationId())) {
                    source.loadConfiguration(replacement);
                }
            });

            events.publish(replacementPlugins);

            val served   = source.getCurrentConfiguration(PDP_ID).orElseThrow();
            val decision = pdp(source).decideOnce(subscription(), PDP_ID);
            assertThat(served.metadata().configurationId()).isEqualTo("replacement");
            assertThat(decision.decision()).isEqualTo(Decision.DENY);
            assertThat(source.getPdpStatus(PDP_ID)).get().returns(PdpState.LOADED, PdpStatus::state)
                    .returns(served.metadata().configurationId(), PdpStatus::configurationId);
        }
    }

    private BlockingPolicyDecisionPoint pdp(PdpVoterSource source) {
        return new BlockingPolicyDecisionPoint(source, attributeBroker, new ThreadLocalRandomIdFactory());
    }

    private static AuthorizationSubscription subscription() {
        return AuthorizationSubscription.of("alice", "read", "archive");
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    private static PDPConfiguration configuration(String id, String policy) {
        return new PDPConfiguration(PDP_ID, id, CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}

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

import static io.sapl.api.test.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.ast.Outcome;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.plugins.PluginsBundle;
import lombok.val;

@DisplayName("BlockingPolicyDecisionPoint notifies the decision interceptors of the configuration in effect")
class BlockingPolicyDecisionPointInterceptorTests {

    private static final String                    PDP_ID   = "test-pdp";
    private static final AuthorizationSubscription SUB      = subscription("subject", "action", "resource");
    private static final long                      WAIT_MS  = 5000;
    private static final PdpVoterMetadata          METADATA = new PdpVoterMetadata("test", PDP_ID, "test-config", null,
            Outcome.PERMIT_OR_DENY, false);

    private static CompiledPdp compiledWith(DecisionInterceptor... interceptors) {
        return new CompiledPdp(METADATA, Vote.abstain(METADATA), null,
                new PluginsBundle(mock(FunctionBroker.class), List.of(interceptors), List.of()));
    }

    private static PdpVoterSource sourceWithBase(DecisionInterceptor... baseInterceptors) {
        val source = mock(PdpVoterSource.class);
        when(source.getBasePlugins())
                .thenReturn(new PluginsBundle(mock(FunctionBroker.class), List.of(baseInterceptors), List.of()));
        return source;
    }

    private static BlockingPolicyDecisionPoint pdpOn(PdpVoterSource source) {
        return new BlockingPolicyDecisionPoint(source, mock(AttributeBroker.class), new ThreadLocalRandomIdFactory());
    }

    private static void verifyNotified(DecisionInterceptor interceptor) {
        verify(interceptor).onDecision(any(), any(), anyString(), eq(SUB));
    }

    private static void verifyNotNotified(DecisionInterceptor interceptor) {
        verify(interceptor, never()).onDecision(any(), any(), anyString(), any());
    }

    @Nested
    @DisplayName("decideOnce")
    class DecideOnce {

        @Test
        @DisplayName("an interceptor of the configuration is notified")
        void whenConfigurationHasInterceptorThenItIsNotified() {
            val configurationInterceptor = mock(DecisionInterceptor.class);
            val source                   = sourceWithBase();
            when(source.getCurrentConfiguration(PDP_ID))
                    .thenReturn(Optional.of(compiledWith(configurationInterceptor)));

            pdpOn(source).decideOnce(SUB, PDP_ID);

            verifyNotified(configurationInterceptor);
        }

        @Test
        @DisplayName("a base interceptor missing from the configuration is not notified")
        void whenConfigurationHasNoInterceptorThenBaseInterceptorIsNotNotified() {
            val baseInterceptor = mock(DecisionInterceptor.class);
            val source          = sourceWithBase(baseInterceptor);
            when(source.getCurrentConfiguration(PDP_ID)).thenReturn(Optional.of(compiledWith()));

            pdpOn(source).decideOnce(SUB, PDP_ID);

            verifyNotNotified(baseInterceptor);
        }

        @Test
        @DisplayName("without a configuration only the base interceptors are notified")
        void whenNoConfigurationThenBaseInterceptorIsNotified() {
            val baseInterceptor = mock(DecisionInterceptor.class);
            val source          = sourceWithBase(baseInterceptor);
            when(source.getCurrentConfiguration(PDP_ID)).thenReturn(Optional.empty());

            pdpOn(source).decideOnce(SUB, PDP_ID);

            verifyNotified(baseInterceptor);
        }
    }

    @Nested
    @DisplayName("streaming")
    class Streaming {

        @Test
        @Timeout(10)
        @DisplayName("a running subscription follows the interceptors of a new configuration")
        void whenConfigurationChangesDuringSubscriptionThenInterceptorsFollow() throws Exception {
            val interceptor = mock(DecisionInterceptor.class);
            val listenerRef = new AtomicReference<Consumer<PdpUpdateEvent>>();
            val source      = sourceWithBase();
            doAnswer(invocation -> {
                listenerRef.set(invocation.getArgument(1));
                return null;
            }).when(source).subscribeToUpdates(eq(PDP_ID), any());

            try (val stream = pdpOn(source).decide(SUB, PDP_ID)) {
                listenerRef.get().accept(new PdpUpdateEvent.Voter(PDP_ID, compiledWith()));
                assertThat(stream.awaitNext()).isNotNull();
                verifyNotNotified(interceptor);

                listenerRef.get().accept(new PdpUpdateEvent.Voter(PDP_ID, compiledWith(interceptor)));

                verify(interceptor, timeout(WAIT_MS)).onDecision(any(), any(), anyString(), eq(SUB));
            }
        }

        @Test
        @Timeout(10)
        @DisplayName("a multi-subscription notifies the interceptor once per contained subscription")
        void whenMultiSubscriptionThenInterceptorIsNotifiedPerSubscription() throws Exception {
            val interceptor = mock(DecisionInterceptor.class);
            val source      = sourceWithBase();
            doAnswer(invocation -> {
                Consumer<PdpUpdateEvent> listener = invocation.getArgument(1);
                listener.accept(new PdpUpdateEvent.Voter(PDP_ID, compiledWith(interceptor)));
                return null;
            }).when(source).subscribeToUpdates(eq(PDP_ID), any());
            val multi = new MultiAuthorizationSubscription().addSubscription("first", SUB).addSubscription("second",
                    SUB);

            try (val stream = pdpOn(source).decideAll(multi, PDP_ID)) {
                assertThat(stream.awaitNext()).isNotNull();

                verify(interceptor, timeout(WAIT_MS).times(2)).onDecision(any(), any(), anyString(), eq(SUB));
            }
        }
    }
}

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

import static java.lang.System.nanoTime;
import static java.lang.Thread.State.WAITING;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
@DisplayName("Configuration callers keep their own threads and plugin migrations cannot skip retired voters")
class PdpVoterSourceThreadIsolationTests {

    private static final String PDP_ID        = "orders";
    private static final Clock  CLOCK         = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);
    private static final String PERMIT_POLICY = """
            policy "access" permit""";
    private static final String DENY_POLICY   = """
            policy "access" deny""";

    @Mock
    private ExtensionsProcessor host;
    @Mock
    private AttributeBroker     attributeBroker;

    @Timeout(20)
    @EnumSource(ConcurrentRequirement.class)
    @ParameterizedTest(name = "Concurrent requirement: {0}")
    @DisplayName("A waiting caller executes its own transition without extending the first caller's work")
    void whenAnotherCallerWaitsThenEachCallerOwnsItsWork(ConcurrentRequirement requirement) throws Exception {
        val events             = new MutablePluginsSource(plugins());
        val context            = new ThreadLocal<String>();
        val executions         = new ArrayList<Execution>();
        val entered            = new CountDownLatch(1);
        val release            = new CountDownLatch(1);
        val replacementEntered = new CountDownLatch(1);
        val replacementRelease = new CountDownLatch(1);
        val callerThread       = new AtomicReference<Thread>();
        when(host.prepare(eq(PDP_ID), any(PDPConfiguration.class))).thenAnswer(invocation -> {
            if ("replacement".equals(invocation.getArgument(1, PDPConfiguration.class).configurationId())) {
                executions.add(new Execution("prepare", currentThread(), context.get()));
            }
            return new Accepted();
        });
        doAnswer(invocation -> {
            val configuration = invocation.getArgument(1, PDPConfiguration.class);
            if ("original".equals(configuration.configurationId())) {
                entered.countDown();
                assertThat(release.await(5, SECONDS)).isTrue();
            } else {
                executions.add(new Execution("commit", currentThread(), context.get()));
                replacementEntered.countDown();
                assertThat(replacementRelease.await(5, SECONDS)).isTrue();
            }
            return null;
        }).when(host).commit(eq(PDP_ID), any(PDPConfiguration.class));
        try (val source = new PdpVoterSource(events, CLOCK, host); val executor = newVirtualThreadPerTaskExecutor()) {
            source.subscribeToUpdates(PDP_ID, event -> {
                if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)
                        && "replacement".equals(voter.metadata().configurationId())) {
                    executions.add(new Execution("listener", currentThread(), context.get()));
                }
            });
            val original = executor.submit(() -> {
                context.set("original caller");
                try {
                    source.loadConfiguration(configuration(PDP_ID, "original", PERMIT_POLICY));
                } finally {
                    context.remove();
                }
            });
            try {
                assertThat(entered.await(5, SECONDS)).isTrue();
                val started     = new CountDownLatch(1);
                val replacement = executor.submit(() -> {
                                    context.set("replacement caller");
                                    callerThread.set(currentThread());
                                    started.countDown();
                                    try {
                                        source.loadConfiguration(configuration(PDP_ID, "replacement", DENY_POLICY));
                                    } finally {
                                        context.remove();
                                    }
                                });
                assertThat(started.await(5, SECONDS)).isTrue();
                awaitWaiting(callerThread.get());
                release.countDown();
                assertThat(replacementEntered.await(5, SECONDS)).isTrue();

                if (requirement == ConcurrentRequirement.CALLER_THREAD) {
                    assertThat(executions).extracting(Execution::operation).containsExactly("prepare", "listener",
                            "commit");
                    assertThat(executions).allSatisfy(execution -> {
                        assertThat(execution.thread()).isSameAs(callerThread.get());
                        assertThat(execution.context()).isEqualTo("replacement caller");
                    });
                } else {
                    assertThatCode(() -> original.get(2, SECONDS)).doesNotThrowAnyException();
                }
                replacementRelease.countDown();
                replacement.get(5, SECONDS);
                original.get(5, SECONDS);
            } finally {
                release.countDown();
                replacementRelease.countDown();
            }
        }
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "Interleaved configuration compiles: {0}")
    @DisplayName("An update during migration cannot stamp a voter bound to the previous plugins as current")
    void whenConfigurationChangesBeforeItsMigrationThenNoRetiredVoterSurvives(boolean valid) {
        when(host.prepare(anyString(), any(PDPConfiguration.class))).thenReturn(new Accepted());
        val initial     = plugins();
        val replacement = plugins();
        val events      = new MutablePluginsSource(initial);
        try (val source = new PdpVoterSource(events, CLOCK, host)) {
            val requested = new AtomicBoolean();
            val target    = new AtomicReference<String>();
            for (val pdpId : List.of("one", "two")) {
                source.loadConfiguration(configuration(pdpId, "original", PERMIT_POLICY));
                source.subscribeToUpdates(pdpId, event -> {
                    if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)
                            && voter.plugins() == replacement && requested.compareAndSet(false, true)) {
                        val other = "one".equals(pdpId) ? "two" : "one";
                        target.set(other);
                        source.loadConfiguration(
                                configuration(other, "replacement", valid ? DENY_POLICY : "not a SAPL document"));
                    }
                });
            }

            events.publish(replacement);

            assertThat(requested).isTrue();
            val pdpId        = target.get();
            val served       = source.getCurrentConfiguration(pdpId).orElseThrow();
            val status       = source.getPdpStatus(pdpId).orElseThrow();
            val pdp          = new BlockingPolicyDecisionPoint(source, attributeBroker,
                    new ThreadLocalRandomIdFactory());
            val subscription = AuthorizationSubscription.of("alice", "read", "archive");
            val decision     = pdp.decideOnce(subscription, pdpId).decision();
            assertSoftly(softly -> {
                softly.assertThat(served.plugins()).as("The served voter must belong to the current runtime")
                        .isSameAs(replacement);
                softly.assertThat(status.state()).isEqualTo(valid ? PdpState.LOADED : PdpState.ERROR);
                softly.assertThat(decision).isEqualTo(valid ? Decision.DENY : Decision.INDETERMINATE);
            });
        }
    }

    private static void awaitWaiting(Thread thread) {
        val deadline = nanoTime() + SECONDS.toNanos(5);
        while (thread.getState() != WAITING && nanoTime() < deadline) {
            parkNanos(MILLISECONDS.toNanos(1));
        }
        assertThat(thread.getState()).isEqualTo(WAITING);
    }

    private static PluginsBundle plugins() {
        return new PluginsBundle(new DefaultFunctionBroker());
    }

    private static PDPConfiguration configuration(String pdpId, String id, String policy) {
        return new PDPConfiguration(pdpId, id, CombiningAlgorithm.DEFAULT, List.of(policy),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private record Execution(String operation, Thread thread, String context) {}

    private enum ConcurrentRequirement {
        CALLER_THREAD,
        OWN_COMPLETION
    }
}

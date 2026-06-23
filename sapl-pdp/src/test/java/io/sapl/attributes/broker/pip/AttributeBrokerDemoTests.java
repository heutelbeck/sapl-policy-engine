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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import com.github.valfirst.slf4jtest.LoggingEvent;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end story for the {@link PolicyInformationPointAttributeBroker},
 * structured to read top-to-bottom as documentation: how an
 * application loads PIPs, opens consumer subscriptions, observes
 * snapshot updates, hot-swaps a PIP for a new version without
 * publishing a transient ErrorValue, and finally unloads.
 * <p>
 * Use this file as the canonical entry point when explaining the
 * broker to someone new; the focused
 * {@code PolicyInformationPointAttributeBrokerTests}
 * unit suite covers the edge cases.
 */
@DisplayName("AttributeBroker end-to-end demo")
class AttributeBrokerDemoTests {

    private static final boolean PRINT_OUTPUT = false;
    private static final Clock   CLOCK        = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void clearCapturedLogs() {
        TestLoggerFactory.clear();
    }

    @AfterEach
    void dumpCapturedLogs() {
        if (!PRINT_OUTPUT) {
            return;
        }
        // The broker emits DEBUG/TRACE around load/swap/unload (cold-path
        // events). slf4j-test captures them silently; surface them here so the
        // demo trace is actually visible when this test runs. Filter to events
        // from io.sapl.attributes.broker so the noise from unrelated loggers
        // does not bury the narrative.
        val events = TestLoggerFactory.getLoggingEvents().stream()
                .filter(e -> e.getCreatingLogger().getName().startsWith("io.sapl.attributes.broker")).toList();
        if (events.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("---- Captured broker events (" + events.size() + ") ----");
        for (LoggingEvent e : events) {
            System.out.printf("  [%-5s] %-30s %s%n", e.getLevel(), shortenLoggerName(e.getCreatingLogger().getName()),
                    e.getFormattedMessage());
        }
        System.out.println("------------------------------------------------------");
    }

    private static String shortenLoggerName(String fqn) {
        val lastDot = fqn.lastIndexOf('.');
        return lastDot < 0 ? fqn : fqn.substring(lastDot + 1);
    }

    @Test
    @DisplayName("load a PIP, observe a value, hot-swap to a new version, observe the new value, unload")
    void endToEndDemo() {
        // Construct the broker. The broker is also the catalog: plug-in engines
        // call load/swap/unload on this object; evaluators call open/close.
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            // Load the v1 PIP. The broker extracts every annotated method into
            // a StreamAttributeFinderSpecification, checks for collisions, and
            // registers them; returns a handle for later swap/unload.
            val v1Handle = broker.load(new GreetingPipV1());
            assertThat(v1Handle.pipName()).isEqualTo("greeting");
            assertThat(v1Handle.isLoaded()).isTrue();
            assertThat(broker.catalog()).containsExactly(v1Handle);

            // Open a consumer subscription. The consumer hands the broker a
            // Set<SubscriptionKey> and a callback; the broker wires backing
            // subscriptions and fires the callback once every dep has a value.
            val helloKey  = key("greeting.hello");
            val snapshots = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();

            val consumerSub = broker.open("demo-subscription", Set.of(helloKey), snapshot -> {
                snapshots.add(snapshot);
                // The callback returns the next dep set. A consumer that wants
                // to keep observing the same deps returns its current set.
                return Set.of(helloKey);
            });

            // First snapshot arrives quickly (v1's value is constant).
            Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(snapshots).hasSizeGreaterThanOrEqualTo(1));
            assertThat(snapshots.get(0).get(helloKey).value()).isEqualTo(Value.of("hello from v1"));

            // Hot-swap to v2. The broker atomically replaces v1's specs with
            // v2's. The active backing rebinds to v2 without publishing a
            // transient ErrorValue: the mailbox keeps the prior value and
            // transitions to v2's value when v2 emits.
            val v2Handle = broker.swap(v1Handle, new GreetingPipV2());
            assertThat(v1Handle.isLoaded()).isFalse();
            assertThat(v2Handle.isLoaded()).isTrue();

            GreetingPipV2.LATEST.put(Value.of("hello from v2"));
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(snapshots)
                    .anySatisfy(s -> assertThat(s.get(helloKey).value()).isEqualTo(Value.of("hello from v2"))));

            // No snapshot in the consumer's history carries an ErrorValue for
            // the swapped key. The transition was invisible to consumers.
            for (val s : snapshots) {
                assertThat(s.get(helloKey).value()).isNotInstanceOf(ErrorValue.class);
            }

            // Unload the PIP. Active backings publish UNDEFINED (absence at
            // this layer) and tear down their source. Under a layered broker
            // composing this with a repository, the consumer would see the
            // repository value; standalone, UNDEFINED.
            v2Handle.unload();
            assertThat(v2Handle.isLoaded()).isFalse();
            assertThat(broker.catalog()).isEmpty();

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                val last = snapshots.get(snapshots.size() - 1).get(helloKey).value();
                assertThat(last).isEqualTo(Value.UNDEFINED);
            });

            // Close the consumer subscription. The try-with-resources around
            // the broker takes care of any remaining backings.
            consumerSub.close();
        }
    }

    private static SubscriptionKey key(String fqn) {
        val invocation = new AttributeFinderInvocation("test-pdp", "default", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        return new SubscriptionKey(invocation, false);
    }

    @Test
    @DisplayName("loads every real PIP shipped in sapl-pdp into a single broker without collisions")
    void loadsAllRealSaplPdpPips() {
        // Wire the minimal infrastructure each PIP needs: a clock, a virtual-time
        // scheduler, a JSON mapper, and a blocking HTTP client. None of these
        // PIPs is exercised through subscriptions here; the test asserts only
        // that every real PIP shipped in sapl-pdp registers cleanly under its
        // declared namespace.
        val mapper      = JsonMapper.builder().build();
        val clock       = CLOCK;
        val scheduler   = new RealTimeScheduler(clock);
        val httpClient  = HttpClient.newHttpClient();
        val webClient   = new BlockingWebClient(mapper, httpClient);
        val keyProvider = new JWTKeyProvider(httpClient, clock);

        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val timeHandle = broker.load(new TimePolicyInformationPoint(clock, scheduler));
            val x509Handle = broker.load(new X509PolicyInformationPoint(clock, scheduler));
            val httpHandle = broker.load(new HttpPolicyInformationPoint(webClient));
            val jwtHandle  = broker.load(new JWTPolicyInformationPoint(keyProvider, clock, scheduler));

            assertThat(broker.catalog()).containsExactlyInAnyOrder(timeHandle, x509Handle, httpHandle, jwtHandle);
            assertThat(timeHandle.pipName()).isEqualTo("time");
            assertThat(x509Handle.pipName()).isEqualTo("x509");
            assertThat(httpHandle.pipName()).isEqualTo("http");
            assertThat(jwtHandle.pipName()).isEqualTo("jwt");
            assertThat(timeHandle.isLoaded()).isTrue();
            assertThat(x509Handle.isLoaded()).isTrue();
            assertThat(httpHandle.isLoaded()).isTrue();
            assertThat(jwtHandle.isLoaded()).isTrue();

            // Sanity: an environment attribute on the time PIP resolves.
            val timeNow = new AttributeFinderInvocation("test-pdp", "default", "time.now", List.of(),
                    Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                    new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            assertThat(broker.resolve(timeNow)).isPresent();

            // Unload them all; catalog ends empty and every handle reports unloaded.
            timeHandle.unload();
            x509Handle.unload();
            httpHandle.unload();
            jwtHandle.unload();

            assertThat(broker.catalog()).isEmpty();
            assertThat(timeHandle.isLoaded()).isFalse();
            assertThat(x509Handle.isLoaded()).isFalse();
            assertThat(httpHandle.isLoaded()).isFalse();
            assertThat(jwtHandle.isLoaded()).isFalse();
        }
    }

    @Test
    @DisplayName("swap promotes an existing terminal invocation to a spec the replacement newly provides")
    void whenSwapAddsSpecThenExistingUnmatchedInvocationIsPromoted() {
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val handle    = broker.load(new ProvPipX());
            val yKey      = key("prov.y");
            val snapshots = new CopyOnWriteArrayList<Map<SubscriptionKey, AttributeSnapshot>>();

            // prov.y is not provided by ProvPipX, so with no fallback the invocation is
            // terminal UNDEFINED.
            val sub = broker.open("s1", Set.of(yKey), snapshot -> {
                snapshots.add(snapshot);
                return Set.of(yKey);
            });
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(snapshots)
                    .anySatisfy(s -> assertThat(s.get(yKey).value()).isEqualTo(Value.UNDEFINED)));

            // The replacement adds prov.y. The existing terminal invocation must be
            // promoted
            // to the new PIP, exactly as load() promotes a newly matching invocation.
            broker.swap(handle, new ProvPipXY());

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(snapshots)
                    .anySatisfy(s -> assertThat(s.get(yKey).value()).isEqualTo(Value.of("y"))));
            sub.close();
        }
    }

    @PolicyInformationPoint(name = "prov")
    static class ProvPipX {

        @EnvironmentAttribute
        public static Value x() {
            return Value.of("x");
        }
    }

    @PolicyInformationPoint(name = "prov")
    static class ProvPipXY {

        @EnvironmentAttribute
        public static Value x() {
            return Value.of("x");
        }

        @EnvironmentAttribute
        public static Value y() {
            return Value.of("y");
        }
    }

    @PolicyInformationPoint(name = "greeting")
    static class GreetingPipV1 {

        @EnvironmentAttribute
        public static Value hello() {
            return Value.of("hello from v1");
        }
    }

    @PolicyInformationPoint(name = "greeting")
    static class GreetingPipV2 {

        static final LatestSlotStream<Value> LATEST = new LatestSlotStream<>();

        @EnvironmentAttribute
        public Stream<Value> hello() {
            return LATEST;
        }
    }
}

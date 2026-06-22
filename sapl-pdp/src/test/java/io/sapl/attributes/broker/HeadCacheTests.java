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
package io.sapl.attributes.broker;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the eval-side head-cache helper. No broker,
 * no concurrency; the five-step pipeline is exercised in isolation.
 */
@DisplayName("HeadCache")
class HeadCacheTests {

    private static final AttributeAccessContext CTX = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT,
            Value.EMPTY_OBJECT);

    private static AttributeFinderInvocation invocation(String fqn) {
        return new AttributeFinderInvocation("test-pdp", "default", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false, CTX);
    }

    private static SubscriptionKey headKey(String fqn) {
        return new SubscriptionKey(invocation(fqn), true);
    }

    private static SubscriptionKey liveKey(String fqn) {
        return new SubscriptionKey(invocation(fqn), false);
    }

    private static AttributeSnapshot snap(Value value) {
        return new AttributeSnapshot(value, Instant.parse("2026-05-17T12:00:00Z"));
    }

    @Nested
    @DisplayName("merge")
    class Merge {

        @Test
        @DisplayName("with empty cache returns the broker snapshot unchanged (no allocation)")
        void whenEmptyCacheThenReturnsInputUnchanged() {
            val cache = new HeadCache();
            val input = Map.of(liveKey("ns.x"), snap(Value.of("v")));

            assertThat(cache.merge(input)).isSameAs(input);
        }

        @Test
        @DisplayName("cached head values override the broker's delivery for the same head key")
        void whenHeadCachedThenOverridesBroker() {
            val cache     = new HeadCache();
            val key       = headKey("ns.x");
            val firstSeen = Map.of(key, snap(Value.of("v1")));
            cache.captureFrom(firstSeen);

            val brokerSnap = Map.of(key, snap(Value.of("v2")));
            val merged     = cache.merge(brokerSnap);

            assertThat(merged.get(key).value()).isEqualTo(Value.of("v1"));
        }

        @Test
        @DisplayName("non-head keys flow through from the broker snapshot unchanged")
        void whenNonHeadKeyThenBrokerValueStays() {
            val cache = new HeadCache();
            cache.captureFrom(Map.of(headKey("ns.x"), snap(Value.of("v1"))));

            val brokerSnap = Map.of(liveKey("ns.y"), snap(Value.of("u1")));
            val merged     = cache.merge(brokerSnap);

            assertThat(merged.get(liveKey("ns.y")).value()).isEqualTo(Value.of("u1"));
        }

        @Test
        @DisplayName("cached head keys are added to the merged snapshot even when absent from the broker delivery")
        void whenHeadKeyNotInBrokerThenServedFromCache() {
            val cache = new HeadCache();
            val key   = headKey("ns.x");
            cache.captureFrom(Map.of(key, snap(Value.of("v1"))));

            val brokerSnap = Map.<SubscriptionKey, AttributeSnapshot>of();
            val merged     = cache.merge(brokerSnap);

            assertThat(merged).containsKey(key).extractingByKey(key).extracting(AttributeSnapshot::value)
                    .isEqualTo(Value.of("v1"));
        }
    }

    @Nested
    @DisplayName("captureFrom")
    class CaptureFrom {

        @Test
        @DisplayName("captures head=true entries")
        void whenHeadEntryThenCaptured() {
            val cache = new HeadCache();
            val key   = headKey("ns.x");
            cache.captureFrom(Map.of(key, snap(Value.of("v1"))));

            assertThat(cache.merge(Map.of()).get(key).value()).isEqualTo(Value.of("v1"));
        }

        @Test
        @DisplayName("ignores head=false entries")
        void whenLiveEntryThenIgnored() {
            val cache = new HeadCache();
            cache.captureFrom(Map.of(liveKey("ns.x"), snap(Value.of("v1"))));

            assertThat(cache.merge(Map.of())).isEmpty();
        }

        @Test
        @DisplayName("putIfAbsent semantics: does not overwrite an existing cache entry")
        void whenSecondObservationThenDoesNotOverwrite() {
            val cache = new HeadCache();
            val key   = headKey("ns.x");
            cache.captureFrom(Map.of(key, snap(Value.of("v1"))));
            cache.captureFrom(Map.of(key, snap(Value.of("v2"))));

            assertThat(cache.merge(Map.of()).get(key).value()).isEqualTo(Value.of("v1"));
        }

        @Test
        @DisplayName("captures multiple head entries in one call")
        void whenMultipleHeadEntriesThenAllCaptured() {
            val cache = new HeadCache();
            val keyA  = headKey("ns.a");
            val keyB  = headKey("ns.b");
            cache.captureFrom(Map.of(keyA, snap(Value.of("va")), keyB, snap(Value.of("vb"))));

            val merged = cache.merge(Map.of());
            assertThat(merged).hasSize(2);
            assertThat(merged.get(keyA).value()).isEqualTo(Value.of("va"));
            assertThat(merged.get(keyB).value()).isEqualTo(Value.of("vb"));
        }
    }

    @Nested
    @DisplayName("retainOnly")
    class RetainOnly {

        @Test
        @DisplayName("drops cache entries whose keys are not in the new eval-deps set")
        void whenKeyDroppedFromDepsThenEvictedFromCache() {
            val cache = new HeadCache();
            val keyA  = headKey("ns.a");
            val keyB  = headKey("ns.b");
            cache.captureFrom(Map.of(keyA, snap(Value.of("va")), keyB, snap(Value.of("vb"))));

            cache.retainOnly(Set.of(keyA));

            val merged = cache.merge(Map.of());
            assertThat(merged).containsOnlyKeys(keyA);
        }

        @Test
        @DisplayName("no-op when cache is empty")
        void whenEmptyCacheThenNoOp() {
            val cache = new HeadCache();
            cache.retainOnly(Set.of(headKey("ns.a")));

            assertThat(cache.merge(Map.of())).isEmpty();
        }

        @Test
        @DisplayName("clearing the entire dep set evicts all cache entries")
        void whenAllDepsRemovedThenCacheEmpty() {
            val cache = new HeadCache();
            cache.captureFrom(Map.of(headKey("ns.a"), snap(Value.of("va"))));

            cache.retainOnly(Set.of(liveKey("ns.y")));

            assertThat(cache.merge(Map.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("brokerDepsFor")
    class BrokerDepsFor {

        @Test
        @DisplayName("returns input unchanged when cache is empty (no allocation)")
        void whenEmptyCacheThenReturnsInputUnchanged() {
            val cache = new HeadCache();
            val deps  = Set.of(headKey("ns.a"), liveKey("ns.y"));

            assertThat(cache.brokerDepsFor(deps)).isSameAs(deps);
        }

        @Test
        @DisplayName("drops head keys whose values are already cached")
        void whenHeadKeyCachedThenFilteredOut() {
            val cache = new HeadCache();
            val keyA  = headKey("ns.a");
            val keyY  = liveKey("ns.y");
            cache.captureFrom(Map.of(keyA, snap(Value.of("va"))));

            assertThat(cache.brokerDepsFor(Set.of(keyA, keyY))).containsExactly(keyY);
        }

        @Test
        @DisplayName("keeps non-head keys regardless of cache contents")
        void whenLiveKeyThenAlwaysPropagated() {
            val cache = new HeadCache();
            cache.captureFrom(Map.of(headKey("ns.a"), snap(Value.of("va"))));

            val deps = Set.of(liveKey("ns.y"));
            assertThat(cache.brokerDepsFor(deps)).containsExactlyElementsOf(deps);
        }

        @Test
        @DisplayName("keeps head keys that are not yet captured")
        void whenHeadKeyNotCachedThenPropagated() {
            val cache = new HeadCache();
            cache.captureFrom(Map.of(headKey("ns.a"), snap(Value.of("va"))));

            val unseen = headKey("ns.b");
            assertThat(cache.brokerDepsFor(Set.of(unseen))).containsExactly(unseen);
        }

        @Test
        @DisplayName("returns the unfiltered set when filtering would yield empty (broker contract requires non-empty)")
        void whenFilteringWouldBeEmptyThenReturnsUnfilteredFallback() {
            val cache = new HeadCache();
            val keyA  = headKey("ns.a");
            cache.captureFrom(Map.of(keyA, snap(Value.of("va"))));

            val onlyCachedHead = Set.of(keyA);
            assertThat(cache.brokerDepsFor(onlyCachedHead)).containsExactlyElementsOf(onlyCachedHead);
        }
    }

    @Nested
    @DisplayName("end-to-end pipeline scenarios")
    class Pipeline {

        @Test
        @DisplayName("typical fire: merge sees cached value, capture is putIfAbsent (no-op), retain keeps it, broker deps drop the head key")
        void whenSteadyStateFireThenCacheServesValue() {
            val cache = new HeadCache();
            val keyH  = headKey("ns.h");
            val keyL  = liveKey("ns.l");
            cache.captureFrom(Map.of(keyH, snap(Value.of("v1"))));

            val brokerSnap = Map.of(keyL, snap(Value.of("u2")));
            val full       = cache.merge(brokerSnap);
            assertThat(full.get(keyH).value()).isEqualTo(Value.of("v1"));
            assertThat(full.get(keyL).value()).isEqualTo(Value.of("u2"));

            // Step 3 with broker delivering nothing new for the head key: no-op.
            cache.captureFrom(brokerSnap);
            cache.retainOnly(Set.of(keyH, keyL));

            assertThat(cache.brokerDepsFor(Set.of(keyH, keyL))).containsExactly(keyL);
        }

        @Test
        @DisplayName("Interpretation C: head dep removed and re-added captures a fresh value on re-entry")
        void whenHeadReEntersAfterEvictionThenFreshCapture() {
            val cache = new HeadCache();
            val keyH  = headKey("ns.h");
            val keyL  = liveKey("ns.l");

            // Round 1: capture the initial value.
            cache.captureFrom(Map.of(keyH, snap(Value.of("v1"))));
            cache.retainOnly(Set.of(keyH, keyL));
            assertThat(cache.merge(Map.of()).get(keyH).value()).isEqualTo(Value.of("v1"));

            // Round 2: head dep dropped; cache evicts it.
            cache.retainOnly(Set.of(keyL));
            assertThat(cache.merge(Map.of()).get(keyH)).isNull();

            // Round 3: head dep re-enters; broker delivers v3 (the world moved).
            cache.captureFrom(Map.of(keyH, snap(Value.of("v3"))));
            cache.retainOnly(Set.of(keyH, keyL));
            assertThat(cache.merge(Map.of()).get(keyH).value()).isEqualTo(Value.of("v3"));
        }

        @Test
        @DisplayName("non-head keys in the broker snapshot pass through merge without being cached")
        void whenLiveKeyInSnapshotThenNotCached() {
            val cache      = new HeadCache();
            val brokerSnap = new HashMap<SubscriptionKey, AttributeSnapshot>();
            brokerSnap.put(liveKey("ns.y"), snap(Value.of("u1")));

            cache.captureFrom(brokerSnap);

            // No head keys captured; cache reports empty via merge with no input.
            assertThat(cache.merge(Map.of())).isEmpty();
        }
    }
}

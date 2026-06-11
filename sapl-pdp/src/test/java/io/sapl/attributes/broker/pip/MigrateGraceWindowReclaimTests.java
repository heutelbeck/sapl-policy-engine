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
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises a hot-swap that runs while an active invocation sits in its
 * refcount-zero grace window, the path on which {@code migrate} promotes a
 * replacement with no inherited subscribers and {@code reclaimIfOrphaned} must
 * reclaim it. This is a regression guard for the swap-during-grace path and the
 * opens/closes balance invariant; it does not by itself isolate the underlying
 * race (the leak it guards against was confirmed by code inspection, and the
 * reclaim is applied defensively).
 */
@DisplayName("Hot-swap during the grace window keeps PIP stream open/close balanced")
class MigrateGraceWindowReclaimTests {

    @PolicyInformationPoint(name = "reclaim")
    public static class CountingPip {
        static final AtomicInteger OPENS  = new AtomicInteger();
        static final AtomicInteger CLOSES = new AtomicInteger();

        @EnvironmentAttribute
        public Stream<Value> latest() {
            OPENS.incrementAndGet();
            val stream = new LatestSlotStream<Value>();
            stream.onClose(CLOSES::incrementAndGet);
            stream.put(Value.of("value"));
            return stream;
        }
    }

    private static SubscriptionKey envKey(String fqn) {
        val invocation = new AttributeFinderInvocation("default", fqn, List.of(), Duration.ofSeconds(1),
                Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        return new SubscriptionKey(invocation, false);
    }

    @Test
    @DisplayName("opened PIP streams are eventually all closed after a swap during the grace window")
    void whenSwapDuringGraceThenStreamsBalance() {
        CountingPip.OPENS.set(0);
        CountingPip.CLOSES.set(0);
        val broker = new PolicyInformationPointAttributeBroker(Duration.ofMillis(200));
        try {
            val handle = broker.load(new CountingPip());
            val key    = envKey("reclaim.latest");

            val subscription = broker.open("s1", Set.of(key), snapshot -> Set.of(key));
            Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> CountingPip.OPENS.get() >= 1);

            subscription.close();
            broker.swap(handle, new CountingPip());

            Awaitility.await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(CountingPip.CLOSES.get()).isEqualTo(CountingPip.OPENS.get()));
        } finally {
            broker.close();
        }
    }
}

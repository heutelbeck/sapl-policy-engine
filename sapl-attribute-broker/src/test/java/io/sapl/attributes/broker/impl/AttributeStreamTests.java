/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.impl;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.sapl.attributes.broker.api.AttributeFinderInvocation;

@Timeout(20)
class AttributeStreamTests {

    private static final AttributeFinderInvocation INVOCATION = new AttributeFinderInvocation("configId",
            "some.attribute", null, List.of(), Map.of(), Duration.ofSeconds(1L), Duration.ofSeconds(1L),
            Duration.ofMillis(50L), 20L, false);

    @Test
    void whenGetInvocationThenIncovationIsReturned() {
        final var invocation      = INVOCATION;
        final var cleanupCallback = (Consumer<AttributeStream>) a -> {};
        final var attributeStream = new AttributeStream(invocation, cleanupCallback, Duration.ofMillis(500L));

        assertThat(attributeStream.getInvocation()).isEqualTo(invocation);
    }

    @Test
    void whenWhenSubscriptionEndsThenAfterGracePeriodCleanupCallbackIsCalled() {
        final var invocation      = INVOCATION;
        final var cleanupCalled   = new AtomicInteger(0);
        final var cleanupCallback = (Consumer<AttributeStream>) a -> cleanupCalled.addAndGet(1);
        final var attributeStream = new AttributeStream(invocation, cleanupCallback, Duration.ofMillis(200L));
        attributeStream.getStream().blockFirst();
        assertThat(cleanupCalled.get()).isZero();
        await().atMost(250, MILLISECONDS).until(() -> cleanupCalled.get() == 1);
    }

    @Test
    void whenWhenSubscriptionEndsAndNewSubscriberDuringGracePeriodThenCacedValueReturnedNoCallbackAndCallbackAfterSecondSubscriberCancels() {
        final var invocation      = INVOCATION;
        final var cleanupCalled   = new AtomicInteger(0);
        final var cleanupCallback = (Consumer<AttributeStream>) a -> cleanupCalled.addAndGet(1);
        final var attributeStream = new AttributeStream(invocation, cleanupCallback, Duration.ofMillis(200L));
        final var firstValue      = attributeStream.getStream().blockFirst();
        assertThat(cleanupCalled.get()).isZero();
        final var secondValue = attributeStream.getStream().blockFirst();
        assertThat(secondValue).isEqualTo(firstValue);
        assertThat(cleanupCalled.get()).isZero();
        await().atMost(250, MILLISECONDS).untilAtomic(cleanupCalled, equalTo(1));
    }

}

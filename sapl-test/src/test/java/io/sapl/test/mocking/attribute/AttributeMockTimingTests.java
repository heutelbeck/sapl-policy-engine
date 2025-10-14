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
package io.sapl.test.mocking.attribute;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.test.SaplTestException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AttributeMockTimingTests {

    @Test
    void test() {
        final var mock = new AttributeMockTiming("test.test");
        mock.loadAttributeMockWithTiming(Duration.ofSeconds(10), Val.of(1), Val.of(2), Val.of(3), Val.of(4));

        final var invocation = new AttributeFinderInvocation("", "test.attribute", List.of(), Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.withVirtualTime(() -> mock.evaluate(invocation)).expectSubscription()
                .expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(1)).expectNoEvent(Duration.ofSeconds(10))
                .expectNext(Val.of(2)).expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(3))
                .expectNoEvent(Duration.ofSeconds(10)).expectNext(Val.of(4)).verifyComplete();

        mock.assertVerifications();
    }

    @Test
    void test_errorMessage() {
        final var mock = new AttributeMockTiming("test.test");
        assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
    }

    @Test
    void test_nullReturnValue() {
        final var mock = new AttributeMockTiming("test.test");
        mock.loadAttributeMockWithTiming(Duration.ofSeconds(1), (Val[]) null);

        final var invocation = new AttributeFinderInvocation("", "test.attribute", List.of(), Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluate(invocation));
    }

    @Test
    void test_nullTiming() {
        final var mock = new AttributeMockTiming("test.test");
        mock.loadAttributeMockWithTiming(null, Val.of(1));

        final var invocation = new AttributeFinderInvocation("", "test.attribute", List.of(), Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluate(invocation));
    }

}

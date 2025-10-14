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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AttributeMockForEntityValueAndArgumentsTests {

    private AttributeMockForEntityValueAndArguments mock;

    @BeforeEach
    void setUp() {
        mock = new AttributeMockForEntityValueAndArguments("attr.test");
    }

    @Test
    void whenMultipleArgumentMatchesThencorrectMockReturned() {
        mock.loadMockForParentValueAndArguments(whenAttributeParams(entityValue(val(true)), arguments(val(1), val(1))),
                Val.of(true));
        mock.loadMockForParentValueAndArguments(whenAttributeParams(entityValue(val(true)), arguments(val(1), val(2))),
                Val.of(false));

        final var arguments1  = List.of(Val.of(1), Val.of(1));
        final var invocation1 = new AttributeFinderInvocation("", "test.attribute", Val.TRUE, arguments1, Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);
        StepVerifier.create(mock.evaluate(invocation1)).expectNext(Val.TRUE).thenCancel().verify();

        final var arguments2  = List.of(Val.of(1), Val.of(2));
        final var invocation2 = new AttributeFinderInvocation("", "test.attribute", Val.TRUE, arguments2, Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);
        StepVerifier.create(mock.evaluate(invocation2)).expectNext(Val.FALSE).thenCancel().verify();

        mock.assertVerifications();
    }

    @Test
    void test_notMatchingMockForParentValue() {
        mock.loadMockForParentValueAndArguments(whenAttributeParams(entityValue(val(true)), arguments(val(1))),
                Val.of(true));

        final var arguments  = List.of(Val.of(1));
        final var invocation = new AttributeFinderInvocation("", "test.attribute", Val.FALSE, arguments, Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> mock.evaluate(invocation));
    }

    @Test
    void test_noMatchingMockForArguments() {
        mock.loadMockForParentValueAndArguments(whenAttributeParams(entityValue(val(true)), arguments(val(1), val(1))),
                Val.of(true));

        final var arguments  = List.of(Val.of(99), Val.of(99));
        final var invocation = new AttributeFinderInvocation("", "test.attribute", Val.TRUE, arguments, Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);
        StepVerifier.create(mock.evaluate(invocation)).expectError().verify();
    }

    @Test
    void test_argumentCountNotMatching() {
        mock.loadMockForParentValueAndArguments(whenAttributeParams(entityValue(val(true)), arguments(val(1), val(1))),
                Val.of(true));

        final var arguments  = List.of(Val.of(1));
        final var invocation = new AttributeFinderInvocation("", "test.attribute", Val.TRUE, arguments, Map.of(),
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);
        StepVerifier.create(mock.evaluate(invocation)).expectError().verify();
    }

    @Test
    void test_errorMessage() {
        assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
    }

}

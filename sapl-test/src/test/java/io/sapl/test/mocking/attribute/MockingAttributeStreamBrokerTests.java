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
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.test.SaplTestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.*;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MockingAttributeStreamBrokerTests {

    private AttributeStreamBroker        unmockedCtx;
    private MockingAttributeStreamBroker attrCtx;
    private HashMap<String, Val>         variables;

    @BeforeEach
    void setup() {
        this.unmockedCtx = Mockito.mock(AttributeStreamBroker.class);
        this.attrCtx     = new MockingAttributeStreamBroker(unmockedCtx);
        this.variables   = new HashMap<>();
    }

    @Test
    void test_dynamicMock() {
        attrCtx.markAttributeMock("foo.bar");

        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.TRUE, List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).then(() -> attrCtx.mockEmit("foo.bar", Val.of(1)))
                .expectNext(Val.of(1)).thenCancel().verify();
    }

    @Test
    void test_dynamicMock_duplicateRegistration() {
        attrCtx.markAttributeMock("foo.bar");
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo.bar"));
    }

    @Test
    void test_dynamicMock_mockEmitCalledForInvalidFullName() {
        attrCtx.loadAttributeMock("test.test", Duration.ofSeconds(10), Val.of(1), Val.of(2));
        final var valOne = Val.of(1);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.mockEmit("test.test", valOne));
    }

    @Test
    void test_dynamicMock_ForEnvironmentAttribute() {
        attrCtx.markAttributeMock("foo.bar");
        final var invocation = new AttributeFinderInvocation("", "foo.bar", List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).then(() -> attrCtx.mockEmit("foo.bar", Val.of(1)))
                .expectNext(Val.of(1)).thenCancel().verify();
    }

    @Test
    void test_timingMock() {
        attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));

        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.of(1), List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.withVirtualTime(() -> attrCtx.attributeStream(invocation)).thenAwait(Duration.ofSeconds(10))
                .expectNext(Val.of(1)).thenAwait(Duration.ofSeconds(10)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_timingMock_duplicateRegistration() {
        attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));
        final var tenSeconds = Duration.ofSeconds(10L);
        final var valOne     = Val.of(1);
        final var valTwo     = Val.of(2);
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> attrCtx.loadAttributeMock("foo.bar", tenSeconds, valOne, valTwo));
    }

    @Test
    void test_timingMock_ForEnvironmentAttribute() {
        attrCtx.loadAttributeMock("foo.bar", Duration.ofSeconds(10), Val.of(1), Val.of(2));

        final var invocation = new AttributeFinderInvocation("", "foo.bar", List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.withVirtualTime(() -> attrCtx.attributeStream(invocation)).thenAwait(Duration.ofSeconds(10))
                .expectNext(Val.of(1)).thenAwait(Duration.ofSeconds(10)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValue() {
        attrCtx.loadAttributeMockForEntityValue("foo.bar", entityValue(val(1)), Val.of(2));

        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.of(1), List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValue_duplicateRegistration() {
        attrCtx.loadAttributeMockForEntityValue("foo.bar", entityValue(val(1)), Val.of(2));
        attrCtx.loadAttributeMockForEntityValue("foo.bar", entityValue(val(2)), Val.of(3));

        final var invocation1 = new AttributeFinderInvocation("", "foo.bar", Val.of(1), List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation1)).expectNext(Val.of(2)).verifyComplete();

        final var invocation2 = new AttributeFinderInvocation("", "foo.bar", Val.of(2), List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation2)).expectNext(Val.of(3)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValue_registeredButWrongType() {
        attrCtx.markAttributeMock("foo.bar");
        final var parent = entityValue(val(1));
        final var valTwo = Val.of(2);
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> attrCtx.loadAttributeMockForEntityValue("foo.bar", parent, valTwo));
    }

    @Test
    void test_ForParentValue_ForEnvironmentAttribute() {
        attrCtx.loadAttributeMockForEntityValue("foo.bar", entityValue(is(Val.UNDEFINED)), Val.of(2));
        final var invocation = new AttributeFinderInvocation("", "foo.bar", List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValueAndArguments() {
        attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
                whenAttributeParams(entityValue(val(1)), arguments(val(true))), Val.of(2));
        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.of(1), List.of(Val.TRUE), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValueAndArguments_duplicateRegistration() {
        attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
                whenAttributeParams(entityValue(val(1)), arguments(val(true))), Val.of(0));
        attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
                whenAttributeParams(entityValue(val(1)), arguments(val(false))), Val.of(1));

        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.of(1), List.of(Val.TRUE), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).expectNext(Val.of(0)).verifyComplete();
    }

    @Test
    void test_loadAttributeMockForParentValueAndArguments_registeredButWrongType() {
        attrCtx.markAttributeMock("foo.bar");
        final var parent     = entityValue(val(1));
        final var valTwo     = Val.of(2);
        final var arguments  = arguments(val(true));
        final var whenParams = whenAttributeParams(parent, arguments);
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar", whenParams, valTwo));
    }

    @Test
    void test_ForParentValueAndArguments_ForEnvironmentAttribute() {
        attrCtx.loadAttributeMockForParentValueAndArguments("foo.bar",
                whenAttributeParams(entityValue(is(Val.UNDEFINED)), arguments(val(true))), Val.of(2));

        final var invocation = new AttributeFinderInvocation("", "foo.bar", Val.UNDEFINED, List.of(Val.TRUE), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(attrCtx.attributeStream(invocation)).expectNext(Val.of(2)).verifyComplete();
    }

    @Test
    void test_invalidFullName() {
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo"));
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> attrCtx.markAttributeMock("foo.bar.xxx"));
    }

    @Test
    void test_ReturnUnmockedEvaluation() {
        when(unmockedCtx.attributeStream(any())).thenReturn(Val.fluxOf("abc"));
        final var invocation = new AttributeFinderInvocation("", "foo.bar", List.of(), variables,
                Duration.ofSeconds(1L), Duration.ofSeconds(1L), Duration.ofSeconds(1L), 1, true);

        StepVerifier.create(this.attrCtx.attributeStream(invocation)).expectNext(Val.of("abc")).expectComplete()
                .verify();
    }

    @Test
    void test_mockEmit_UnmockedAttribute() {
        final var anUnmockedCtx = new CachingAttributeStreamBroker();
        final var ctx           = new MockingAttributeStreamBroker(anUnmockedCtx);
        final var valOne        = Val.of(1);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.mockEmit("foo.bar", valOne));
    }

}

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
package io.sapl.test;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.args;
import static io.sapl.test.Matchers.eq;
import static io.sapl.test.verification.Times.never;
import static io.sapl.test.verification.Times.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.NoMatchingFunctionException;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.functions.DefaultFunctionBroker;

import lombok.val;

@DisplayName("Function binding through scenario mocks")
class FunctionBindingTests {

    @Test
    @DisplayName("mock-only names are declared by arity without inspecting matchers at bind time")
    void whenMockHasNoLibraryThenBindingDefersMatchersAndReturnValuesUntilEachCall() {
        val broker  = new MockingFunctionBroker();
        val matched = new AtomicInteger();
        broker.mock("office.local", args(new MockingFunctionBroker.ArgumentMatcher.Predicated(value -> {
            matched.incrementAndGet();
            return true;
        })), Value.of(1), Value.of(2));

        val bound = broker.bindFunction("office.local", 1);
        assertThat(matched).hasValue(0);
        assertThat(broker.getInvocations()).isEmpty();
        val invocation = new FunctionInvocation("office.local", List.of(Value.of("reader")));
        assertThat(bound.evaluate(invocation)).isEqualTo(Value.of(1));
        assertThat(bound.evaluate(invocation)).isEqualTo(Value.of(2));
        assertThat(matched).hasValue(2);
        broker.verify("office.local", args(any()), times(2));
    }

    @Test
    @DisplayName("verification and a different mock arity do not declare the requested call")
    void whenOnlyVerificationOrAnotherArityExistsThenBindingFails() {
        val broker = new MockingFunctionBroker();
        broker.verify("office.local", args(any()), never());

        assertThatThrownBy(() -> broker.bindFunction("office.local", 1))
                .isInstanceOf(NoMatchingFunctionException.class);
        broker.mock("office.local", args(any()), Value.TRUE);
        assertThatThrownBy(() -> broker.bindFunction("office.local", 0))
                .isInstanceOf(NoMatchingFunctionException.class);
    }

    @Test
    @DisplayName("a matcher miss still reaches the real bound delegate and its result cache")
    void whenMockArgumentsDoNotMatchThenTheRealDelegateRemainsAvailable() {
        val library  = new OfficeLibrary();
        val delegate = new DefaultFunctionBroker();
        delegate.load(library);
        val broker = new MockingFunctionBroker(delegate);
        broker.mock("office.echo", args(eq(Value.of("override"))), Value.of("mock"));
        val bound    = broker.bindFunction("office.echo", 1);
        val ordinary = new FunctionInvocation("office.echo", List.of(Value.of("reader")));
        val mocked   = new FunctionInvocation("office.echo", List.of(Value.of("override")));

        assertThat(library.bodies).hasValue(0);
        assertThat(bound.evaluate(ordinary)).isEqualTo(Value.of("reader"));
        assertThat(bound.evaluate(ordinary)).isEqualTo(Value.of("reader"));
        assertThat(bound.evaluate(mocked)).isEqualTo(Value.of("mock"));
        assertThat(bound.evaluate(mocked)).isEqualTo(Value.of("mock"));
        assertThat(library.bodies).hasValue(1);
        broker.verify("office.echo", args(any()), times(4));
    }

    @Test
    @DisplayName("a declared mock does not hide an ambiguous real delegate")
    void whenDelegateIsAmbiguousThenMockBindingStillFails() {
        val delegate = new DefaultFunctionBroker();
        delegate.load(new OfficeLibrary());
        val broker = new MockingFunctionBroker(delegate);
        broker.mock("office.choice", args(any(), any()), Value.TRUE);

        assertThatThrownBy(() -> broker.bindFunction("office.choice", 2)).isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(NoMatchingFunctionException.class);
        assertThat(broker.getInvocations()).isEmpty();
    }

    @ParameterizedTest(name = "integration fixture {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("a declared mock without a real library compiles and runs in either fixture")
    void whenScenarioDeclaresAMockOnlyFunctionThenItsPolicyCanCallIt(boolean integration) {
        final SaplTestFixture fixture;
        if (integration) {
            fixture = SaplTestFixture.createIntegrationTest().withCombiningAlgorithm(CombiningAlgorithm.DEFAULT);
        } else {
            fixture = SaplTestFixture.createSingleTest();
        }
        fixture.withPolicy("policy \"office\" permit office.gate(subject) && office.gate(subject);")
                .givenFunction("office.gate", args(any()), Value.TRUE)
                .whenDecide(AuthorizationSubscription.of("reader", "enter", "desk")).expectPermit().verify();
    }

    @FunctionLibrary(name = "office", description = "Ordinary bound delegate functions.")
    public static final class OfficeLibrary {
        private final AtomicInteger bodies = new AtomicInteger();

        @Function(docs = "Returns a supplied value.")
        public Value echo(Value value) {
            bodies.incrementAndGet();
            return value;
        }

        @Function(name = "choice", docs = "Accepts zero or more values.")
        public static Value anyArguments(Value... values) {
            return Value.of(values.length);
        }

        @Function(name = "choice", docs = "Accepts one or more values.")
        public static Value oneOrMoreArguments(Value first, Value... values) {
            return Value.ofArray(first, Value.of(values.length));
        }
    }
}

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
package io.sapl.test;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.anyText;
import static io.sapl.test.Matchers.args;
import static io.sapl.test.Matchers.eq;
import static io.sapl.test.verification.Times.atLeast;
import static io.sapl.test.verification.Times.once;
import static io.sapl.test.verification.Times.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import io.sapl.test.verification.MockVerificationException;
import io.sapl.test.verification.Times;

class MockingAttributeBrokerTests {

    private static final String   TEST_CONFIG_ID  = "test-config";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_POLL    = Duration.ofSeconds(1);
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(100);
    private static final long     DEFAULT_RETRIES = 3;
    private static final boolean  DEFAULT_FRESH   = false;

    private AttributeBroker        delegate;
    private MockingAttributeBroker broker;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(AttributeBroker.class);
        broker   = new MockingAttributeBroker(delegate);
    }

    // ========== Helper Methods ==========

    private static AttributeFinderInvocation envInvocation(String name, List<Value> arguments) {
        return new AttributeFinderInvocation(TEST_CONFIG_ID, name, arguments, Map.of(), DEFAULT_TIMEOUT, DEFAULT_POLL,
                DEFAULT_BACKOFF, DEFAULT_RETRIES, DEFAULT_FRESH);
    }

    private static AttributeFinderInvocation entityInvocation(String name, Value entity, List<Value> arguments) {
        return new AttributeFinderInvocation(TEST_CONFIG_ID, name, entity, arguments, Map.of(), DEFAULT_TIMEOUT,
                DEFAULT_POLL, DEFAULT_BACKOFF, DEFAULT_RETRIES, DEFAULT_FRESH);
    }

    // ========== Environment Attribute - Initial Value Tests ==========

    @Test
    void whenEnvironmentAttributeWithInitialValue_thenEmitsOnSubscription() {
        var initialValue = Value.of("2025-01-06T10:00:00Z");
        broker.mockEnvironmentAttribute("timeNow", "time.now", args(), initialValue);

        var invocation = envInvocation("time.now", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).expectNext(initialValue).verifyComplete();
    }

    @Test
    void whenEnvironmentAttributeWithInitialValueAndArgs_thenMatchesArity() {
        var initialValue = Value.of("MONDAY");
        broker.mockEnvironmentAttribute("dayOfWeek", "time.dayOfWeek", args(any()), initialValue);

        var invocation = envInvocation("time.dayOfWeek", List.of(Value.of("2025-01-06")));
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).expectNext(initialValue).verifyComplete();
    }

    @Test
    void whenEnvironmentAttributeWithInitialValue_thenContinuesAfterInitial() {
        var initialValue = Value.of("first");
        broker.mockEnvironmentAttribute("streamTest", "stream.test", args(), initialValue);

        var invocation = envInvocation("stream.test", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(3)).expectNext(initialValue)
                .then(() -> broker.emit("streamTest", Value.of("second"))).expectNext(Value.of("second"))
                .then(() -> broker.emit("streamTest", Value.of("third"))).expectNext(Value.of("third"))
                .verifyComplete();
    }

    // ========== Environment Attribute - Streaming Only Tests ==========

    @Test
    void whenEnvironmentAttributeStreamingOnly_thenWaitsForEmit() {
        broker.mockEnvironmentAttribute("streamValues", "stream.values", args());

        var invocation = envInvocation("stream.values", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(2)).then(() -> broker.emit("streamValues", Value.of("emitted1")))
                .expectNext(Value.of("emitted1")).then(() -> broker.emit("streamValues", Value.of("emitted2")))
                .expectNext(Value.of("emitted2")).verifyComplete();
    }

    @Test
    void whenEnvironmentAttributeEmitBeforeSubscription_thenCachesLastValue() {
        broker.mockEnvironmentAttribute("bufferedStream", "buffered.stream", args());

        broker.emit("bufferedStream", Value.of("pre-emit-1"));
        broker.emit("bufferedStream", Value.of("pre-emit-2"));

        var invocation = envInvocation("buffered.stream", List.of());
        var stream     = broker.attributeStream(invocation);

        // With replay(1), only the last emitted value is cached for late subscribers
        StepVerifier.create(stream.take(1)).expectNext(Value.of("pre-emit-2")).verifyComplete();
    }

    @Test
    void whenEnvironmentAttributeWithInitialThenEmit_thenCachesLatestValue() {
        var initialValue = Value.of("initial");
        broker.mockEnvironmentAttribute("combinedStream", "combined.stream", args(), initialValue);

        // This emit replaces the initial value in the cache (replay limit = 1)
        broker.emit("combinedStream", Value.of("emitted-after"));

        var invocation = envInvocation("combined.stream", List.of());
        var stream     = broker.attributeStream(invocation);

        // Late subscriber gets only the last cached value
        StepVerifier.create(stream.take(1)).expectNext(Value.of("emitted-after")).verifyComplete();
    }

    // ========== Environment Attribute - Arity Matching Tests ==========

    @Test
    void whenEnvironmentAttributeDifferentArities_thenMatchesCorrectMock() {
        broker.mockEnvironmentAttribute("multiArity0", "multi.arity", args(), Value.of("zero-args"));
        broker.mockEnvironmentAttribute("multiArity1", "multi.arity", args(any()), Value.of("one-arg"));
        broker.mockEnvironmentAttribute("multiArity2", "multi.arity", args(any(), any()), Value.of("two-args"));

        var invocation0 = envInvocation("multi.arity", List.of());
        var invocation1 = envInvocation("multi.arity", List.of(Value.of("a")));
        var invocation2 = envInvocation("multi.arity", List.of(Value.of("a"), Value.of("b")));

        StepVerifier.create(broker.attributeStream(invocation0).take(1)).expectNext(Value.of("zero-args"))
                .verifyComplete();

        StepVerifier.create(broker.attributeStream(invocation1).take(1)).expectNext(Value.of("one-arg"))
                .verifyComplete();

        StepVerifier.create(broker.attributeStream(invocation2).take(1)).expectNext(Value.of("two-args"))
                .verifyComplete();
    }

    // ========== Regular Attribute - Entity Matching Tests ==========

    @Test
    void whenRegularAttributeWithEntityMatch_thenEmitsOnSubscription() {
        var entityMatcher = eq(Value.of("adminUser"));
        broker.mockAttribute("adminRoles", "user.roles", entityMatcher, args(), Value.of("[\"ADMIN\"]"));

        var invocation = entityInvocation("user.roles", Value.of("adminUser"), List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).expectNext(Value.of("[\"ADMIN\"]")).verifyComplete();
    }

    @Test
    void whenRegularAttributeDifferentEntities_thenMatchesCorrectMock() {
        var adminMatcher = eq(Value.of("admin"));
        var guestMatcher = eq(Value.of("guest"));

        broker.mockAttribute("adminRolesMock", "user.roles", adminMatcher, args(), Value.of("[\"ADMIN\",\"USER\"]"));
        broker.mockAttribute("guestRolesMock", "user.roles", guestMatcher, args(), Value.of("[\"GUEST\"]"));

        var adminInvocation = entityInvocation("user.roles", Value.of("admin"), List.of());
        var guestInvocation = entityInvocation("user.roles", Value.of("guest"), List.of());

        StepVerifier.create(broker.attributeStream(adminInvocation).take(1))
                .expectNext(Value.of("[\"ADMIN\",\"USER\"]")).verifyComplete();

        StepVerifier.create(broker.attributeStream(guestInvocation).take(1)).expectNext(Value.of("[\"GUEST\"]"))
                .verifyComplete();
    }

    @Test
    void whenRegularAttributeWithAnyEntityMatcher_thenMatchesAllEntities() {
        broker.mockAttribute("genericAttr", "generic.attr", any(), args(), Value.of("matched-any"));

        var invocation1 = entityInvocation("generic.attr", Value.of("entity1"), List.of());
        var invocation2 = entityInvocation("generic.attr", Value.of("entity2"), List.of());

        StepVerifier.create(broker.attributeStream(invocation1).take(1)).expectNext(Value.of("matched-any"))
                .verifyComplete();

        StepVerifier.create(broker.attributeStream(invocation2).take(1)).expectNext(Value.of("matched-any"))
                .verifyComplete();
    }

    @Test
    void whenRegularAttributeEmitToSpecificEntity_thenEmitsCorrectly() {
        var adminMatcher = eq(Value.of("admin"));
        broker.mockAttribute("userStatus", "user.status", adminMatcher, args());

        var invocation = entityInvocation("user.status", Value.of("admin"), List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).then(() -> broker.emit("userStatus", Value.of("ACTIVE")))
                .expectNext(Value.of("ACTIVE")).verifyComplete();
    }

    // ========== Specificity Tests ==========

    @Test
    void whenMultipleMocksMatch_thenMostSpecificWins() {
        broker.mockAttribute("anyAny", "specific.test", any(), args(any()), Value.of("any-any"));
        broker.mockAttribute("exactAny", "specific.test", eq(Value.of("special")), args(any()), Value.of("exact-any"));

        var invocation = entityInvocation("specific.test", Value.of("special"), List.of(Value.of("arg")));

        StepVerifier.create(broker.attributeStream(invocation).take(1)).expectNext(Value.of("exact-any"))
                .verifyComplete();
    }

    @Test
    void whenMultipleMocksMatchByArgs_thenMostSpecificArgWins() {
        broker.mockEnvironmentAttribute("argsAny", "args.test", args(any()), Value.of("any-matcher"));
        broker.mockEnvironmentAttribute("argsExact", "args.test", args(eq(Value.of("exact"))),
                Value.of("exact-matcher"));

        var exactInvocation = envInvocation("args.test", List.of(Value.of("exact")));
        var otherInvocation = envInvocation("args.test", List.of(Value.of("other")));

        StepVerifier.create(broker.attributeStream(exactInvocation).take(1)).expectNext(Value.of("exact-matcher"))
                .verifyComplete();

        StepVerifier.create(broker.attributeStream(otherInvocation).take(1)).expectNext(Value.of("any-matcher"))
                .verifyComplete();
    }

    @Test
    void whenCombinedEntityAndArgSpecificity_thenSumDeterminesWinner() {
        broker.mockAttribute("anyExact", "combo.test", any(), args(eq(Value.of("exactArg"))), Value.of("any-exact"));
        broker.mockAttribute("exactAny", "combo.test", eq(Value.of("exactEntity")), args(any()), Value.of("exact-any"));
        broker.mockAttribute("exactExact", "combo.test", eq(Value.of("exactEntity")), args(eq(Value.of("exactArg"))),
                Value.of("exact-exact"));

        var fullMatchInvocation = entityInvocation("combo.test", Value.of("exactEntity"),
                List.of(Value.of("exactArg")));

        StepVerifier.create(broker.attributeStream(fullMatchInvocation).take(1)).expectNext(Value.of("exact-exact"))
                .verifyComplete();
    }

    // ========== Delegation Tests ==========

    @Test
    void whenNoMockRegistered_thenDelegatesToUnderlying() {
        var delegateResponse = Value.of("from-delegate");
        when(delegate.attributeStream(argThat(inv -> "delegate.attr".equals(inv.attributeName()))))
                .thenReturn(Flux.just(delegateResponse));

        var invocation = envInvocation("delegate.attr", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).expectNext(delegateResponse).verifyComplete();
    }

    @Test
    void whenMockArityDoesNotMatch_thenDelegatesToUnderlying() {
        broker.mockEnvironmentAttribute("partialMock", "partial.mock", args(any()), Value.of("mocked"));

        var delegateResponse = Value.of("from-delegate");
        when(delegate.attributeStream(
                argThat(inv -> "partial.mock".equals(inv.attributeName()) && inv.arguments().isEmpty())))
                .thenReturn(Flux.just(delegateResponse));

        var invocation = envInvocation("partial.mock", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(1)).expectNext(delegateResponse).verifyComplete();
    }

    @Test
    void whenGetRegisteredLibraries_thenDelegatesToUnderlying() {
        var expectedLibraries = List.<Class<?>>of(String.class);
        when(delegate.getRegisteredLibraries()).thenReturn(expectedLibraries);

        assertThat(broker.getRegisteredLibraries()).isSameAs(expectedLibraries);
    }

    // ========== Error Handling Tests ==========

    @Test
    void whenEmitToNonExistentMockId_thenThrows() {
        assertThatThrownBy(() -> broker.emit("nonexistent", Value.of("value")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No mock registered with id 'nonexistent'");
    }

    @Test
    void whenMockIdIsNull_thenThrows() {
        assertThatThrownBy(() -> broker.mockEnvironmentAttribute(null, "test.attr", args()))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("mockId");
    }

    @Test
    void whenMockIdIsBlank_thenThrows() {
        assertThatThrownBy(() -> broker.mockEnvironmentAttribute("  ", "test.attr", args()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MockId must not be blank");
    }

    @Test
    void whenDuplicateMockId_thenThrows() {
        broker.mockEnvironmentAttribute("duplicateId", "first.attr", args());

        assertThatThrownBy(() -> broker.mockEnvironmentAttribute("duplicateId", "second.attr", args()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'duplicateId' is already registered");
    }

    @Test
    void whenEnvironmentAttributeWithInvalidArguments_thenThrows() {
        var invalidParams = new SaplTestFixture.Parameters() {};

        assertThatThrownBy(() -> broker.mockEnvironmentAttribute("test", "test.attr", invalidParams))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }

    @Test
    void whenRegularAttributeWithInvalidArguments_thenThrows() {
        var invalidParams = new SaplTestFixture.Parameters() {};

        assertThatThrownBy(() -> broker.mockAttribute("test", "test.attr", any(), invalidParams))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }

    // ========== Query Method Tests ==========

    @Test
    void whenHasMockForRegisteredId_thenReturnsTrue() {
        broker.mockEnvironmentAttribute("registeredMock", "registered.attr", args(), Value.of("value"));

        assertThat(broker.hasMock("registeredMock")).isTrue();
    }

    @Test
    void whenHasMockForUnregisteredId_thenReturnsFalse() {
        assertThat(broker.hasMock("unregistered")).isFalse();
    }

    @Test
    void whenHasMockForAttributeRegistered_thenReturnsTrue() {
        broker.mockEnvironmentAttribute("someMock", "registered.attr", args(), Value.of("value"));

        assertThat(broker.hasMockForAttribute("registered.attr")).isTrue();
    }

    @Test
    void whenHasMockForAttributeUnregistered_thenReturnsFalse() {
        assertThat(broker.hasMockForAttribute("unregistered.attr")).isFalse();
    }

    @Test
    void whenClearAllMocks_thenNoMocksRemain() {
        broker.mockEnvironmentAttribute("mock1", "attr1", args(), Value.of("v1"));
        broker.mockEnvironmentAttribute("mock2", "attr2", args(), Value.of("v2"));

        broker.clearAllMocks();

        assertThat(broker.hasMock("mock1")).isFalse();
        assertThat(broker.hasMock("mock2")).isFalse();
        assertThat(broker.hasMockForAttribute("attr1")).isFalse();
        assertThat(broker.hasMockForAttribute("attr2")).isFalse();
    }

    // ========== Multiple Subscription Tests ==========

    @Test
    void whenMultipleSubscribers_thenAllReceiveEmissions() {
        var initialValue = Value.of("initial");
        broker.mockEnvironmentAttribute("multiSub", "multi.sub", args(), initialValue);

        var invocation = envInvocation("multi.sub", List.of());
        var stream1    = broker.attributeStream(invocation);
        var stream2    = broker.attributeStream(invocation);

        StepVerifier.create(stream1.take(1)).expectNext(initialValue).verifyComplete();

        StepVerifier.create(stream2.take(1)).expectNext(initialValue).verifyComplete();
    }

    @Test
    void whenEmitAfterMultipleSubscriptions_thenAllReceive() {
        broker.mockEnvironmentAttribute("broadcast", "test.broadcast", args(), Value.of("initial"));

        var invocation = envInvocation("test.broadcast", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(2)).expectNext(Value.of("initial"))
                .then(() -> broker.emit("broadcast", Value.of("emitted"))).expectNext(Value.of("emitted"))
                .verifyComplete();
    }

    @Test
    void whenSequentialSubscriptionsAndEmissions_thenLateSubscriberGetsCachedValue() {
        var x = Value.of("x");
        var y = Value.of("y");
        var z = Value.of("z");

        broker.mockEnvironmentAttribute("sequential", "test.sequential", args(), x);

        var invocation = envInvocation("test.sequential", List.of());

        // First subscriber: collects values
        var subscriber1Values = Collections.synchronizedList(new ArrayList<Value>());
        var stream1           = broker.attributeStream(invocation);
        var disposable1       = stream1.subscribe(subscriber1Values::add);

        // First subscriber gets cached initial value x
        assertThat(subscriber1Values).containsExactly(x);

        // Emit y - first subscriber gets it, cache now contains y
        broker.emit("sequential", y);
        assertThat(subscriber1Values).containsExactly(x, y);

        // Second subscriber joins - gets cached y (NOT initial x)
        var subscriber2Values = Collections.synchronizedList(new ArrayList<Value>());
        var stream2           = broker.attributeStream(invocation);
        var disposable2       = stream2.subscribe(subscriber2Values::add);

        // With replay(1), late subscriber gets the last cached value (y)
        assertThat(subscriber2Values).containsExactly(y);

        // Emit z - BOTH subscribers get it
        broker.emit("sequential", z);

        assertThat(subscriber1Values).containsExactly(x, y, z);
        assertThat(subscriber2Values).containsExactly(y, z);

        // Cleanup
        disposable1.dispose();
        disposable2.dispose();
    }

    // ========== MockId Uniqueness Tests ==========

    @Test
    void whenSameAttributeDifferentMockIds_thenBothEmitIndependently() {
        broker.mockEnvironmentAttribute("mondayMock", "time.dayOfWeek", args(eq(Value.of("2025-01-06"))),
                Value.of("MONDAY"));
        broker.mockEnvironmentAttribute("tuesdayMock", "time.dayOfWeek", args(eq(Value.of("2025-01-07"))),
                Value.of("TUESDAY"));

        var mondayInvocation  = envInvocation("time.dayOfWeek", List.of(Value.of("2025-01-06")));
        var tuesdayInvocation = envInvocation("time.dayOfWeek", List.of(Value.of("2025-01-07")));

        // Subscribe to both
        var mondayValues  = Collections.synchronizedList(new ArrayList<Value>());
        var tuesdayValues = Collections.synchronizedList(new ArrayList<Value>());

        var mondayDisposable  = broker.attributeStream(mondayInvocation).subscribe(mondayValues::add);
        var tuesdayDisposable = broker.attributeStream(tuesdayInvocation).subscribe(tuesdayValues::add);

        assertThat(mondayValues).containsExactly(Value.of("MONDAY"));
        assertThat(tuesdayValues).containsExactly(Value.of("TUESDAY"));

        // Emit to monday mock specifically
        broker.emit("mondayMock", Value.of("HOLIDAY"));
        assertThat(mondayValues).containsExactly(Value.of("MONDAY"), Value.of("HOLIDAY"));
        assertThat(tuesdayValues).containsExactly(Value.of("TUESDAY")); // Unchanged

        // Emit to tuesday mock specifically
        broker.emit("tuesdayMock", Value.of("WORKDAY"));
        assertThat(mondayValues).containsExactly(Value.of("MONDAY"), Value.of("HOLIDAY")); // Unchanged
        assertThat(tuesdayValues).containsExactly(Value.of("TUESDAY"), Value.of("WORKDAY"));

        mondayDisposable.dispose();
        tuesdayDisposable.dispose();
    }

    // ========== Complex Scenario Tests ==========

    @Test
    void whenMixedEnvironmentAndRegularAttributes_thenBothWork() {
        broker.mockEnvironmentAttribute("timeNow", "time.now", args(), Value.of("2025-01-06T10:00:00Z"));
        broker.mockAttribute("aliceDept", "user.department", eq(Value.of("alice")), args(), Value.of("Engineering"));

        var timeInvocation = envInvocation("time.now", List.of());
        var userInvocation = entityInvocation("user.department", Value.of("alice"), List.of());

        StepVerifier.create(broker.attributeStream(timeInvocation).take(1)).expectNext(Value.of("2025-01-06T10:00:00Z"))
                .verifyComplete();

        StepVerifier.create(broker.attributeStream(userInvocation).take(1)).expectNext(Value.of("Engineering"))
                .verifyComplete();
    }

    @Test
    void whenStreamingScenarioWithMultipleEmissions_thenAllEmissionsReceived() {
        broker.mockEnvironmentAttribute("counter", "test.counter", args());

        var invocation = envInvocation("test.counter", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(5)).then(() -> broker.emit("counter", Value.of(1))).expectNext(Value.of(1))
                .then(() -> broker.emit("counter", Value.of(2))).expectNext(Value.of(2))
                .then(() -> broker.emit("counter", Value.of(3))).expectNext(Value.of(3))
                .then(() -> broker.emit("counter", Value.of(4))).expectNext(Value.of(4))
                .then(() -> broker.emit("counter", Value.of(5))).expectNext(Value.of(5)).verifyComplete();
    }

    @Test
    void whenPredicateMatcherOnEntity_thenMatchesBasedOnPredicate() {
        broker.mockAttribute("textLength", "text.length", anyText(), args(), Value.of("text-entity"));

        var textInvocation   = entityInvocation("text.length", Value.of("hello"), List.of());
        var numberInvocation = entityInvocation("text.length", Value.of(42), List.of());

        StepVerifier.create(broker.attributeStream(textInvocation).take(1)).expectNext(Value.of("text-entity"))
                .verifyComplete();

        when(delegate.attributeStream(argThat(inv -> "text.length".equals(inv.attributeName()))))
                .thenReturn(Flux.just(Value.of("delegated")));

        StepVerifier.create(broker.attributeStream(numberInvocation).take(1)).expectNext(Value.of("delegated"))
                .verifyComplete();
    }

    @Test
    void whenStreamStaysOpenAfterInitialEmission_thenCanReceiveMore() {
        broker.mockEnvironmentAttribute("longRunning", "long.running", args(), Value.of("start"));

        var invocation = envInvocation("long.running", List.of());
        var stream     = broker.attributeStream(invocation);

        StepVerifier.create(stream.take(Duration.ofMillis(500))).expectNext(Value.of("start"))
                .then(() -> broker.emit("longRunning", Value.of("update1"))).expectNext(Value.of("update1"))
                .then(() -> broker.emit("longRunning", Value.of("update2"))).expectNext(Value.of("update2"))
                .thenCancel().verify();
    }

    // ========== Invocation Recording Tests ==========

    @Test
    void whenAttributeInvoked_thenRecordsInvocation() {
        broker.mockEnvironmentAttribute("recordTest", "record.test", args(), Value.of("value"));

        var invocation = envInvocation("record.test", List.of());
        broker.attributeStream(invocation).blockFirst();

        assertThat(broker.getInvocations()).hasSize(1);
        assertThat(broker.getInvocations().getFirst().attributeName()).isEqualTo("record.test");
    }

    @Test
    void whenMultipleAttributeInvocations_thenRecordsAll() {
        broker.mockEnvironmentAttribute("multi1", "multi.test", args(), Value.of("v1"));

        var invocation = envInvocation("multi.test", List.of());
        broker.attributeStream(invocation).blockFirst();
        broker.attributeStream(invocation).blockFirst();
        broker.attributeStream(invocation).blockFirst();

        assertThat(broker.getInvocations()).hasSize(3);
    }

    @Test
    void whenAttributeInvocationRecorded_thenContainsArguments() {
        broker.mockEnvironmentAttribute("argsRecord", "args.record", args(any(), any()), Value.of("result"));

        var invocation = envInvocation("args.record", List.of(Value.of("arg1"), Value.of("arg2")));
        broker.attributeStream(invocation).blockFirst();

        var recorded = broker.getInvocations().getFirst();
        assertThat(recorded.arguments()).containsExactly(Value.of("arg1"), Value.of("arg2"));
    }

    @Test
    void whenEntityAttributeInvocationRecorded_thenContainsEntity() {
        var entity = Value.of("testEntity");
        broker.mockAttribute("entityRecord", "entity.record", eq(entity), args(), Value.of("result"));

        var invocation = entityInvocation("entity.record", entity, List.of());
        broker.attributeStream(invocation).blockFirst();

        var recorded = broker.getInvocations().getFirst();
        assertThat(recorded.entity()).isEqualTo(entity);
        assertThat(recorded.isEnvironmentAttribute()).isFalse();
    }

    @Test
    void whenEnvironmentAttributeInvocationRecorded_thenEntityIsNull() {
        broker.mockEnvironmentAttribute("envRecord", "env.record", args(), Value.of("result"));

        var invocation = envInvocation("env.record", List.of());
        broker.attributeStream(invocation).blockFirst();

        var recorded = broker.getInvocations().getFirst();
        assertThat(recorded.entity()).isNull();
        assertThat(recorded.isEnvironmentAttribute()).isTrue();
    }

    @Test
    void whenInvocationRecorded_thenHasSequenceNumber() {
        broker.mockEnvironmentAttribute("seq1", "seq.test1", args(), Value.of("v1"));
        broker.mockEnvironmentAttribute("seq2", "seq.test2", args(), Value.of("v2"));

        broker.attributeStream(envInvocation("seq.test1", List.of())).blockFirst();
        broker.attributeStream(envInvocation("seq.test2", List.of())).blockFirst();
        broker.attributeStream(envInvocation("seq.test1", List.of())).blockFirst();

        var invocations = broker.getInvocations();
        assertThat(invocations.get(0).sequenceNumber()).isEqualTo(0);
        assertThat(invocations.get(1).sequenceNumber()).isEqualTo(1);
        assertThat(invocations.get(2).sequenceNumber()).isEqualTo(2);
    }

    @Test
    void whenGetInvocationsForAttribute_thenFiltersCorrectly() {
        broker.mockEnvironmentAttribute("filter1", "filter.attr1", args(), Value.of("v1"));
        broker.mockEnvironmentAttribute("filter2", "filter.attr2", args(), Value.of("v2"));

        broker.attributeStream(envInvocation("filter.attr1", List.of())).blockFirst();
        broker.attributeStream(envInvocation("filter.attr2", List.of())).blockFirst();
        broker.attributeStream(envInvocation("filter.attr1", List.of())).blockFirst();

        assertThat(broker.getInvocations("filter.attr1")).hasSize(2);
        assertThat(broker.getInvocations("filter.attr2")).hasSize(1);
    }

    @Test
    void whenClearInvocations_thenKeepsMocksButClearsRecords() {
        broker.mockEnvironmentAttribute("clearInv", "clear.inv", args(), Value.of("result"));

        broker.attributeStream(envInvocation("clear.inv", List.of())).blockFirst();
        assertThat(broker.getInvocations()).hasSize(1);

        broker.clearInvocations();

        assertThat(broker.getInvocations()).isEmpty();
        assertThat(broker.hasMock("clearInv")).isTrue();
    }

    @Test
    void whenClearAllMocks_thenClearsInvocationsToo() {
        broker.mockEnvironmentAttribute("clearAll", "clear.all", args(), Value.of("result"));

        broker.attributeStream(envInvocation("clear.all", List.of())).blockFirst();

        broker.clearAllMocks();

        assertThat(broker.getInvocations()).isEmpty();
    }

    // ========== Verification Tests ==========

    @Test
    void whenVerifyEnvironmentAttributeOnce_thenPassesIfCalledOnce() {
        broker.mockEnvironmentAttribute("verifyOnce", "verify.once", args(), Value.of("result"));

        broker.attributeStream(envInvocation("verify.once", List.of())).blockFirst();

        broker.verifyEnvironmentAttribute("verify.once", args(), once());
    }

    @Test
    void whenVerifyEnvironmentAttributeOnce_thenFailsIfNeverCalled() {
        broker.mockEnvironmentAttribute("verifyFail", "verify.fail", args(), Value.of("result"));

        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("verify.fail", args(), once()))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("exactly once")
                .hasMessageContaining("invoked 0 time(s)");
    }

    @Test
    void whenVerifyEnvironmentAttributeOnce_thenFailsIfCalledMultipleTimes() {
        broker.mockEnvironmentAttribute("verifyMulti", "verify.multi", args(), Value.of("result"));

        broker.attributeStream(envInvocation("verify.multi", List.of())).blockFirst();
        broker.attributeStream(envInvocation("verify.multi", List.of())).blockFirst();

        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("verify.multi", args(), once()))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("exactly once")
                .hasMessageContaining("invoked 2 time(s)");
    }

    @Test
    void whenVerifyEnvironmentAttributeNever_thenPassesIfNeverCalled() {
        broker.mockEnvironmentAttribute("verifyNever", "verify.never", args(), Value.of("result"));

        broker.verifyEnvironmentAttribute("verify.never", args(), Times.never());
    }

    @Test
    void whenVerifyEnvironmentAttributeNever_thenFailsIfCalled() {
        broker.mockEnvironmentAttribute("verifyNeverFail", "verify.neverfail", args(), Value.of("result"));

        broker.attributeStream(envInvocation("verify.neverfail", List.of())).blockFirst();

        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("verify.neverfail", args(), Times.never()))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("never")
                .hasMessageContaining("invoked 1 time(s)");
    }

    @Test
    void whenVerifyEnvironmentAttributeTimes_thenPassesIfCountMatches() {
        broker.mockEnvironmentAttribute("verifyTimes", "verify.times", args(), Value.of("result"));

        broker.attributeStream(envInvocation("verify.times", List.of())).blockFirst();
        broker.attributeStream(envInvocation("verify.times", List.of())).blockFirst();
        broker.attributeStream(envInvocation("verify.times", List.of())).blockFirst();

        broker.verifyEnvironmentAttribute("verify.times", args(), times(3));
    }

    @Test
    void whenVerifyEnvironmentAttributeWithArgs_thenMatchesCorrectly() {
        broker.mockEnvironmentAttribute("verifyArgs", "verify.args", args(any()), Value.of("result"));

        broker.attributeStream(envInvocation("verify.args", List.of(Value.of("a")))).blockFirst();
        broker.attributeStream(envInvocation("verify.args", List.of(Value.of("b")))).blockFirst();
        broker.attributeStream(envInvocation("verify.args", List.of(Value.of("a")))).blockFirst();

        broker.verifyEnvironmentAttribute("verify.args", args(eq(Value.of("a"))), times(2));
        broker.verifyEnvironmentAttribute("verify.args", args(eq(Value.of("b"))), once());
        broker.verifyEnvironmentAttribute("verify.args", args(any()), times(3));
    }

    @Test
    void whenVerifyRegularAttribute_thenMatchesEntityAndArgs() {
        var entity = Value.of("user1");
        broker.mockAttribute("verifyEntity", "verify.entity", eq(entity), args(), Value.of("result"));

        broker.attributeStream(entityInvocation("verify.entity", entity, List.of())).blockFirst();
        broker.attributeStream(entityInvocation("verify.entity", entity, List.of())).blockFirst();

        broker.verifyAttribute("verify.entity", eq(entity), args(), times(2));
    }

    @Test
    void whenVerifyRegularAttributeWithAny_thenMatchesAllEntities() {
        broker.mockAttribute("verifyAnyEntity", "verify.anyentity", any(), args(), Value.of("result"));

        broker.attributeStream(entityInvocation("verify.anyentity", Value.of("user1"), List.of())).blockFirst();
        broker.attributeStream(entityInvocation("verify.anyentity", Value.of("user2"), List.of())).blockFirst();

        broker.verifyAttribute("verify.anyentity", any(), args(), times(2));
        broker.verifyAttribute("verify.anyentity", eq(Value.of("user1")), args(), once());
    }

    @Test
    void whenVerifyEnvironmentAttributeCalled_thenIsConvenienceForAtLeastOnce() {
        broker.mockEnvironmentAttribute("verifyCalled", "verify.called", args(), Value.of("result"));

        broker.attributeStream(envInvocation("verify.called", List.of())).blockFirst();
        broker.attributeStream(envInvocation("verify.called", List.of())).blockFirst();

        broker.verifyEnvironmentAttributeCalled("verify.called", args());
    }

    @Test
    void whenVerifyAttributeCalled_thenIsConvenienceForAtLeastOnce() {
        var entity = Value.of("testUser");
        broker.mockAttribute("verifyAttrCalled", "verify.attrcalled", eq(entity), args(), Value.of("result"));

        broker.attributeStream(entityInvocation("verify.attrcalled", entity, List.of())).blockFirst();

        broker.verifyAttributeCalled("verify.attrcalled", eq(entity), args());
    }

    @Test
    void whenVerificationFails_thenMessageShowsRecordedInvocations() {
        broker.mockEnvironmentAttribute("failMsg", "fail.msg", args(any()), Value.of("result"));

        broker.attributeStream(envInvocation("fail.msg", List.of(Value.of("arg1")))).blockFirst();
        broker.attributeStream(envInvocation("fail.msg", List.of(Value.of("arg2")))).blockFirst();

        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("fail.msg", args(eq(Value.of("other"))), once()))
                .isInstanceOf(MockVerificationException.class).hasMessageContaining("Recorded invocations")
                .hasMessageContaining("fail.msg");
    }

    @Test
    void whenVerificationFailsForUnknownAttribute_thenMessageIndicatesNoInvocations() {
        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("unknown.attr", args(), once()))
                .isInstanceOf(MockVerificationException.class)
                .hasMessageContaining("No invocations of 'unknown.attr' were recorded");
    }

    @Test
    void whenVerifyWithInvalidParameters_thenThrows() {
        var invalidParams = new SaplTestFixture.Parameters() {};

        assertThatThrownBy(() -> broker.verifyEnvironmentAttribute("test.attr", invalidParams, once()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }

    @Test
    void whenVerifyAttributeWithInvalidParameters_thenThrows() {
        var invalidParams = new SaplTestFixture.Parameters() {};

        assertThatThrownBy(() -> broker.verifyAttribute("test.attr", any(), invalidParams, once()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("args()");
    }
}

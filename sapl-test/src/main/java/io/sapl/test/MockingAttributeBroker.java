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
import io.sapl.test.MockingFunctionBroker.ArgumentMatcher;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import io.sapl.test.verification.AttributeInvocationRecord;
import io.sapl.test.verification.MockVerificationException;
import io.sapl.test.verification.Times;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An AttributeBroker decorator that intercepts attribute lookups and returns
 * mocked streams when configured.
 * <p>
 * Implements a spy pattern: unmocked attributes delegate to the underlying
 * broker, while mocked attributes return test-controlled streams.
 * <p>
 * Each mock is identified by a unique mockId provided at registration time.
 * This mockId is used for emitting values to specific mocks, eliminating
 * ambiguity when multiple mocks exist for the same attribute name.
 * <p>
 * Each mock uses a multicast sink with cache size 1 (replay latest). This
 * means:
 * <ul>
 * <li>Initial values are emitted to the sink immediately on mock
 * registration</li>
 * <li>Late subscribers receive the last emitted value (cached)</li>
 * <li>All current subscribers receive new emissions</li>
 * </ul>
 * <p>
 * Supports two types of attributes following SAPL semantics:
 * <ul>
 * <li>Environment attributes: no entity, matched by name and argument
 * arity</li>
 * <li>Regular attributes: matched by entity, name, and argument arity</li>
 * </ul>
 * <p>
 * Mock matching uses most-specific-first semantics, same as
 * {@link MockingFunctionBroker}.
 */
public final class MockingAttributeBroker implements AttributeBroker {

    private AttributeBroker                        delegate;
    private final Map<String, List<AttributeMock>> mocksByName     = new HashMap<>();
    private final Map<String, AttributeMock>       mocksById       = new HashMap<>();
    private final Map<String, Sinks.Many<Value>>   sinks           = new HashMap<>();
    private final List<AttributeInvocationRecord>  invocations     = new CopyOnWriteArrayList<>();
    private final AtomicLong                       sequenceCounter = new AtomicLong(0);

    /**
     * Creates a mocking broker with no delegate.
     * A delegate must be set via {@link #setDelegate(AttributeBroker)} before
     * unmocked attributes can be resolved.
     */
    public MockingAttributeBroker() {
        this.delegate = null;
    }

    /**
     * Creates a mocking broker wrapping the given delegate.
     *
     * @param delegate the broker to delegate unmocked calls to
     * @throws NullPointerException if delegate is null
     */
    public MockingAttributeBroker(@NonNull AttributeBroker delegate) {
        this.delegate = delegate;
    }

    /**
     * Sets or replaces the delegate broker for unmocked attribute lookups.
     *
     * @param delegate the broker to delegate unmocked calls to
     * @throws NullPointerException if delegate is null
     */
    public void setDelegate(@NonNull AttributeBroker delegate) {
        this.delegate = delegate;
    }

    @Override
    public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
        recordInvocation(invocation);
        return findMostSpecificMatch(invocation).map(mock -> getStream(mock.mockId())).orElseGet(() -> {
            if (delegate == null) {
                return Flux.error(
                        new IllegalStateException("No mock matched attribute '%s' and no delegate broker configured."
                                .formatted(invocation.attributeName())));
            }
            return delegate.attributeStream(invocation);
        });
    }

    private void recordInvocation(AttributeFinderInvocation invocation) {
        var invocationRecord = new AttributeInvocationRecord(invocation.attributeName(), invocation.entity(),
                invocation.arguments(), sequenceCounter.getAndIncrement());
        invocations.add(invocationRecord);
    }

    @Override
    public List<Class<?>> getRegisteredLibraries() {
        if (delegate == null) {
            return List.of();
        }
        return delegate.getRegisteredLibraries();
    }

    /**
     * Registers an environment attribute mock (streaming only).
     * <p>
     * The stream will only emit values when {@link #emit(String, Value)} is called
     * with the same mockId. Late subscribers will wait for the first emit.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name (e.g., "time.now")
     * @param arguments argument matchers defining arity
     * @throws NullPointerException if mockId, attributeName, or arguments is null
     * @throws IllegalArgumentException if mockId is blank, already registered, or
     * arguments invalid
     */
    public void mockEnvironmentAttribute(String mockId, String attributeName, SaplTestFixture.Parameters arguments) {
        mockEnvironmentAttribute(mockId, attributeName, arguments, null);
    }

    /**
     * Registers an environment attribute mock with an initial value.
     * <p>
     * The initial value is emitted immediately to the mock's sink and cached.
     * All subscribers (including late ones) will receive the cached value,
     * then any subsequent emissions via {@link #emit(String, Value)}.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name (e.g., "time.now")
     * @param arguments argument matchers defining arity
     * @param initialValue value to emit immediately (null for no initial value)
     * @throws NullPointerException if mockId, attributeName, or arguments is null
     * @throws IllegalArgumentException if mockId is blank, already registered, or
     * arguments invalid
     */
    public void mockEnvironmentAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull SaplTestFixture.Parameters arguments, Value initialValue) {
        validateMockId(mockId);
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }
        var mock = new AttributeMock(mockId, null, matchers, true);
        addMock(attributeName, mock);

        // Emit initial value to sink immediately (cached for late subscribers)
        if (initialValue != null) {
            emitToSink(mockId, initialValue);
        }
    }

    /**
     * Registers a regular attribute mock (streaming only).
     * <p>
     * The stream will only emit values when {@link #emit(String, Value)} is called
     * with the same mockId. Late subscribers will wait for the first emit.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers defining arity
     * @throws NullPointerException if mockId, attributeName, or arguments is null
     * @throws IllegalArgumentException if mockId is blank, already registered, or
     * arguments invalid
     */
    public void mockAttribute(String mockId, String attributeName, ArgumentMatcher entityMatcher,
            SaplTestFixture.Parameters arguments) {
        mockAttribute(mockId, attributeName, entityMatcher, arguments, null);
    }

    /**
     * Registers a regular attribute mock with an initial value.
     * <p>
     * The initial value is emitted immediately to the mock's sink and cached.
     * All subscribers (including late ones) will receive the cached value,
     * then any subsequent emissions via {@link #emit(String, Value)}.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers defining arity
     * @param initialValue value to emit immediately (null for no initial value)
     * @throws NullPointerException if mockId, attributeName, or arguments is null
     * @throws IllegalArgumentException if mockId is blank, already registered, or
     * arguments invalid
     */
    public void mockAttribute(@NonNull String mockId, @NonNull String attributeName, ArgumentMatcher entityMatcher,
            @NonNull SaplTestFixture.Parameters arguments, Value initialValue) {
        validateMockId(mockId);
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }
        var mock = new AttributeMock(mockId, entityMatcher, matchers, false);
        addMock(attributeName, mock);

        // Emit initial value to sink immediately (cached for late subscribers)
        if (initialValue != null) {
            emitToSink(mockId, initialValue);
        }
    }

    /**
     * Emits a value to the mock identified by mockId.
     * <p>
     * The value is sent to all current subscribers and cached for late subscribers
     * (cache size 1 - only the last value is retained). To signal an error
     * condition,
     * emit an {@link io.sapl.api.model.ErrorValue}.
     *
     * @param mockId the unique identifier of the mock to emit to
     * @param value the value to emit
     * @throws NullPointerException if mockId or value is null
     * @throws IllegalStateException if no mock is registered with the given mockId
     */
    public void emit(@NonNull String mockId, @NonNull Value value) {
        if (!mocksById.containsKey(mockId)) {
            throw new IllegalStateException("No mock registered with id '%s'.".formatted(mockId));
        }
        emitToSink(mockId, value);
    }

    /**
     * Checks if any mock is registered for the given attribute name.
     *
     * @param attributeName fully qualified name
     * @return true if at least one mock exists for this attribute
     */
    public boolean hasMockForAttribute(String attributeName) {
        return mocksByName.containsKey(attributeName) && !mocksByName.get(attributeName).isEmpty();
    }

    /**
     * Checks if a mock with the given id is registered.
     *
     * @param mockId the mock identifier
     * @return true if a mock with this id exists
     */
    public boolean hasMock(String mockId) {
        return mocksById.containsKey(mockId);
    }

    /**
     * Clears all registered mocks and recorded invocations.
     * <p>
     * After clearing, subsequent attribute lookups will delegate to the underlying
     * broker. Existing streams from previously registered mocks will no longer
     * receive emissions.
     */
    public void clearAllMocks() {
        sinks.clear();
        mocksByName.clear();
        mocksById.clear();
        clearInvocations();
    }

    /**
     * Clears recorded invocations without clearing mocks.
     * <p>
     * Useful when you want to verify invocations for a specific phase of testing
     * while keeping mocks in place.
     */
    public void clearInvocations() {
        invocations.clear();
        sequenceCounter.set(0);
    }

    /**
     * Returns all recorded invocations.
     *
     * @return unmodifiable list of invocation records
     */
    public List<AttributeInvocationRecord> getInvocations() {
        return List.copyOf(invocations);
    }

    /**
     * Returns recorded invocations for a specific attribute.
     *
     * @param attributeName fully qualified attribute name
     * @return unmodifiable list of invocation records for this attribute
     */
    public List<AttributeInvocationRecord> getInvocations(String attributeName) {
        return invocations.stream().filter(r -> r.attributeName().equals(attributeName)).toList();
    }

    /**
     * Verifies that an environment attribute was invoked the expected number of
     * times
     * with arguments matching the given matchers.
     * <p>
     * Example:
     *
     * <pre>{@code
     * broker.verifyEnvironmentAttribute("time.now", args(), Times.once());
     * broker.verifyEnvironmentAttribute("clock.ticker", args(), Times.times(5));
     * }</pre>
     *
     * @param attributeName fully qualified attribute name
     * @param arguments argument matchers (use args() for arity matching)
     * @param times expected invocation count
     * @throws MockVerificationException if verification fails
     * @throws IllegalArgumentException if arguments is not an ArgumentMatchers
     * instance
     */
    public void verifyEnvironmentAttribute(@NonNull String attributeName, @NonNull SaplTestFixture.Parameters arguments,
            @NonNull Times times) {
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }

        var matchingCount = countMatchingInvocations(attributeName, null, matchers, true);
        if (!times.verify(matchingCount)) {
            throw new MockVerificationException(
                    buildVerificationMessage(attributeName, null, matchers, true, times, matchingCount));
        }
    }

    /**
     * Verifies that a regular attribute was invoked the expected number of times
     * with entity and arguments matching the given matchers.
     * <p>
     * Example:
     *
     * <pre>{@code
     * broker.verifyAttribute("user.role", eq(Value.of("alice")), args(), Times.once());
     * }</pre>
     *
     * @param attributeName fully qualified attribute name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers (use args() for arity matching)
     * @param times expected invocation count
     * @throws MockVerificationException if verification fails
     * @throws IllegalArgumentException if arguments is not an ArgumentMatchers
     * instance
     */
    public void verifyAttribute(@NonNull String attributeName, @NonNull ArgumentMatcher entityMatcher,
            @NonNull SaplTestFixture.Parameters arguments, @NonNull Times times) {
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException("Arguments must be created via args().");
        }

        var matchingCount = countMatchingInvocations(attributeName, entityMatcher, matchers, false);
        if (!times.verify(matchingCount)) {
            throw new MockVerificationException(
                    buildVerificationMessage(attributeName, entityMatcher, matchers, false, times, matchingCount));
        }
    }

    /**
     * Verifies that an environment attribute was invoked at least once.
     *
     * @param attributeName fully qualified attribute name
     * @param arguments argument matchers
     * @throws MockVerificationException if attribute was never invoked
     */
    public void verifyEnvironmentAttributeCalled(@NonNull String attributeName,
            @NonNull SaplTestFixture.Parameters arguments) {
        verifyEnvironmentAttribute(attributeName, arguments, Times.atLeast(1));
    }

    /**
     * Verifies that a regular attribute was invoked at least once.
     *
     * @param attributeName fully qualified attribute name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers
     * @throws MockVerificationException if attribute was never invoked
     */
    public void verifyAttributeCalled(@NonNull String attributeName, @NonNull ArgumentMatcher entityMatcher,
            @NonNull SaplTestFixture.Parameters arguments) {
        verifyAttribute(attributeName, entityMatcher, arguments, Times.atLeast(1));
    }

    private int countMatchingInvocations(String attributeName, ArgumentMatcher entityMatcher,
            List<ArgumentMatcher> argMatchers, boolean isEnvironmentAttribute) {
        return (int) invocations.stream().filter(r -> r.attributeName().equals(attributeName))
                .filter(r -> isEnvironmentAttribute == r.isEnvironmentAttribute())
                .filter(r -> isEnvironmentAttribute || entityMatcher == null || entityMatcher.matches(r.entity()))
                .filter(r -> matchesArguments(r.arguments(), argMatchers)).count();
    }

    private boolean matchesArguments(List<Value> arguments, List<ArgumentMatcher> matchers) {
        if (arguments.size() != matchers.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (!matchers.get(i).matches(arguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private String buildVerificationMessage(String attributeName, ArgumentMatcher entityMatcher,
            List<ArgumentMatcher> argMatchers, boolean isEnvironmentAttribute, Times times, int actualCount) {
        var sb = new StringBuilder();
        sb.append("Attribute verification failed for '%s'.%n".formatted(attributeName));
        sb.append(times.failureMessage(actualCount));

        var attributeInvocations = getInvocations(attributeName);
        if (attributeInvocations.isEmpty()) {
            sb.append("%nNo invocations of '%s' were recorded.".formatted(attributeName));
        } else {
            sb.append("%nRecorded invocations of '%s':".formatted(attributeName));
            for (var inv : attributeInvocations) {
                sb.append("\n  - ").append(inv);
            }
        }
        return sb.toString();
    }

    private void validateMockId(String mockId) {
        if (mockId.isBlank()) {
            throw new IllegalArgumentException("MockId must not be blank.");
        }
        if (mocksById.containsKey(mockId)) {
            throw new IllegalArgumentException("MockId '%s' is already registered.".formatted(mockId));
        }
    }

    private void addMock(String attributeName, AttributeMock mock) {
        mocksByName.computeIfAbsent(attributeName, k -> new ArrayList<>()).add(mock);
        mocksById.put(mock.mockId(), mock);
    }

    private Optional<AttributeMock> findMostSpecificMatch(AttributeFinderInvocation invocation) {
        var attributeMocks = mocksByName.get(invocation.attributeName());
        if (attributeMocks == null || attributeMocks.isEmpty()) {
            return Optional.empty();
        }

        return attributeMocks.stream().filter(mock -> mock.matches(invocation.entity(), invocation.arguments()))
                .max(Comparator.comparingInt(AttributeMock::specificity));
    }

    private Sinks.Many<Value> getOrCreateSink(String mockId) {
        return sinks.computeIfAbsent(mockId, k -> Sinks.many().replay().limit(1));
    }

    private Flux<Value> getStream(String mockId) {
        return getOrCreateSink(mockId).asFlux();
    }

    private void emitToSink(String mockId, Value value) {
        getOrCreateSink(mockId).tryEmitNext(value);
    }

    /**
     * A registered attribute mock with entity matcher and argument matchers.
     */
    private record AttributeMock(
            String mockId,
            ArgumentMatcher entityMatcher,
            List<ArgumentMatcher> argumentMatchers,
            boolean isEnvironmentAttribute) {

        AttributeMock {
            argumentMatchers = List.copyOf(argumentMatchers);
        }

        boolean matches(Value entity, List<Value> arguments) {
            // Check arity first
            if (arguments.size() != argumentMatchers.size()) {
                return false;
            }

            // Environment attributes don't match entity
            if (!isEnvironmentAttribute && entityMatcher != null && !entityMatcher.matches(entity)) {
                return false;
            }

            // Check argument matchers
            for (int i = 0; i < arguments.size(); i++) {
                if (!argumentMatchers.get(i).matches(arguments.get(i))) {
                    return false;
                }
            }
            return true;
        }

        int specificity() {
            int entitySpecificity = (entityMatcher != null) ? entityMatcher.specificity() : 0;
            int argsSpecificity   = argumentMatchers.stream().mapToInt(ArgumentMatcher::specificity).sum();
            return entitySpecificity + argsSpecificity;
        }
    }
}

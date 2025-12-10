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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import lombok.val;
import org.hamcrest.Matcher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * An AttributeBroker implementation that allows mocking of attribute streams
 * for testing purposes.
 * <p>
 * This broker wraps a delegate AttributeBroker and intercepts attribute
 * requests. If a mock is registered for an attribute, the mock stream is
 * returned. Otherwise, the call is delegated to the original broker.
 * <p>
 * This implements a spy-like pattern where unmocked attributes work normally
 * while mocked attributes return test-defined streams.
 * <p>
 * Supports multiple mocking strategies:
 * <ul>
 * <li>Fixed value streams (always emit the same value)</li>
 * <li>Timed value sequences (emit values at specified intervals)</li>
 * <li>Dynamic publisher mocks (emit values programmatically during tests)</li>
 * <li>Entity-specific mocks (return different values based on the entity)</li>
 * </ul>
 */
public class MockingAttributeBroker implements AttributeBroker {

    private static final String ERROR_MOCK_INVALID_FULL_NAME           = "Invalid attribute reference containing more than one '.' delimiter: '%s'.";
    private static final String ERROR_MOCK_ALREADY_REGISTERED          = "A mock for attribute '%s' is already registered.";
    private static final String ERROR_MOCK_COLLISION                   = "A mock for attribute '%s' is already registered with a different mock type.";
    private static final String ERROR_NO_PUBLISHER_MOCK                = "No publisher mock registered for attribute '%s'. Did you forget to call markAttributeMock()?";
    private static final String ERROR_NO_MATCHING_ENTITY_MOCK          = "No mock found for attribute '%s' matching the given entity.";
    private static final String NAME_DELIMITER                         = ".";
    private static final int    EXPECTED_PARTS_IN_FULLY_QUALIFIED_NAME = 2;

    private final AttributeBroker                      delegate;
    private final Map<String, AttributeMockDefinition> registeredMocks = new HashMap<>();

    /**
     * Creates a MockingAttributeBroker with the given delegate.
     *
     * @param delegate the AttributeBroker to delegate unmocked attribute requests
     * to
     */
    public MockingAttributeBroker(AttributeBroker delegate) {
        this.delegate = delegate;
    }

    @Override
    public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
        val attributeName = invocation.attributeName();
        val mock          = registeredMocks.get(attributeName);

        if (mock != null) {
            return mock.stream(invocation);
        }

        return delegate.attributeStream(invocation);
    }

    @Override
    public List<Class<?>> getRegisteredLibraries() {
        return delegate.getRegisteredLibraries();
    }

    /**
     * Registers a mock that always emits the same value.
     *
     * @param attributeName the fully qualified attribute name (e.g., "time.now")
     * @param value the value to emit
     */
    public void mockAttributeAlwaysReturns(String attributeName, Value value) {
        validateAttributeName(attributeName);
        ensureNoExistingMock(attributeName);
        registeredMocks.put(attributeName, new FixedValueMock(value));
    }

    /**
     * Registers a mock that emits values at timed intervals.
     *
     * @param attributeName the fully qualified attribute name
     * @param interval the duration between emissions
     * @param values the values to emit in sequence
     */
    public void mockAttributeWithTimedSequence(String attributeName, Duration interval, Value... values) {
        validateAttributeName(attributeName);
        ensureNoExistingMock(attributeName);
        registeredMocks.put(attributeName, new TimedSequenceMock(interval, values));
    }

    /**
     * Registers a dynamic publisher mock that can be controlled during tests.
     * Use {@link #emitToAttribute(String, Value)} to emit values to the stream.
     *
     * @param attributeName the fully qualified attribute name
     */
    public void markAttributeMock(String attributeName) {
        validateAttributeName(attributeName);
        ensureNoExistingMock(attributeName);
        registeredMocks.put(attributeName, new PublisherMock());
    }

    /**
     * Emits a value to a publisher mock.
     *
     * @param attributeName the fully qualified attribute name
     * @param value the value to emit
     */
    public void emitToAttribute(String attributeName, Value value) {
        val mock = registeredMocks.get(attributeName);
        if (mock instanceof PublisherMock publisherMock) {
            publisherMock.emit(value);
        } else {
            throw new SaplTestException(ERROR_NO_PUBLISHER_MOCK.formatted(attributeName));
        }
    }

    /**
     * Completes a publisher mock stream.
     *
     * @param attributeName the fully qualified attribute name
     */
    public void completeAttribute(String attributeName) {
        val mock = registeredMocks.get(attributeName);
        if (mock instanceof PublisherMock publisherMock) {
            publisherMock.complete();
        } else {
            throw new SaplTestException(ERROR_NO_PUBLISHER_MOCK.formatted(attributeName));
        }
    }

    /**
     * Registers a mock that returns different values based on the entity.
     *
     * @param attributeName the fully qualified attribute name
     * @param entityMatcher a function that checks if an entity matches
     * @param returnValue the value to return when entity matches
     */
    public void mockAttributeForEntity(String attributeName, Function<Value, Boolean> entityMatcher,
            Value returnValue) {
        validateAttributeName(attributeName);
        val existingMock = registeredMocks.get(attributeName);

        if (existingMock != null) {
            if (existingMock instanceof EntitySpecificMock entityMock) {
                entityMock.addMapping(entityMatcher, returnValue);
            } else {
                throw new SaplTestException(ERROR_MOCK_COLLISION.formatted(attributeName));
            }
        } else {
            val entityMock = new EntitySpecificMock(attributeName);
            entityMock.addMapping(entityMatcher, returnValue);
            registeredMocks.put(attributeName, entityMock);
        }
    }

    /**
     * Registers a mock that computes the stream based on the invocation.
     *
     * @param attributeName the fully qualified attribute name
     * @param computation a function that takes the invocation and returns a value
     * stream
     */
    public void mockAttributeComputed(String attributeName,
            Function<AttributeFinderInvocation, Flux<Value>> computation) {
        validateAttributeName(attributeName);
        ensureNoExistingMock(attributeName);
        registeredMocks.put(attributeName, new ComputedMock(computation));
    }

    /**
     * Registers a mock that returns values based on entity and argument matchers.
     *
     * @param attributeName the fully qualified attribute name
     * @param parameters the AttributeParameters containing matchers
     * @param returnValue the value to return when matchers match
     */
    public void mockAttributeForParameterMatchers(String attributeName, AttributeParameters parameters,
            Value returnValue) {
        validateAttributeName(attributeName);
        val existingMock = registeredMocks.get(attributeName);

        if (existingMock != null) {
            if (existingMock instanceof ParameterMatcherMock matcherMock) {
                matcherMock.addMapping(parameters, returnValue);
            } else {
                throw new SaplTestException(ERROR_MOCK_COLLISION.formatted(attributeName));
            }
        } else {
            val matcherMock = new ParameterMatcherMock(attributeName);
            matcherMock.addMapping(parameters, returnValue);
            registeredMocks.put(attributeName, matcherMock);
        }
    }

    /**
     * Checks if a mock is registered for the given attribute.
     *
     * @param attributeName the fully qualified attribute name
     * @return true if a mock is registered
     */
    public boolean hasMock(String attributeName) {
        return registeredMocks.containsKey(attributeName);
    }

    /**
     * Removes a mock registration.
     *
     * @param attributeName the fully qualified attribute name
     */
    public void clearMock(String attributeName) {
        registeredMocks.remove(attributeName);
    }

    /**
     * Removes all mock registrations.
     */
    public void clearAllMocks() {
        registeredMocks.clear();
    }

    private void validateAttributeName(String attributeName) {
        val parts = attributeName.split(Pattern.quote(NAME_DELIMITER));
        if (parts.length != EXPECTED_PARTS_IN_FULLY_QUALIFIED_NAME) {
            throw new SaplTestException(ERROR_MOCK_INVALID_FULL_NAME.formatted(attributeName));
        }
    }

    private void ensureNoExistingMock(String attributeName) {
        if (registeredMocks.containsKey(attributeName)) {
            throw new SaplTestException(ERROR_MOCK_ALREADY_REGISTERED.formatted(attributeName));
        }
    }

    /**
     * Internal interface for mock definitions.
     */
    private sealed interface AttributeMockDefinition {

        Flux<Value> stream(AttributeFinderInvocation invocation);

    }

    /**
     * Mock that always emits the same value.
     */
    private record FixedValueMock(Value value) implements AttributeMockDefinition {

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            return Flux.just(value);
        }

    }

    /**
     * Mock that emits values at timed intervals.
     */
    private record TimedSequenceMock(Duration interval, Value[] values) implements AttributeMockDefinition {

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            return Flux.fromArray(values).delayElements(interval);
        }

    }

    /**
     * Mock that can be controlled programmatically.
     * Uses replay sink to buffer values for late subscribers, which is essential
     * when policy SETs use combining algorithms like first-applicable that
     * internally use Flux.combineLatest - requiring all streams to emit before
     * producing any output.
     */
    private static final class PublisherMock implements AttributeMockDefinition {

        private final Sinks.Many<Value> sink = Sinks.many().replay().all();

        void emit(Value value) {
            sink.tryEmitNext(value);
        }

        void complete() {
            sink.tryEmitComplete();
        }

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            return sink.asFlux();
        }

    }

    /**
     * Mock that returns different values based on the entity.
     */
    private static final class EntitySpecificMock implements AttributeMockDefinition {

        private final String                attributeName;
        private final List<EntityValuePair> mappings = new ArrayList<>();

        EntitySpecificMock(String attributeName) {
            this.attributeName = attributeName;
        }

        void addMapping(Function<Value, Boolean> entityMatcher, Value returnValue) {
            mappings.add(new EntityValuePair(entityMatcher, returnValue));
        }

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            val entity = invocation.entity();
            for (val mapping : mappings) {
                if (mapping.matches(entity)) {
                    return Flux.just(mapping.returnValue());
                }
            }
            return Flux.error(new SaplTestException(ERROR_NO_MATCHING_ENTITY_MOCK.formatted(attributeName)));
        }

        private record EntityValuePair(Function<Value, Boolean> entityMatcher, Value returnValue) {

            boolean matches(Value entity) {
                return entityMatcher.apply(entity);
            }

        }

    }

    /**
     * Mock that computes the stream dynamically.
     */
    private record ComputedMock(Function<AttributeFinderInvocation, Flux<Value>> computation)
            implements AttributeMockDefinition {

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            return computation.apply(invocation);
        }

    }

    /**
     * Mock that returns different values based on entity and argument matchers.
     */
    private static final class ParameterMatcherMock implements AttributeMockDefinition {

        private static final String ERROR_NO_MATCHING_PARAMS = "No mock found for attribute '%s' matching the given entity and arguments.";

        private final String                             attributeName;
        private final List<ParameterMatcherValueMapping> mappings = new ArrayList<>();

        ParameterMatcherMock(String attributeName) {
            this.attributeName = attributeName;
        }

        void addMapping(AttributeParameters parameters, Value returnValue) {
            mappings.add(new ParameterMatcherValueMapping(parameters, returnValue));
        }

        @Override
        public Flux<Value> stream(AttributeFinderInvocation invocation) {
            for (val mapping : mappings) {
                if (mapping.matches(invocation)) {
                    return Flux.just(mapping.returnValue());
                }
            }
            return Flux.error(new SaplTestException(ERROR_NO_MATCHING_PARAMS.formatted(attributeName)));
        }

        private record ParameterMatcherValueMapping(AttributeParameters parameters, Value returnValue) {

            boolean matches(AttributeFinderInvocation invocation) {
                val entityMatcher = parameters.getEntityValueMatcher().getMatcher();
                if (!entityMatcher.matches(invocation.entity())) {
                    return false;
                }

                val argMatchers = parameters.getArgumentMatchers().getMatchers();
                val actualArgs  = invocation.arguments();

                if (argMatchers.length != actualArgs.size()) {
                    return false;
                }

                for (int i = 0; i < argMatchers.length; i++) {
                    if (!argMatchers[i].matches(actualArgs.get(i))) {
                        return false;
                    }
                }

                return true;
            }

        }

    }

}

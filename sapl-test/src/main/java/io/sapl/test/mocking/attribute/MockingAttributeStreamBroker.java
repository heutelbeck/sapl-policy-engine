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
import io.sapl.attributes.broker.api.PolicyInformationPointImplementation;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.models.AttributeEntityValueMatcher;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MockingAttributeStreamBroker implements AttributeStreamBroker {

    private static final String ERROR_MOCK_INVALID_FULL_NAME = "Got invalid attribute reference containing more than one \".\" delimiter: \"%s\"";

    private static final String ERROR_NOT_MARKED_DYNAMIC_MOCK = "No registered dynamic mock found for \"%s\". Did you forgot to register the mock via \".givenAttribute(\"%s\")\"";

    private static final String ERROR_DUPLICATE_MOCK_REGISTRATION = "Duplicate registration of mock for PIP attribute \"%s\"";

    private static final String NAME_DELIMITER = ".";

    /**
     * Holds an AttributeContext implementation to delegate evaluations if this
     * attribute is not mocked
     */
    private final AttributeStreamBroker originalAttributeStreamBroker;

    /**
     * Contains a Map of all registered mocks. Key is the String of the full name of
     * the attribute finder Value is the {@link Flux} to be returned
     */
    private final Map<String, AttributeMock> registeredMocks;

    /**
     * Constructor of MockingAttributeContext
     *
     * @param originalAttributeContext original "normal" AttributeContext do
     * delegate original attribute calls
     */
    public MockingAttributeStreamBroker(AttributeStreamBroker originalAttributeContext) {
        this.originalAttributeStreamBroker = originalAttributeContext;
        this.registeredMocks               = new HashMap<>();
    }

    @Override
    public Flux<Val> attributeStream(AttributeFinderInvocation invocation) {
        AttributeMock mock = this.registeredMocks.get(invocation.attributeName());
        if (null == mock) {
            return this.originalAttributeStreamBroker.attributeStream(invocation);
        }
        return mock.evaluate(invocation);
    }

    public void markAttributeMock(String fullName) {
        checkImportName(fullName);

        if (this.registeredMocks.containsKey(fullName)) {
            throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
        } else {
            AttributeMockPublisher mock = new AttributeMockPublisher(fullName);
            this.registeredMocks.put(fullName, mock);
        }
    }

    public void mockEmit(String fullName, Val returns) {
        AttributeMock mock = this.registeredMocks.get(fullName);

        if (mock instanceof AttributeMockPublisher attributeMockPublisher) {
            attributeMockPublisher.mockEmit(returns);
        } else {
            throw new SaplTestException(String.format(ERROR_NOT_MARKED_DYNAMIC_MOCK, fullName, fullName));
        }
    }

    public void loadAttributeMockForEntityValue(String fullName, AttributeEntityValueMatcher parentValueMatcher,
            Val returns) {
        checkImportName(fullName);

        AttributeMock mock = this.registeredMocks.get(fullName);
        if (mock != null) {
            if (mock instanceof AttributeMockForEntityValue attributeMockForParentValue) {
                attributeMockForParentValue.loadMockForParentValue(parentValueMatcher, returns);
            } else {
                throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
            }
        } else {
            AttributeMockForEntityValue newMock = new AttributeMockForEntityValue(fullName);
            newMock.loadMockForParentValue(parentValueMatcher, returns);
            this.registeredMocks.put(fullName, newMock);
        }
    }

    public void loadAttributeMockForParentValueAndArguments(String fullName, AttributeParameters parameters,
            Val returns) {
        checkImportName(fullName);
        AttributeMock mock = this.registeredMocks.get(fullName);
        if (mock != null) {
            if (mock instanceof AttributeMockForEntityValueAndArguments attributeMockForParentValueAndArguments) {
                attributeMockForParentValueAndArguments.loadMockForParentValueAndArguments(parameters, returns);
            } else {
                throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
            }
        } else {
            AttributeMockForEntityValueAndArguments newMock = new AttributeMockForEntityValueAndArguments(fullName);
            newMock.loadMockForParentValueAndArguments(parameters, returns);
            this.registeredMocks.put(fullName, newMock);
        }
    }

    public void loadAttributeMock(String fullName, Duration timing, Val... returns) {
        checkImportName(fullName);

        if (this.registeredMocks.containsKey(fullName)) {
            throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
        } else {
            AttributeMockTiming mock = new AttributeMockTiming(fullName);
            mock.loadAttributeMockWithTiming(timing, returns);
            this.registeredMocks.put(fullName, mock);
        }
    }

    public void assertVerifications() {
        this.registeredMocks.forEach((fullName, mock) -> mock.assertVerifications());
    }

    private void checkImportName(String fullName) {
        String[] split = fullName.split(Pattern.quote(NAME_DELIMITER));
        if (split.length != 2) {
            throw new SaplTestException(String.format(ERROR_MOCK_INVALID_FULL_NAME, fullName));
        }
    }

    @Override
    public void loadPolicyInformationPoint(PolicyInformationPointImplementation pipImplementation) {
        // NOOP
    }

    @Override
    public void unloadPolicyInformationPoint(String name) {
        // NOOP
    }

}

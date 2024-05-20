/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import reactor.core.publisher.Flux;

public class MockingAttributeContext implements AttributeContext {

    private static final String ERROR_MOCK_INVALID_FULL_NAME = "Got invalid attribute reference containing more than one \".\" delimiter: \"%s\"";

    private static final String ERROR_NOT_MARKED_DYNAMIC_MOCK = "No registered dynamic mock found for \"%s\". Did you forgot to register the mock via \".givenAttribute(\"%s\")\"";

    private static final String ERROR_DUPLICATE_MOCK_REGISTRATION = "Duplicate registration of mock for PIP attribute \"%s\"";

    private static final String NAME_DELIMITER = ".";

    /**
     * Holds an AttributeContext implementation to delegate evaluations if this
     * attribute is not mocked
     */
    private final AttributeContext originalAttributeContext;

    /**
     * Contains a Map of all registered mocks. Key is the String of the full name of
     * the attribute finder Value is the {@link Flux} to be returned
     */
    private final Map<String, AttributeMock> registeredMocks;

    private final Map<String, PolicyInformationPointDocumentation> pipDocumentations;

    /**
     * Constructor of MockingAttributeContext
     *
     * @param originalAttributeContext original "normal" AttributeContext do
     *                                 delegate original attribute calls
     */
    public MockingAttributeContext(AttributeContext originalAttributeContext) {
        this.originalAttributeContext = originalAttributeContext;
        this.registeredMocks          = new HashMap<>();
        this.pipDocumentations        = new HashMap<>();
    }

    @Override
    public Boolean isProvidedFunction(String function) {
        if (this.registeredMocks.containsKey(function)) {
            return Boolean.TRUE;
        } else if (Boolean.TRUE.equals(originalAttributeContext.isProvidedFunction(function))) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public Collection<String> providedFunctionsOfLibrary(String pipName) {
        Set<String> set = new HashSet<>();

        for (String fullName : this.registeredMocks.keySet()) {
            String[] split = fullName.split(Pattern.quote(NAME_DELIMITER));
            if (split[0].equals(pipName))
                set.add(split[1]);
        }

        set.addAll(this.originalAttributeContext.providedFunctionsOfLibrary(pipName));

        return set;
    }

    @Override
    public Flux<Val> evaluateAttribute(EObject location, String attribute, Val value, Arguments arguments,
            Map<String, Val> variables) {
        AttributeMock mock = this.registeredMocks.get(attribute);
        if (mock != null) {
            List<Flux<Val>> args = new LinkedList<>();
            if (arguments != null) {
                for (Expression argument : arguments.getArgs()) {
                    args.add(argument.evaluate());
                }
            }
            return mock.evaluate(attribute, value, variables, args);
        } else {
            return this.originalAttributeContext.evaluateAttribute(location, attribute, value, arguments, variables);
        }
    }

    @Override
    public Flux<Val> evaluateEnvironmentAttribute(EObject location, String attribute, Arguments arguments,
            Map<String, Val> variables) {
        AttributeMock mock = this.registeredMocks.get(attribute);
        if (mock != null) {
            List<Flux<Val>> args = new LinkedList<>();
            if (arguments != null) {
                for (Expression argument : arguments.getArgs()) {
                    args.add(argument.evaluate());
                }
            }
            return mock.evaluate(attribute, Val.UNDEFINED, variables, args);
        } else {
            return this.originalAttributeContext.evaluateEnvironmentAttribute(location, attribute, arguments,
                    variables);
        }
    }

    @Override
    public Collection<PolicyInformationPointDocumentation> getDocumentation() {
        Collection<PolicyInformationPointDocumentation> doc = new LinkedList<>(this.pipDocumentations.values());
        doc.addAll(this.originalAttributeContext.getDocumentation());
        return Collections.unmodifiableCollection(doc);
    }

    public void markAttributeMock(String fullName) {
        checkImportName(fullName);

        if (this.registeredMocks.containsKey(fullName)) {
            throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
        } else {
            AttributeMockPublisher mock = new AttributeMockPublisher(fullName);
            this.registeredMocks.put(fullName, mock);

            addNewPIPDocumentation(fullName);
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

    public void loadAttributeMockForParentValue(String fullName, AttributeParentValueMatcher parentValueMatcher,
            Val returns) {
        checkImportName(fullName);

        AttributeMock mock = this.registeredMocks.get(fullName);
        if (mock != null) {
            if (mock instanceof AttributeMockForParentValue attributeMockForParentValue) {
                attributeMockForParentValue.loadMockForParentValue(parentValueMatcher, returns);
            } else {
                throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
            }
        } else {
            AttributeMockForParentValue newMock = new AttributeMockForParentValue(fullName);
            newMock.loadMockForParentValue(parentValueMatcher, returns);
            this.registeredMocks.put(fullName, newMock);

            addNewPIPDocumentation(fullName);
        }
    }

    public void loadAttributeMockForParentValueAndArguments(String fullName, AttributeParameters parameters,
            Val returns) {
        checkImportName(fullName);

        AttributeMock mock = this.registeredMocks.get(fullName);
        if (mock != null) {
            if (mock instanceof AttributeMockForParentValueAndArguments attributeMockForParentValueAndArguments) {
                attributeMockForParentValueAndArguments.loadMockForParentValueAndArguments(parameters, returns);
            } else {
                throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullName));
            }
        } else {
            AttributeMockForParentValueAndArguments newMock = new AttributeMockForParentValueAndArguments(fullName);
            newMock.loadMockForParentValueAndArguments(parameters, returns);
            this.registeredMocks.put(fullName, newMock);

            addNewPIPDocumentation(fullName);
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

            addNewPIPDocumentation(fullName);
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

    private void addNewPIPDocumentation(String fullName) {
        String[] split         = fullName.split(Pattern.quote(NAME_DELIMITER));
        String   pipName       = split[0];
        String   attributeName = split[1];

        var existingDoc = this.pipDocumentations.get(pipName);
        if (existingDoc != null) {
            existingDoc.getDocumentation().put(attributeName, "Mocked Attribute");
        } else {
            PolicyInformationPointDocumentation pipDocs = new PolicyInformationPointDocumentation(pipName,
                    "Mocked PIP " + pipName);
            pipDocs.getDocumentation().put(attributeName, "Mocked Attribute");
            this.pipDocumentations.put(pipName, pipDocs);
        }

    }

    @Override
    public Collection<String> getAvailableLibraries() {
        return registeredMocks.keySet();
    }

    @Override
    public Collection<String> getAllFullyQualifiedFunctions() {
        return List.of();
    }

    @Override
    public List<String> getEnvironmentAttributeCodeTemplates() {
        return List.of();
    }

    @Override
    public List<String> getAttributeCodeTemplates() {
        return List.of();
    }

    @Override
    public Map<String, String> getDocumentedAttributeCodeTemplates() {
        return Map.of();
    }

    @Override
    public Map<String, JsonNode> getAttributeSchemas() {
        return Map.of();
    }

}

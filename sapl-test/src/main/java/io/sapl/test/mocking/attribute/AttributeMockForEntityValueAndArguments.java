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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesParameterCalledVerification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Flux;

public class AttributeMockForEntityValueAndArguments implements AttributeMock {

    private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS = "You already defined a Mock for %s which is returning specified values when parameters are matching the expectation";

    private static final String ERROR_NO_MATCHING_ARGUMENTS = "Unable to find a mocked return value for this argument combination";

    private static final String ERROR_INVALID_NUMBER_PARAMETERS = "Test case has configured mocked attribute \"%s\" return value depending on %d parameters, but is called at runtime with %d parameters";

    private static final String ERROR_NO_MATCHING_ENTITY_VALUE_S = "Unable to find a mocked return value for this entity value. Invocation: %s";

    private final String fullName;

    private final List<ParameterSpecificMockReturnValue> listParameterSpecificMockReturnValues;

    private final MockRunInformation mockRunInformation;

    private final List<TimesParameterCalledVerification> listMockingVerifications;

    public AttributeMockForEntityValueAndArguments(String fullName) {
        this.fullName                              = fullName;
        this.listParameterSpecificMockReturnValues = new LinkedList<>();
        this.listMockingVerifications              = new LinkedList<>();
        this.mockRunInformation                    = new MockRunInformation(fullName);
    }

    public void loadMockForParentValueAndArguments(AttributeParameters parameters, Val returnValue) {
        this.listParameterSpecificMockReturnValues.add(new ParameterSpecificMockReturnValue(parameters, returnValue));

        List<Matcher<Val>> listOfAllMatcher = new LinkedList<>();
        listOfAllMatcher.add(parameters.getEntityValueMatcher().getMatcher());
        listOfAllMatcher.addAll(List.of(parameters.getArgumentMatchers().getMatchers()));
        this.listMockingVerifications.add(new TimesParameterCalledVerification(Imports.times(1), listOfAllMatcher));

    }

    @Override
    public Flux<Val> evaluate(AttributeFinderInvocation invocation) {
        List<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues = findMatchingEntityValueMockReturnValue(
                invocation.entity());

        checkAtLeastOneMatchingMockReturnValueExists(matchingParameterSpecificMockReturnValues, invocation);

        final var arguments = invocation.arguments();
        final var trace     = HashMap.<String, Val>newHashMap(arguments.size() + 1);
        trace.put("attributeName", Val.of(invocation.attributeName()));
        for (int i = 0; i < arguments.size(); i++) {
            trace.put("argument[" + i + "]", arguments.get(i));
        }
        saveCall(invocation.entity(), arguments);
        try {
            for (ParameterSpecificMockReturnValue parameterSpecificMockReturnValue : matchingParameterSpecificMockReturnValues) {

                final var argumentMatchers = parameterSpecificMockReturnValue.getExpectedParameters()
                        .getArgumentMatchers();

                checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(argumentMatchers.getMatchers(), arguments);

                if (isEveryArgumentValueMatchingItsMatcher(argumentMatchers.getMatchers(), arguments)) {
                    return Flux.just(parameterSpecificMockReturnValue.getMockReturnValue()
                            .withTrace(AttributeMockForEntityValueAndArguments.class, false, trace));
                }
            }
            throw new SaplTestException(ERROR_NO_MATCHING_ARGUMENTS);
        } catch (SaplTestException e) {
            return Flux.error(e);
        }
    }

    private void saveCall(Val parentValue, List<Val> arguments) {
        Val[] parameter = new Val[1 + arguments.size()];
        parameter[0] = parentValue;
        for (int i = 0; i < arguments.size(); i++) {
            parameter[i + 1] = arguments.get(i);
        }
        this.mockRunInformation.saveCall(new MockCall(parameter));
    }

    private boolean isEveryArgumentValueMatchingItsMatcher(Matcher<Val>[] argumentMatchers,
            List<Val> latestPublishedEventsPerArgument) {
        boolean isMatching = true;
        for (int i = 0; i < argumentMatchers.length; i++) {
            Matcher<Val> argumentMatcher = argumentMatchers[i];
            Val          argumentValue   = latestPublishedEventsPerArgument.get(i);
            if (!argumentMatcher.matches(argumentValue))
                isMatching = false;
        }
        return isMatching;
    }

    private void checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(Matcher<Val>[] argumentMatchers,
            Collection<Val> latestPublishedEventsPerArgument) {
        if (latestPublishedEventsPerArgument.size() != argumentMatchers.length) {
            throw new SaplTestException(String.format(ERROR_INVALID_NUMBER_PARAMETERS, this.fullName,
                    argumentMatchers.length, latestPublishedEventsPerArgument.size()));
        }
    }

    private void checkAtLeastOneMatchingMockReturnValueExists(
            Collection<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues,
            AttributeFinderInvocation invocation) {
        if (matchingParameterSpecificMockReturnValues.isEmpty()) {
            throw new SaplTestException(String.format(ERROR_NO_MATCHING_ENTITY_VALUE_S, invocation));
        }
    }

    private List<ParameterSpecificMockReturnValue> findMatchingEntityValueMockReturnValue(Val entityValue) {
        return this.listParameterSpecificMockReturnValues.stream()
                .filter((ParameterSpecificMockReturnValue mock) -> mock.getExpectedParameters().getEntityValueMatcher()
                        .getMatcher().matches(entityValue))
                .toList();
    }

    @Override
    public void assertVerifications() {
        this.listMockingVerifications.forEach(verification -> verification.verify(this.mockRunInformation));
    }

    @Override
    public String getErrorMessageForCurrentMode() {
        return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS, this.fullName);
    }

    @Getter
    @AllArgsConstructor
    static class ParameterSpecificMockReturnValue {

        private AttributeParameters expectedParameters;

        private Val mockReturnValue;

    }

}

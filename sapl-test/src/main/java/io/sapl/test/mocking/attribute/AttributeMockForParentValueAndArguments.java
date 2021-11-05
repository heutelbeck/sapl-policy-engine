/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesParameterCalledVerification;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Flux;

public class AttributeMockForParentValueAndArguments implements AttributeMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS = "You already defined a Mock for %s which is returning specified values when parameters are matching the expectation";

	private static final String ERROR_NO_MATCHING_ARGUMENTS = "Unable to find a mocked return value for this argument combination";

	private static final String ERROR_INVALID_NUMBER_PARAMETERS = "Test case has configured mocked attribute \"%s\" return value depending on %d parameters, but is called at runtime with %d parameters";

	private static final String ERROR_NO_MATCHING_PARENT_VALUE = "Unable to find a mocked return value for this parent value";

	private final String fullName;

	private final List<ParameterSpecificMockReturnValue> listParameterSpecificMockReturnValues;

	private final MockRunInformation mockRunInformation;

	private final List<TimesParameterCalledVerification> listMockingVerifications;

	public AttributeMockForParentValueAndArguments(String fullName) {
		this.fullName = fullName;
		this.listParameterSpecificMockReturnValues = new LinkedList<>();
		this.listMockingVerifications = new LinkedList<>();
		this.mockRunInformation = new MockRunInformation(fullName);
	}

	public void loadMockForParentValueAndArguments(AttributeParameters parameters, Val returnValue) {
		this.listParameterSpecificMockReturnValues.add(new ParameterSpecificMockReturnValue(parameters, returnValue));

		List<Matcher<Val>> listOfAllMatcher = new LinkedList<>();
		listOfAllMatcher.add(parameters.getParentValueMatcher().getMatcher());
		listOfAllMatcher.addAll(List.of(parameters.getArgumentMatchers().getMatchers()));
		this.listMockingVerifications.add(new TimesParameterCalledVerification(Imports.times(1), listOfAllMatcher));

	}

	@Override
	public Flux<Val> evaluate(Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args) {

		List<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues = findMatchingParentValueMockReturnValue(
				parentValue);

		checkAtLeastOneMatchingMockReturnValueExists(matchingParameterSpecificMockReturnValues);

		return Flux.combineLatest(args, (latestPublishedEventsPerArgument) -> {

			// interpret a call to an AttributeMock as
			// not when the evaluate method is called
			// but for every combination of Val objects from parentValue and by argument flux
			// emitted
			saveCall(parentValue, latestPublishedEventsPerArgument);

			for (ParameterSpecificMockReturnValue parameterSpecificMockReturnValue : matchingParameterSpecificMockReturnValues) {

				var argumentMatchers = parameterSpecificMockReturnValue.getExpectedParameters().getArgumentMatchers();

				checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(argumentMatchers.getMatchers(),
						latestPublishedEventsPerArgument);

				if (isEveryArgumentValueMatchingItsMatcher(argumentMatchers.getMatchers(),
						latestPublishedEventsPerArgument)) {
					return parameterSpecificMockReturnValue.getMockReturnValue();
				}
			}
			throw new SaplTestException(ERROR_NO_MATCHING_ARGUMENTS);
		});
	}

	private void saveCall(Val parentValue, Object[] arguments) {
		Val[] parameter = new Val[1 + arguments.length];
		parameter[0] = parentValue;
		for (int i = 0; i < arguments.length; i++) {
			parameter[i + 1] = (Val) arguments[i];
		}
		this.mockRunInformation.saveCall(new MockCall(parameter));
	}

	private boolean isEveryArgumentValueMatchingItsMatcher(Matcher<Val>[] argumentMatchers,
			Object[] latestPublishedEventsPerArgument) {
		boolean isMatching = true;
		for (int i = 0; i < argumentMatchers.length; i++) {
			Matcher<Val> argumentMatcher = argumentMatchers[i];
			Val argumentValue = (Val) latestPublishedEventsPerArgument[i];
			if (!argumentMatcher.matches(argumentValue))
				isMatching = false;
		}
		return isMatching;
	}

	private void checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(Matcher<Val>[] argumentMatchers,
			Object[] latestPublishedEventsPerArgument) {
		if (latestPublishedEventsPerArgument.length != argumentMatchers.length) {
			throw new SaplTestException(String.format(ERROR_INVALID_NUMBER_PARAMETERS, this.fullName,
					argumentMatchers.length, latestPublishedEventsPerArgument.length));
		}
	}

	private void checkAtLeastOneMatchingMockReturnValueExists(
			Collection<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues) {
		if (matchingParameterSpecificMockReturnValues.isEmpty()) {
			throw new SaplTestException(ERROR_NO_MATCHING_PARENT_VALUE);
		}
	}

	private List<ParameterSpecificMockReturnValue> findMatchingParentValueMockReturnValue(Val parentValue) {
		return this.listParameterSpecificMockReturnValues.stream()
				.filter((ParameterSpecificMockReturnValue mock) -> mock.getExpectedParameters().getParentValueMatcher()
						.getMatcher().matches(parentValue))
				.collect(Collectors.toList());
	}

	@Override
	public void assertVerifications() {
		this.listMockingVerifications.forEach((verification) -> verification.verify(this.mockRunInformation));
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

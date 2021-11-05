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
package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.times;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.hamcrest.number.OrderingComparison;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockingFunctionContext implements FunctionContext {

	private static final String ERROR_MOCK_INVALID_FULL_NAME = "Got invalid function reference containing more than one \".\" delimiter: \"%s\"";

	private static final String ERROR_LOADING_LIB_NOT_SUPPORTED = "Loading a FunctionLibrary on a MockingFunctionContext is not supported";

	private static final String NAME_DELIMITER = ".";

	/**
	 * Holds an FunctionContext implementation to delegate evaluations if this function is
	 * not mocked
	 */
	private final FunctionContext originalFunctionContext;

	/**
	 * Contains a Map of all registered mocks. Key is the String of the full name of the
	 * function Value is the {@link FunctionMock} deciding the {@link Val} to be returned
	 */
	private final Map<String, FunctionMock> registeredMocks;

	private final Map<String, LibraryDocumentation> functionDocumentations;

	public MockingFunctionContext(FunctionContext originalFunctionContext) {
		this.originalFunctionContext = originalFunctionContext;
		this.registeredMocks = new HashMap<>();
		this.functionDocumentations = new HashMap<>();
	}

	@Override
	public Boolean isProvidedFunction(String function) {
		if (this.registeredMocks.containsKey(function)) {
			log.trace("Function \"{}\" is mocked", function);
			return Boolean.TRUE;
		}
		else if (originalFunctionContext.isProvidedFunction(function)) {
			log.trace("Function \"{}\" is provided by original function context", function);
			return Boolean.TRUE;
		}
		else {
			log.trace("Function \"{}\" is NOT provided", function);
			return Boolean.FALSE;
		}
	}

	@Override
	public Collection<String> providedFunctionsOfLibrary(String libName) {
		Set<String> set = new HashSet<>();

		for (String fullName : this.registeredMocks.keySet()) {
			String[] split = fullName.split(Pattern.quote(NAME_DELIMITER));
			if (split[0].equals(libName))
				set.add(split[1]);
		}

		set.addAll(this.originalFunctionContext.providedFunctionsOfLibrary(libName));

		return set;
	}

	@Override
	public Val evaluate(String function, Val... parameters) {
		FunctionMock mock = this.registeredMocks.get(function);
		if (mock != null) {
			log.debug("| | | | |-- Evaluate mocked function \"{}\"", function);
			var result = mock.evaluateFunctionCall(parameters);
			log.trace("| | | | |-- FunctionMock returned: " + result.toString());
			return result;
		}
		else {
			log.debug("| | | | |-- Delegate function \"{}\" to original function context", function);
			return this.originalFunctionContext.evaluate(function, parameters);
		}
	}

	@Override
	public void loadLibrary(Object library) {
		throw new SaplTestException(ERROR_LOADING_LIB_NOT_SUPPORTED);
	}

	@Override
	public Collection<LibraryDocumentation> getDocumentation() {
		Collection<LibraryDocumentation> doc = new LinkedList<>(
				this.functionDocumentations.values());
		doc.addAll(this.originalFunctionContext.getDocumentation());
		return Collections.unmodifiableCollection(doc);
	}

	public void loadFunctionMockAlwaysSameValue(String fullName, Val mockReturnValue) {
		this.loadFunctionMockAlwaysSameValue(fullName, mockReturnValue,
				times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockAlwaysSameValue(String fullName, Val mockReturnValue,
			TimesCalledVerification verification) {
		checkImportName(fullName);

		FunctionMock mock = this.registeredMocks.get(fullName);
		if (this.registeredMocks.containsKey(fullName)) {
			throw new SaplTestException(mock.getErrorMessageForCurrentMode());
		}
		else {
			FunctionMock newMock = new FunctionMockAlwaysSameValue(fullName, mockReturnValue, verification);
			this.registeredMocks.put(fullName, newMock);

			addNewLibraryDocumentation(fullName, newMock);
		}
	}

	public void loadFunctionMockOnceReturnValue(String fullName, Val mockReturnValue) {
		this.loadFunctionMockReturnsSequence(fullName, new Val[] { mockReturnValue });
	}

	public void loadFunctionMockReturnsSequence(String fullName, Val[] mockReturnValue) {
		checkImportName(fullName);

		FunctionMock mock = this.registeredMocks.get(fullName);
		if (mock != null) {
			if (mock instanceof FunctionMockSequence) {
				((FunctionMockSequence) mock).loadMockReturnValue(mockReturnValue);
			}
			else {
				throw new SaplTestException(mock.getErrorMessageForCurrentMode());
			}
		}
		else {
			FunctionMockSequence newMock = new FunctionMockSequence(fullName);
			newMock.loadMockReturnValue(mockReturnValue);
			this.registeredMocks.put(fullName, newMock);

			addNewLibraryDocumentation(fullName, newMock);
		}
	}

	public void loadFunctionMockAlwaysSameValueForParameters(String fullName, Val mockReturnValue,
			FunctionParameters parameter) {
		this.loadFunctionMockAlwaysSameValueForParameters(fullName, mockReturnValue, parameter,
				times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockAlwaysSameValueForParameters(String fullName, Val mockReturnValue,
			FunctionParameters parameter, TimesCalledVerification verification) {
		checkImportName(fullName);

		FunctionMock mock = this.registeredMocks.get(fullName);
		if (mock != null) {
			if (mock instanceof FunctionMockAlwaysSameForParameters) {
				((FunctionMockAlwaysSameForParameters) mock).loadParameterSpecificReturnValue(mockReturnValue,
						parameter, verification);
			}
			else {
				throw new SaplTestException(mock.getErrorMessageForCurrentMode());
			}
		}
		else {
			FunctionMockAlwaysSameForParameters newMock = new FunctionMockAlwaysSameForParameters(fullName);
			newMock.loadParameterSpecificReturnValue(mockReturnValue, parameter, verification);
			this.registeredMocks.put(fullName, newMock);

			addNewLibraryDocumentation(fullName, newMock);
		}
	}

	public void loadFunctionMockValueFromFunction(String fullName, Function<Val[], Val> returns) {
		this.loadFunctionMockValueFromFunction(fullName, returns, times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockValueFromFunction(String fullName, Function<Val[], Val> returns,
			TimesCalledVerification verification) {
		checkImportName(fullName);

		FunctionMock mock = this.registeredMocks.get(fullName);
		if (mock != null) {
			throw new SaplTestException(mock.getErrorMessageForCurrentMode());
		}
		else {
			FunctionMock newMock = new FunctionMockFunctionResult(fullName, returns, verification);
			this.registeredMocks.put(fullName, newMock);

			addNewLibraryDocumentation(fullName, newMock);
		}
	}

	public void assertVerifications() {
		this.registeredMocks.forEach((fullName, mock) -> mock.assertVerifications());
	}

	void checkImportName(String importName) {
		String[] split = importName.split(Pattern.quote(NAME_DELIMITER));
		if (split.length != 2) {
			throw new SaplTestException(String.format(ERROR_MOCK_INVALID_FULL_NAME, importName));
		}
	}

	void addNewLibraryDocumentation(String fullName, FunctionMock mock) {
		String[] split = fullName.split(Pattern.quote(NAME_DELIMITER));
		String libName = split[0];
		String functionName = split[1];

		var existingDoc = this.functionDocumentations.get(libName);
		if (existingDoc != null) {
			existingDoc.getDocumentation().put(functionName, "Mocked Function");
		}
		else {
			LibraryDocumentation functionDocs = new LibraryDocumentation(libName, "Mocked Function Library: " + libName,
					mock);
			functionDocs.getDocumentation().put(functionName, "Mocked Function");
			this.functionDocumentations.put(libName, functionDocs);
		}
	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return this.registeredMocks.keySet();
	}

}

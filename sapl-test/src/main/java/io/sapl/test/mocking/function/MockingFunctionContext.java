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

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;

import org.hamcrest.number.OrderingComparison;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockingFunctionContext implements FunctionContext {

	private static final String ERROR_MOCK_INVALID_FULLNAME = "Got invalid function reference containing more than one \".\" delimiter: \"%s\"";

	private static final String ERROR_LOADING_LIB_NOT_SUPPORTED = "Loading a FunctionLibrary on a MockingFunctionContext is not supported";

	private static final String NAME_DELIMITER = ".";

	/**
	 * Holds an FunctionContext implementation to delegate evaluations if this function is
	 * not mocked
	 */
	private final FunctionContext unmockedFunctionContext;

	/**
	 * Contains a Map of all registered mocks. Key is the String of the fullname of the
	 * function Value is the {@link FunctionMock} deciding the {@link Val} to be returned
	 */
	private final Map<String, FunctionMock> registeredMocks;

	private final Map<String, LibraryDocumentation> functionDocumentations;

	public MockingFunctionContext(FunctionContext unmockedFunctionContext) {
		this.unmockedFunctionContext = unmockedFunctionContext;
		this.registeredMocks = new HashMap<>();
		this.functionDocumentations = new HashMap<>();
	}

	@Override
	public Boolean isProvidedFunction(String function) {
		if (this.registeredMocks.containsKey(function)) {
			log.trace("Function \"{}\" is mocked", function);
			return Boolean.TRUE;
		}
		else if (unmockedFunctionContext.isProvidedFunction(function)) {
			log.trace("Function \"{}\" is provided by unmocked function context", function);
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
			String[] splitted = fullName.split(Pattern.quote(NAME_DELIMITER));
			if (splitted[0].equals(libName))
				set.add(splitted[1]);
		}

		set.addAll(this.unmockedFunctionContext.providedFunctionsOfLibrary(libName));

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
			log.debug("| | | | |-- Delegate function \"{}\" to unmocked function context", function);
			return this.unmockedFunctionContext.evaluate(function, parameters);
		}
	}

	@Override
	public void loadLibrary(Object library) throws InitializationException {
		throw new SaplTestException(ERROR_LOADING_LIB_NOT_SUPPORTED);
	}

	@Override
	public Collection<LibraryDocumentation> getDocumentation() {
		Collection<LibraryDocumentation> doc = new LinkedList<>(
				this.functionDocumentations.values());
		doc.addAll(this.unmockedFunctionContext.getDocumentation());
		return Collections.unmodifiableCollection(doc);
	}

	public void loadFunctionMockAlwaysSameValue(String fullname, Val mockReturnValue) {
		this.loadFunctionMockAlwaysSameValue(fullname, mockReturnValue,
				times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockAlwaysSameValue(String fullname, Val mockReturnValue,
			TimesCalledVerification verification) {
		checkImportName(fullname);

		FunctionMock mock = this.registeredMocks.get(fullname);
		if (this.registeredMocks.containsKey(fullname)) {
			throw new SaplTestException(mock.getErrorMessageForCurrentMode());
		}
		else {
			FunctionMock newMock = new FunctionMockAlwaysSameValue(fullname, mockReturnValue, verification);
			this.registeredMocks.put(fullname, newMock);

			addNewLibraryDocumentation(fullname, newMock);
		}
	}

	public void loadFunctionMockOnceReturnValue(String fullname, Val mockReturnValue) {
		this.loadFunctionMockReturnsSequence(fullname, new Val[] { mockReturnValue });
	}

	public void loadFunctionMockReturnsSequence(String fullname, Val[] mockReturnValue) {
		checkImportName(fullname);

		FunctionMock mock = this.registeredMocks.get(fullname);
		if (mock != null) {
			if (mock instanceof FunctionMockSequence) {
				((FunctionMockSequence) mock).loadMockReturnValue(mockReturnValue);
			}
			else {
				throw new SaplTestException(mock.getErrorMessageForCurrentMode());
			}
		}
		else {
			FunctionMockSequence newMock = new FunctionMockSequence(fullname);
			newMock.loadMockReturnValue(mockReturnValue);
			this.registeredMocks.put(fullname, newMock);

			addNewLibraryDocumentation(fullname, newMock);
		}
	}

	public void loadFunctionMockAlwaysSameValueForParameters(String fullname, Val mockReturnValue,
			FunctionParameters parameter) {
		this.loadFunctionMockAlwaysSameValueForParameters(fullname, mockReturnValue, parameter,
				times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockAlwaysSameValueForParameters(String fullname, Val mockReturnValue,
			FunctionParameters parameter, TimesCalledVerification verification) {
		checkImportName(fullname);

		FunctionMock mock = this.registeredMocks.get(fullname);
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
			FunctionMockAlwaysSameForParameters newMock = new FunctionMockAlwaysSameForParameters(fullname);
			newMock.loadParameterSpecificReturnValue(mockReturnValue, parameter, verification);
			this.registeredMocks.put(fullname, newMock);

			addNewLibraryDocumentation(fullname, newMock);
		}
	}

	public void loadFunctionMockValueFromFunction(String fullname, Function<Val[], Val> returns) {
		this.loadFunctionMockValueFromFunction(fullname, returns, times(OrderingComparison.greaterThanOrEqualTo(1)));
	}

	public void loadFunctionMockValueFromFunction(String fullname, Function<Val[], Val> returns,
			TimesCalledVerification verification) {
		checkImportName(fullname);

		FunctionMock mock = this.registeredMocks.get(fullname);
		if (mock != null) {
			throw new SaplTestException(mock.getErrorMessageForCurrentMode());
		}
		else {
			FunctionMock newMock = new FunctionMockFunctionResult(fullname, returns, verification);
			this.registeredMocks.put(fullname, newMock);

			addNewLibraryDocumentation(fullname, newMock);
		}
	}

	public void assertVerifications() {
		this.registeredMocks.forEach((fullname, mock) -> mock.assertVerifications());
	}

	void checkImportName(String importName) {
		String[] splitted = importName.split(Pattern.quote(NAME_DELIMITER));
		if (splitted.length != 2) {
			throw new SaplTestException(String.format(ERROR_MOCK_INVALID_FULLNAME, importName));
		}
	}

	void addNewLibraryDocumentation(String fullname, FunctionMock mock) {
		String[] splitted = fullname.split(Pattern.quote(NAME_DELIMITER));
		String libName = splitted[0];
		String functionName = splitted[1];

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

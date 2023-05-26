/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.LinkedList;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;

public class FunctionMockSequence implements FunctionMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_SEQUENCE = "You already defined a Mock for %s which is returning a sequence of values";

	private static final String ERROR_SEQUENCE_EMPTY = "You defined a Mock for %s returning a sequence of Val's but was called more often than mock return values specified";

	private final String fullName;

	private final LinkedList<Val> listMockReturnValues;

	private final MockRunInformation mockRunInformation;

	private int numberOfReturnValues = 0;

	public FunctionMockSequence(String fullName) {
		this.fullName             = fullName;
		this.listMockReturnValues = new LinkedList<>();

		this.mockRunInformation = new MockRunInformation(fullName);
	}

	@Override
	public Val evaluateFunctionCall(Val... parameter) {
		this.mockRunInformation.saveCall(new MockCall(parameter));

		if (this.listMockReturnValues.size() > 0) {
			// if so, take the first element from the fifo list and return this val
			return this.listMockReturnValues.removeFirst();
		} else {
			throw new SaplTestException(String.format(ERROR_SEQUENCE_EMPTY, this.fullName));
		}
	}

	@Override
	public void assertVerifications() {
		times(this.numberOfReturnValues).verify(this.mockRunInformation);
	}

	public void loadMockReturnValue(Val[] mockReturnValueSequence) {
		var tracedMockValues = new ArrayList<Val>(mockReturnValueSequence.length);
		for (var val : mockReturnValueSequence) {
			tracedMockValues.add(val.withTrace(FunctionMockSequence.class));
		}
		this.listMockReturnValues.addAll(tracedMockValues);
		this.numberOfReturnValues = this.numberOfReturnValues + mockReturnValueSequence.length;
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_SEQUENCE, this.fullName);
	}

}

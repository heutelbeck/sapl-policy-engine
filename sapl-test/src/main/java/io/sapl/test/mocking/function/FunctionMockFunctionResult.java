/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesCalledVerification;

public class FunctionMockFunctionResult implements FunctionMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FUNCTION = "You already defined a Mock for %s which is always returning a specified value from your lambda-Expression";

	private final String fullName;

	final Function<Val[], Val> returnValue;

	private final TimesCalledVerification timesCalledVerification;

	private final MockRunInformation mockRunInformation;

	public FunctionMockFunctionResult(String fullName, Function<Val[], Val> returns,
			TimesCalledVerification verification) {
		this.fullName = fullName;
		this.returnValue = returns;
		this.timesCalledVerification = verification;

		this.mockRunInformation = new MockRunInformation(fullName);
	}

	@Override
	public Val evaluateFunctionCall(Val... parameter) {
		this.mockRunInformation.saveCall(new MockCall(parameter));
		return this.returnValue.apply(parameter);
	}

	@Override
	public void assertVerifications() {
		this.timesCalledVerification.verify(this.mockRunInformation);

	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_FUNCTION, this.fullName);
	}

}

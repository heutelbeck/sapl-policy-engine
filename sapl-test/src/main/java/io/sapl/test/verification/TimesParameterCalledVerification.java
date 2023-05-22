/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.verification.MockRunInformation.CallWithMetadata;

/**
 * Verify that this mock was called n times with the specified list of Matcher.
 *
 */
public class TimesParameterCalledVerification implements MockingVerification {

	final List<Matcher<Val>> wantedArgs;

	final TimesCalledVerification verification;

	public TimesParameterCalledVerification(TimesCalledVerification verification, List<Matcher<Val>> wantedArgs) {
		this.verification = verification;
		this.wantedArgs = wantedArgs;
	}

	@Override
	public void verify(MockRunInformation mockRunInformation) {
		this.verify(mockRunInformation, null);
	}

	@Override
	public void verify(MockRunInformation mockRunInformation, String verificationFailedMessage) {
		// collect calls with specified information in new MockRunInformation
		MockRunInformation callsMatchingWantedArgs = new MockRunInformation(mockRunInformation.getFullName());

		for (int i = 0; i < mockRunInformation.getCalls().size(); i++) {
			CallWithMetadata call = mockRunInformation.getCalls().get(i);

			// is this call already used on another TimesParameterCalledVerification ->
			// don't use it
			if (call.isUsed()) {
				continue;
			}

			boolean callMatchesArgs = areAllCallArgumentsMatchingTheArgumentMatcher(call);

			if (callMatchesArgs) {
				callsMatchingWantedArgs.saveCall(call.getCall());
				mockRunInformation.getCalls().get(i).setUsed(true);
			}
		}

		this.verification.verify(callsMatchingWantedArgs, constructErrorMessage(verificationFailedMessage,
				callsMatchingWantedArgs, this.wantedArgs, this.verification));

	}

	private String constructErrorMessage(String verificationFailedMessage, MockRunInformation callsMatchingWantedArgs,
			Iterable<Matcher<Val>> wantedArgs, TimesCalledVerification verification) {

		if (verificationFailedMessage != null && !verificationFailedMessage.isEmpty()) {
			return verificationFailedMessage;
		}

		StringBuilder builder = new StringBuilder("Error verifying the expected number of calls to the mock \""
				+ callsMatchingWantedArgs.getFullName() + "\" for parameters [");

		for (Matcher<Val> matcher : wantedArgs) {
			builder.append(matcher).append(", ");
		}

		builder.deleteCharAt(builder.length() - 1);
		builder.append(']');

		builder.append(" - Expected: ").append(verification).append(" - got: ")
				.append(callsMatchingWantedArgs.getTimesCalled());

		return builder.toString();
	}

	private boolean areAllCallArgumentsMatchingTheArgumentMatcher(CallWithMetadata call) {
		return listCombiner(this.wantedArgs, call.getCall().getListOfArguments(), Matcher::matches).stream()
				.allMatch(Boolean::booleanValue);
	}

	private List<Boolean> listCombiner(List<Matcher<Val>> list1, List<Val> list2,
			BiFunction<Matcher<Val>, Val, Boolean> combiner) {
		if (list1.size() != list2.size()) {
			throw new SaplTestException(
					"Number of parameters in the mock call is not equals the number of provided parameter matcher!");
		}

		List<Boolean> result = new ArrayList<>(list1.size());
		for (int i = 0; i < list1.size(); i++) {
			result.add(combiner.apply(list1.get(i), list2.get(i)));
		}
		return result;
	}

}

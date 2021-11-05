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
package io.sapl.test;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.attribute.models.AttributeArgumentMatchers;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Imports {

	@SafeVarargs
	public static FunctionParameters whenFunctionParams(Matcher<Val>... matcher) {
		return new FunctionParameters(matcher);
	}

	public static AttributeParameters whenAttributeParams(AttributeParentValueMatcher parentValueMatcher,
			AttributeArgumentMatchers argumentMatchers) {
		return new AttributeParameters(parentValueMatcher, argumentMatchers);
	}

	public static AttributeParentValueMatcher whenParentValue(Matcher<Val> matcher) {
		return new AttributeParentValueMatcher(matcher);
	}

	public static AttributeParentValueMatcher parentValue(Matcher<Val> matcher) {
		return new AttributeParentValueMatcher(matcher);
	}

	@SafeVarargs
	public static AttributeArgumentMatchers arguments(Matcher<Val>... argumentMatcher) {
		return new AttributeArgumentMatchers(argumentMatcher);
	}

	public static Val thenReturn(Val val) {
		return val;
	}

	/**
	 * see {@link TimesCalledVerification}
	 * @param wantedNumberOfInvocations the expected number of invocations
	 * @return the verification
	 */
	public static TimesCalledVerification times(int wantedNumberOfInvocations) {
		return new TimesCalledVerification(comparesEqualTo(wantedNumberOfInvocations));
	}

	/**
	 * see {@link TimesCalledVerification}
	 * @param matcher an Integer matcher
	 * @return the verification
	 */
	public static TimesCalledVerification times(Matcher<Integer> matcher) {
		return new TimesCalledVerification(matcher);
	}

	/**
	 * see {@link TimesCalledVerification}
	 * @return the verification
	 */
	public static TimesCalledVerification never() {
		return new TimesCalledVerification(comparesEqualTo(0));
	}

	/**
	 * see {@link TimesCalledVerification}
	 * @return the verification
	 */
	public static TimesCalledVerification anyTimes() {
		return new TimesCalledVerification(any(Integer.class));
	}

}

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
package io.sapl.test;

import io.sapl.api.model.Value;
import io.sapl.test.mocking.attribute.models.AttributeArgumentMatchers;
import io.sapl.test.mocking.attribute.models.AttributeEntityValueMatcher;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;
import lombok.experimental.UtilityClass;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;

@UtilityClass
public class Imports {

    /**
     * Specify Matchers for the arguments of a function mock.
     *
     * @param matcher Varargs of {@link Matcher}
     * @return an {@link FunctionParameters} object required by the given step
     */
    @SafeVarargs
    public static FunctionParameters whenFunctionParams(Matcher<Value>... matcher) {
        return new FunctionParameters(matcher);
    }

    /**
     * Specify Matchers for the parent value and all arguments of an attribute mock.
     *
     * @param parentValueMatcher Matcher for the parent value. See
     * {@link #entityValue(Matcher)}
     * @param argumentMatchers Matcher for the arguments. See
     * {@link #arguments(Matcher[])}
     * @return an {@link AttributeParameters} object required by the given step
     */
    public static AttributeParameters whenAttributeParams(AttributeEntityValueMatcher parentValueMatcher,
            AttributeArgumentMatchers argumentMatchers) {
        return new AttributeParameters(parentValueMatcher, argumentMatchers);
    }

    /**
     * Specify Matchers for all arguments of an environment attribute mock.
     *
     * @param argumentMatchers Matcher for the arguments. See
     * {@link #arguments(Matcher[])}
     * @return an {@link AttributeParameters} object required by the given step
     */
    public static AttributeParameters whenEnvironmentAttributeParams(AttributeArgumentMatchers argumentMatchers) {
        return new AttributeParameters(new AttributeEntityValueMatcher(isNullValue()), argumentMatchers);
    }

    /**
     * Returns a matcher that matches null values.
     *
     * @return a matcher for null values
     */
    public static Matcher<Value> isNullValue() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                return item == null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a null Value");
            }
        };
    }

    /**
     * Specify Matcher for the parent value of an attribute mock.
     *
     * @param matcher Matcher for the parent value
     * @return an {@link AttributeEntityValueMatcher} object required by the given
     * step
     */
    public static AttributeEntityValueMatcher whenEntityValue(Matcher<Value> matcher) {
        return new AttributeEntityValueMatcher(matcher);
    }

    /**
     * Specify a matcher for the parent value used in
     * {@link #whenAttributeParams(AttributeEntityValueMatcher, AttributeArgumentMatchers)}.
     *
     * @param matcher the matcher for the parent value
     * @return an {@link AttributeEntityValueMatcher} object required by the
     * {@link #whenAttributeParams(AttributeEntityValueMatcher, AttributeArgumentMatchers)}
     * method
     */
    public static AttributeEntityValueMatcher entityValue(Matcher<Value> matcher) {
        return new AttributeEntityValueMatcher(matcher);
    }

    /**
     * Specify matchers for the arguments used in
     * {@link #whenAttributeParams(AttributeEntityValueMatcher, AttributeArgumentMatchers)}.
     *
     * @param argumentMatcher the matchers for the arguments
     * @return an {@link AttributeArgumentMatchers} object required by the
     * {@link #whenAttributeParams(AttributeEntityValueMatcher, AttributeArgumentMatchers)}
     * method
     */
    @SafeVarargs
    public static AttributeArgumentMatchers arguments(Matcher<Value>... argumentMatcher) {
        return new AttributeArgumentMatchers(argumentMatcher);
    }

    /**
     * Convenience method to improve readability in complex mock definitions.
     *
     * @param value the {@link Value} to return
     * @return the unmodified passed {@link Value}
     */
    public static Value thenReturn(Value value) {
        return value;
    }

    /**
     * see {@link TimesCalledVerification}
     *
     * @param wantedNumberOfInvocations the expected number of invocations
     * @return the verification
     */
    public static TimesCalledVerification times(int wantedNumberOfInvocations) {
        return new TimesCalledVerification(comparesEqualTo(wantedNumberOfInvocations));
    }

    /**
     * see {@link TimesCalledVerification}
     *
     * @param matcher an Integer matcher
     * @return the verification
     */
    public static TimesCalledVerification times(Matcher<Integer> matcher) {
        return new TimesCalledVerification(matcher);
    }

    /**
     * see {@link TimesCalledVerification}
     *
     * @return the verification
     */
    public static TimesCalledVerification never() {
        return new TimesCalledVerification(comparesEqualTo(0));
    }

    /**
     * see {@link TimesCalledVerification}
     *
     * @return the verification
     */
    public static TimesCalledVerification anyTimes() {
        return new TimesCalledVerification(any(Integer.class));
    }

}

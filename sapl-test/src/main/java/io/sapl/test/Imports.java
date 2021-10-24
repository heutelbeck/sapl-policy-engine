package io.sapl.test;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.attribute.models.AttributeArgumentMatchers;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;

import org.hamcrest.Matcher;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Imports {

	@SafeVarargs
	public static FunctionParameters whenFunctionParams(Matcher<Val>... matcher) {
		return new FunctionParameters(matcher);
	}
	
	public static AttributeParameters whenAttributeParams(AttributeParentValueMatcher parentValueMatcher, AttributeArgumentMatchers argumentMatchers) {
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
	 * @param wantedNumberOfInvocations
	 * @return
	 */
	public static TimesCalledVerification times(int wantedNumberOfInvocations) {
		return new TimesCalledVerification(comparesEqualTo(wantedNumberOfInvocations));
	}
	
	/**
	 * see {@link TimesCalledVerification}
	 * @param matcher
	 * @return
	 */
	public static TimesCalledVerification times(Matcher<Integer> matcher) {
		return new TimesCalledVerification(matcher);
	}
	
	
	/**
	 * see {@link TimesCalledVerification}
	 * @param matcher
	 * @return
	 */
	public static TimesCalledVerification never() {
		return new TimesCalledVerification(comparesEqualTo(0));
	}
	
	/**
	 * see {@link TimesCalledVerification}
	 * @param matcher
	 * @return
	 */
	public static TimesCalledVerification anyTimes() {
		return new TimesCalledVerification(any(Integer.class));
	}
}


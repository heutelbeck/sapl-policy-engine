package io.sapl.test;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.FunctionParameters;
import io.sapl.test.verification.TimesCalledVerification;

public class Imports {

	@SafeVarargs
	public static FunctionParameters whenParameters(Matcher<Val>... matcher) {
		return new FunctionParameters(matcher);
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


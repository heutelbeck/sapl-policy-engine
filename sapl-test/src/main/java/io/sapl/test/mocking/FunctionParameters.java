package io.sapl.test.mocking;

import java.util.Arrays;
import java.util.List;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;

public class FunctionParameters {

	private List<Matcher<Val>> matchers;
	
	@SafeVarargs
	public FunctionParameters(Matcher<Val>... matcher) {
		this.matchers = Arrays.asList(matcher);
	}
	
	public List<Matcher<Val>> getParameterMatchers() {
		return this.matchers;
	}
}

package io.sapl.interpreter.functions;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import io.sapl.api.interpreter.Val;

public class IsError extends TypeSafeMatcher<Val> {

	public static Matcher<Val> isError() {
		return new IsError();
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("not an error");
	}

	@Override
	protected boolean matchesSafely(Val item) {
		return item.isError();
	}

}

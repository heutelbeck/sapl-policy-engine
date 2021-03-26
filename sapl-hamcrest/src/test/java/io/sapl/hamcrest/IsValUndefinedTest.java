package io.sapl.hamcrest;

import static io.sapl.hamcrest.Matchers.valUndefined;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class IsValUndefinedTest {

	@Test
	void testTypeFalseError() {
		var sut = valUndefined();
		assertThat(Val.error(), not(is(sut)));
	}

	@Test
	void testTypeFalseValue() {
		var sut = valUndefined();
		assertThat(Val.TRUE, not(is(sut)));
	}

	@Test
	void testType() {
		var sut = valUndefined();
		assertThat(Val.UNDEFINED, is(sut));
	}

	@Test
	void testDescriptionForEmptyConstructor() {
		var sut = valUndefined();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("undefined"));
	}
}

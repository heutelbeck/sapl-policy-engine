package io.sapl.hamcrest;

import static io.sapl.hamcrest.IsValError.valError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class IsValErrorTest {

	private static final String MESSAGE = "test message";
	private static final String MESSAGE_MIXED_CASE = "TeSt MeSsAge";

	@Test
	void testType() {
		var sut = valError();
		assertThat(Val.error(), is(sut));
	}

	@Test
	void testTypeFalse() {
		var sut = valError();
		assertThat(Val.UNDEFINED, not(is(sut)));
	}

	@Test
	void testMessageTrue() {
		var sut = valError(MESSAGE);
		assertThat(Val.error(MESSAGE), is(sut));
	}

	@Test
	void testMessageFalse() {
		var sut = valError(MESSAGE);
		assertThat(Val.error("X"), not(is(sut)));
	}

	@Test
	void testMatcher() {
		var sut = valError(equalToIgnoringCase(MESSAGE_MIXED_CASE));
		assertThat(Val.error(MESSAGE), is(sut));
	}

	@Test
	void testDescriptionForEmptyConstructor() {
		var sut = valError();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("an error with message that is ANYTHING"));
	}
}

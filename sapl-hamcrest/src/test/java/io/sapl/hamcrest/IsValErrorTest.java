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
package io.sapl.hamcrest;

import static io.sapl.hamcrest.Matchers.valError;
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

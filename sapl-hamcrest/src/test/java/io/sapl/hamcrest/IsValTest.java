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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonBoolean;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valFalse;
import static io.sapl.hamcrest.Matchers.valNull;
import static io.sapl.hamcrest.Matchers.valTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class IsValTest {

	private static final Val VALUE = Val.of("test value");

	@Test
	void testTypeError() {
		var sut = val();
		assertThat(Val.error(), not(is(sut)));
	}

	@Test
	void testTypeUndefined() {
		var sut = val();
		assertThat(Val.UNDEFINED, not(is(sut)));
	}

	@Test
	void testType() {
		var sut = val();
		assertThat(VALUE, is(sut));
	}

	@Test
	void testValueTrue() {
		var sut = val(is(jsonText()));
		assertThat(VALUE, is(sut));
	}

	@Test
	void testValueFalse() {
		var sut = val(is(jsonBoolean()));
		assertThat(VALUE, not(is(sut)));
	}

	@Test
	void testBool() {
		var sut = val(true);
		assertThat(Val.TRUE, is(sut));
	}

	@Test
	void testTrue() {
		var sut = valTrue();
		assertThat(Val.TRUE, is(sut));
	}

	@Test
	void testFalse() {
		var sut = valFalse();
		assertThat(Val.FALSE, is(sut));
	}

	@Test
	void testText() {
		var sut = val("XXX");
		assertThat(Val.of("XXX"), is(sut));
	}

	@Test
	void testInt() {
		var sut = val(1);
		assertThat(Val.of(1), is(sut));
	}

	@Test
	void testLong() {
		var sut = val(2L);
		assertThat(Val.of(2L), is(sut));
	}

	@Test
	void testDouble() {
		var sut = val(2.0D);
		assertThat(Val.of(2.0D), is(sut));
	}

	@Test
	void testFloat() {
		var sut = val(3.0F);
		assertThat(Val.of(3.0F), is(sut));
	}

	@Test
	void testBigDecimal() {
		var sut = val(BigDecimal.valueOf(3.12D));
		assertThat(Val.of(BigDecimal.valueOf(3.12D)), is(sut));
	}

	@Test
	void testBigInteger() {
		var sut = val(BigInteger.valueOf(3L));
		assertThat(Val.of(BigInteger.valueOf(3L)), is(sut));
	}

	@Test
	void testNull() {
		var sut = valNull();
		assertThat(Val.NULL, is(sut));
	}

	@Test
	void testAnyVal() {
		var sut = anyVal();
		assertThat(Val.NULL, is(sut));
		assertThat(Val.of(1), is(sut));
		assertThat(Val.FALSE, is(sut));
		assertThat(Val.error(), not(is(sut)));
		assertThat(Val.UNDEFINED, not(is(sut)));
	}

	@Test
	void testDescriptionForEmptyConstructor() {
		var sut = val();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("a val that is any JsonNode"));
	}

	@Test
	void testDescriptionForMatcher() {
		var sut = val(is(jsonBoolean()));
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("a val that is a boolean node with value that is ANYTHING"));
	}

}

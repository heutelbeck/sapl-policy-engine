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
package io.sapl.hamcrest;

import static com.spotify.hamcrest.jackson.IsJsonBoolean.jsonBoolean;
import static com.spotify.hamcrest.jackson.IsJsonNull.jsonNull;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonBigInteger;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonDouble;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonFloat;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonInt;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonLong;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;

public class IsVal extends TypeSafeDiagnosingMatcher<Val> {

	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public IsVal(Matcher<? super JsonNode> jsonMatcher) {
		super(Val.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public IsVal() {
		super(Val.class);
		this.jsonMatcher = Optional.empty();
	}

	public static Matcher<Val> val() {
		return new IsVal();
	}

	public static Matcher<Val> val(String text) {
		return new IsVal(jsonText(text));
	}

	public static Matcher<Val> val(int integer) {
		return new IsVal(jsonInt(integer));
	}

	public static Matcher<Val> val(BigDecimal decimal) {
		return new IsVal(jsonBigDecimal(decimal));
	}

	public static Matcher<Val> val(BigInteger bigInt) {
		return new IsVal(jsonBigInteger(bigInt));
	}

	public static Matcher<Val> val(float floatValue) {
		return new IsVal(jsonFloat(floatValue));
	}

	public static Matcher<Val> valNull() {
		return new IsVal(jsonNull());
	}

	public static Matcher<Val> val(boolean bool) {
		return new IsVal(jsonBoolean(bool));
	}

	public static Matcher<Val> valTrue() {
		return val(true);
	}

	public static Matcher<Val> valFalse() {
		return val(false);
	}

	public static Matcher<Val> val(double doubleValue) {
		return new IsVal(jsonDouble(doubleValue));
	}

	public static Matcher<Val> val(long longValue) {
		return new IsVal(jsonLong(longValue));
	}

	public static Matcher<Val> val(Matcher<? super JsonNode> jsonMatcher) {
		return new IsVal(jsonMatcher);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("a val that ");
		jsonMatcher.ifPresentOrElse(matcher -> description.appendDescriptionOf(matcher),
				() -> description.appendText("is any JsonNode"));
	}

	@Override
	protected boolean matchesSafely(Val item, Description mismatchDescription) {
		if (item.isError()) {
			mismatchDescription.appendText("an error that is '").appendText(item.getMessage()).appendText("'");
			return false;
		}
		if (item.isUndefined()) {
			mismatchDescription.appendText("undefined");
			return false;
		}
		var json = item.get();
		if (jsonMatcher.isEmpty() || jsonMatcher.get().matches(json)) {
			return true;
		} else {
			mismatchDescription.appendText("was val that ");
			jsonMatcher.get().describeMismatch(json, mismatchDescription);
			return false;
		}
	}

}

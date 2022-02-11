/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasAdviceContainingKeyValueTest {

	@Test
	public void test() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		advice.put("key", "value");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);

		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(adviceArray));

		var sut = Matchers.hasAdviceContainingKeyValue("key", jsonText("value"));

		assertThat(dec, is(sut));
	}

	@Test
	public void test_neg() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		advice.put("key", "value");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);

		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(adviceArray));

		var sut = Matchers.hasAdviceContainingKeyValue("xxx", jsonText("yyy"));

		assertThat(dec, not(is(sut)));
	}

	@Test
	public void test_nullDecision() {
		var sut = Matchers.hasAdviceContainingKeyValue("key", jsonText("value"));

		assertThat(null, not(is(sut)));
	}

	@Test
	public void test_nullKey() {
		assertThrows(NullPointerException.class, () -> Matchers.hasAdviceContainingKeyValue(null, jsonText("value")));
	}

	@Test
	public void test_nullValue() {
		assertThrows(NullPointerException.class,
				() -> Matchers.hasAdviceContainingKeyValue("key", (Matcher<JsonNode>) null));
	}

	@Test
	public void test_emptyAdvice() {
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.empty());

		var sut = Matchers.hasAdviceContainingKeyValue("key", jsonText("value"));

		assertThat(dec, not(is(sut)));
	}

	@Test
	public void test_emptyMatcher() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode actualAdivce = mapper.createObjectNode();
		actualAdivce.put("foo", "bar");
		actualAdivce.put("key", "value");
		ArrayNode actualAdivces = mapper.createArrayNode();
		actualAdivces.add(actualAdivce);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(actualAdivces));

		var sut = Matchers.hasAdviceContainingKeyValue("key");

		assertThat(dec, is(sut));
	}

	@Test
	public void test_StringValueMatcher() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode actualAdivce = mapper.createObjectNode();
		actualAdivce.put("foo", "bar");
		actualAdivce.put("key", "value");
		ArrayNode actualAdivces = mapper.createArrayNode();
		actualAdivces.add(actualAdivce);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(actualAdivces));

		var sut = Matchers.hasAdviceContainingKeyValue("key", "value");

		assertThat(dec, is(sut));
	}

	@Test
	public void test_MatchingKey_NotMatchingValue() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode actualAdivce = mapper.createObjectNode();
		actualAdivce.put("foo", "bar");
		actualAdivce.put("key", "value");
		ArrayNode actualAdivces = mapper.createArrayNode();
		actualAdivces.add(actualAdivce);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(actualAdivces));

		var sut = Matchers.hasAdviceContainingKeyValue("key", "xxx");

		assertThat(dec, not(is(sut)));
	}

	@Test
	void testDescriptionForMatcherEmptyMatcher() {
		var sut = Matchers.hasAdviceContainingKeyValue("key");
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("the decision has an advice containing key key with any value"));
	}

	@Test
	void testDescriptionForMatcher() {
		var sut = Matchers.hasAdviceContainingKeyValue("key", jsonText("value"));
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(),
				is("the decision has an advice containing key key with a text node with value that is \"value\""));
	}

}

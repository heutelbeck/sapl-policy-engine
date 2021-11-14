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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.hasAdvice;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasAdviceTest {

	@Test
	public void test() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(adviceArray));

		var matcher = hasAdvice(advice);

		assertThat(dec, is(matcher));
	}

	@Test
	public void testConvenienceMatchierAdviceString() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);
		adviceArray.add(JsonNodeFactory.instance.textNode("food"));
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(adviceArray));

		assertThat(dec, is(Matchers.hasAdvice("food")));
	}

	@Test
	public void test_neg() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);

		ObjectNode expectedadvice = mapper.createObjectNode();
		expectedadvice.put("xxx", "xxx");
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(adviceArray));

		var matcher = hasAdvice(expectedadvice);

		assertThat(dec, not(is(matcher)));
	}

	@Test
	public void test_nullDecision() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		var matcher = hasAdvice(advice);
		assertThat(null, not(is(matcher)));
	}

	@Test
	public void test_emptyAdvice() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		var matcher = hasAdvice(advice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.empty());
		assertThat(dec, not(is(matcher)));
	}

	@Test
	public void test_numberEqualTest() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode expectedAdvice = mapper.createObjectNode();
		expectedAdvice.put("foo", 1);
		var matcher = hasAdvice(expectedAdvice);
		ObjectNode actualAdvice = mapper.createObjectNode();
		actualAdvice.put("foo", 1f);
		ArrayNode actualAdviceArray = mapper.createArrayNode();
		actualAdviceArray.add(actualAdvice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(actualAdviceArray));
		assertThat(dec, is(matcher));
	}

	@Test
	public void test_emptyMatcher() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode actualAdvice = mapper.createObjectNode();
		actualAdvice.put("foo", 1);
		ArrayNode actualAdviceArray = mapper.createArrayNode();
		actualAdviceArray.add(actualAdvice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
				Optional.of(actualAdviceArray));

		var sut = new HasAdvice();

		assertThat(dec, is(sut));
	}

	@Test
	public void test_nullJsonNode() {
		assertThrows(NullPointerException.class, () -> hasAdvice((ObjectNode) null));
	}

	@Test
	void testDescriptionForMatcherEmptyMatcher() {
		var sut = new HasAdvice();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("the decision has an advice equals any advice"));
	}

	@Test
	void testDescriptionForMatcher() {
		var sut = hasAdvice(jsonText("value"));
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(),
				is("the decision has an advice equals a text node with value that is \"value\""));
	}

}

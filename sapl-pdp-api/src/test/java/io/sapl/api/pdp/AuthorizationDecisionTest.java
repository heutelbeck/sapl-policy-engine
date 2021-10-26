/*
 * Copyright © 2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AuthorizationDecisionTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void defaultConstructorResultsInNoEntriesAndIndeterminate() {
		var decision = new AuthorizationDecision();
		assertAll(() -> assertEquals(Decision.INDETERMINATE, decision.getDecision()),
				() -> assertThat(decision.getAdvice(), is(emptyOptional())),
				() -> assertThat(decision.getObligations(), is(emptyOptional())),
				() -> assertThat(decision.getResource(), is(emptyOptional())));
	}

	@Test
	void decisionConstructorResultsInNoEntries() {
		var decision = new AuthorizationDecision(Decision.DENY);
		assertAll(() -> assertEquals(Decision.DENY, decision.getDecision()),
				() -> assertThat(decision.getAdvice(), is(emptyOptional())),
				() -> assertThat(decision.getObligations(), is(emptyOptional())),
				() -> assertThat(decision.getResource(), is(emptyOptional())));
	}

	@Test
	void decisionConstructorNull() {
		assertThrows(NullPointerException.class, () -> {
			new AuthorizationDecision(null).withAdvice(null);
		});
	}

	@Test
	void withAdviceNull() {
		assertThrows(NullPointerException.class, () -> {
			new AuthorizationDecision().withAdvice(null);
		});
	}

	@Test
	void withDecisionNull() {
		assertThrows(NullPointerException.class, () -> {
			new AuthorizationDecision().withDecision(null);
		});
	}

	@Test
	void withAdviceEmpty() {
		var advice = JSON.arrayNode();
		var decision = new AuthorizationDecision().withAdvice(advice);
		assertThat(decision.getAdvice(), is(emptyOptional()));
	}

	@Test
	void withAdvicePresent() {
		var advice = JSON.arrayNode();
		advice.add(JSON.numberNode(0));
		var decision = new AuthorizationDecision().withAdvice(advice);
		assertThat(decision.getAdvice(), is(optionalWithValue(is(jsonArray(contains(jsonInt(0)))))));
	}

	@Test
	void withObligationsEmpty() {
		var obligations = JSON.arrayNode();
		var decision = new AuthorizationDecision().withObligations(obligations);
		assertThat(decision.getObligations(), is(emptyOptional()));
	}

	@Test
	void withObligationsNull() {
		assertThrows(NullPointerException.class, () -> {
			new AuthorizationDecision().withObligations(null);
		});
	}

	@Test
	void withObligationsPresent() {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.numberNode(0));
		var decision = new AuthorizationDecision().withObligations(obligations);
		assertThat(decision.getObligations(), is(optionalWithValue(is(jsonArray(contains(jsonInt(0)))))));
	}

	@Test
	void withResourceNull() {
		assertThrows(NullPointerException.class, () -> {
			new AuthorizationDecision().withResource(null);
		});
	}

	@Test
	void withResource() {
		var decision = new AuthorizationDecision().withResource(JSON.numberNode(0));
		assertThat(decision.getResource(), is(optionalWithValue(is(jsonInt(0)))));
	}

	@Test
	void withDecision() {
		var decision = AuthorizationDecision.DENY.withDecision(Decision.PERMIT);
		assertThat(decision.getDecision(), is(Decision.PERMIT));
	}

}

/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Objects;
import java.util.function.Predicate;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasAdviceMatching extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

	private final Predicate<? super JsonNode> predicate;

	public HasAdviceMatching(Predicate<? super JsonNode> jsonPredicate) {
		super(AuthorizationDecision.class);
		this.predicate = Objects.requireNonNull(jsonPredicate);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an advice matching the predicate");
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		var advice = decision.getAdvice();
		if (advice.isEmpty()) {
			mismatchDescription.appendText("decision didn't contain any advice");
			return false;
		}

		var containsAdvice = false;

		for (JsonNode node : advice.get()) {
			if (this.predicate.test(node))
				containsAdvice = true;
		}

		if (containsAdvice) {
			return true;
		} else {
			mismatchDescription.appendText("no advice matched");
			return false;
		}
	}

}

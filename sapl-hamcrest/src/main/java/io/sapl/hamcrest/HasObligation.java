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

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasObligation extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public HasObligation(Matcher<? super JsonNode> jsonMatcher) {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public HasObligation() {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.empty();
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an obligation equals ");
		this.jsonMatcher.ifPresentOrElse(description::appendDescriptionOf,
				() -> description.appendText("any obligation"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		var obligations = decision.getObligations();
		if (obligations.isEmpty()) {
			mismatchDescription.appendText("decision didn't contain any obligations");
			return false;
		}

		if (jsonMatcher.isEmpty()) {
			return true;
		}

		var containsObligation = false;
		for (JsonNode node : obligations.get()) {
			if (this.jsonMatcher.get().matches(node))
				containsObligation = true;
		}

		if (containsObligation) {
			return true;
		} else {
			mismatchDescription.appendText("no obligation matched");
			return false;
		}
	}

}
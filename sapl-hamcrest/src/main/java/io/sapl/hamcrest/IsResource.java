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
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class IsResource extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public IsResource(Matcher<? super JsonNode> jsonMatcher) {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public IsResource() {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.empty();
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("a resource with ");
		jsonMatcher.ifPresentOrElse(description::appendDescriptionOf, () -> description.appendText("any JsonNode"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		var resource = decision.getResource();
		if (resource.isEmpty()) {
			mismatchDescription.appendText("decision didn't contain a resource");
			return false;
		}

		var json = resource.get();
		if (jsonMatcher.isEmpty() || jsonMatcher.get().matches(json)) {
			return true;
		} else {
			mismatchDescription.appendText("was resource that ");
			jsonMatcher.get().describeMismatch(json, mismatchDescription);
			return false;
		}
	}

}
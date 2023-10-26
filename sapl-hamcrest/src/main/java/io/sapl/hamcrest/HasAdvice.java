/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

/**
 * Matcher for examining the advice contained in an AuthorizationDecision.
 */
public class HasAdvice extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

    private final Optional<Matcher<? super JsonNode>> jsonMatcher;

    /**
     * Checks for the presence of any advice matching a matcher.
     * 
     * @param jsonMatcher matcher for advice objects.
     */
    public HasAdvice(Matcher<? super JsonNode> jsonMatcher) {
        super(AuthorizationDecision.class);
        this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
    }

    /**
     * Checks for the presence of any advice.
     */
    public HasAdvice() {
        super(AuthorizationDecision.class);
        this.jsonMatcher = Optional.empty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("the decision has an advice equals ");
        this.jsonMatcher.ifPresentOrElse(description::appendDescriptionOf, () -> description.appendText("any advice"));
    }

    @Override
    protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
        var advice = decision.getAdvice();
        if (advice.isEmpty()) {
            mismatchDescription.appendText("decision didn't contain any advice");
            return false;
        }

        if (jsonMatcher.isEmpty()) {
            return true;
        }

        var containsAdvice = false;

        for (JsonNode node : advice.get()) {
            if (this.jsonMatcher.get().matches(node))
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

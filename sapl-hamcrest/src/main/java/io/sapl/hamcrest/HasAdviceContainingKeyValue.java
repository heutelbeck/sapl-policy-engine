/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Objects;
import java.util.Optional;

/**
 * Matcher for examining the advice contained in an AuthorizationDecision.
 */
public class HasAdviceContainingKeyValue extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

    private final String key;

    private final Optional<Matcher<? super JsonNode>> valueMatcher;

    /**
     * Checks for the presence of an advice containing a field with the given key
     * and a value matching a matcher.
     *
     * @param key a key
     * @param value a value matcher.
     */
    public HasAdviceContainingKeyValue(String key, Matcher<? super JsonNode> value) {
        super(AuthorizationDecision.class);
        this.key          = Objects.requireNonNull(key);
        this.valueMatcher = Optional.of(Objects.requireNonNull(value));
    }

    /**
     * Checks for the presence of an advice containing a field with the given key.
     *
     * @param key a key
     */
    public HasAdviceContainingKeyValue(String key) {
        super(AuthorizationDecision.class);
        this.key          = Objects.requireNonNull(key);
        this.valueMatcher = Optional.empty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("the decision has an advice containing key %s", this.key));

        this.valueMatcher.ifPresentOrElse(matcher -> description.appendText(" with ").appendDescriptionOf(matcher),
                () -> description.appendText(" with any value"));
    }

    @Override
    protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
        final var optionalAdvice = decision.getAdvice();
        if (optionalAdvice.isEmpty()) {
            mismatchDescription.appendText("decision didn't contain any advice");
            return false;
        }

        var containsAdvice = false;
        for (JsonNode advice : optionalAdvice.get()) {
            final var iterator = advice.properties().iterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                if (entry.getKey().equals(this.key)
                        && (this.valueMatcher.isEmpty() || this.valueMatcher.get().matches(entry.getValue()))) {
                    containsAdvice = true;
                }
            }
        }

        if (containsAdvice) {
            return true;
        } else {
            mismatchDescription.appendText("no entry in advice matched");
            return false;
        }
    }

}

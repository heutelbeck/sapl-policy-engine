/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

/**
 * Matcher for examining the obligation contained in an AuthorizationDecision.
 */
public class HasObligationContainingKeyValue extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

    private final String key;

    private final Optional<Matcher<? super JsonNode>> valueMatcher;

    /**
     * Checks for the presence of an obligation containing a field with the given
     * key and a value matching a matcher.
     *
     * @param key   a key
     * @param value a value matcher.
     */
    public HasObligationContainingKeyValue(String key, Matcher<? super JsonNode> value) {
        super(AuthorizationDecision.class);
        this.key          = Objects.requireNonNull(key);
        this.valueMatcher = Optional.of(Objects.requireNonNull(value));
    }

    /**
     * Checks for the presence of an obligation containing a field with the given
     * key.
     *
     * @param key a key
     */
    public HasObligationContainingKeyValue(String key) {
        super(AuthorizationDecision.class);
        this.key          = Objects.requireNonNull(key);
        this.valueMatcher = Optional.empty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(String.format("the decision has an obligation containing key %s", this.key));

        this.valueMatcher.ifPresentOrElse(matcher -> description.appendText(" with ").appendDescriptionOf(matcher),
                () -> description.appendText(" with any value"));
    }

    @Override
    protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
        var obligations = decision.getObligations();
        if (obligations.isEmpty()) {
            mismatchDescription.appendText("decision didn't contain any obligations");
            return false;
        }

        var containsObligationKeyValue = false;

        // iterate over all obligations
        for (JsonNode obligation : obligations.get()) {
            var iterator = obligation.fields();
            // iterate over fields in this obligation
            while (iterator.hasNext()) {
                var entry = iterator.next();
                // check if key/value exists
                if (entry.getKey().equals(this.key)
                        && (this.valueMatcher.isEmpty() || this.valueMatcher.get().matches(entry.getValue()))) {
                    containsObligationKeyValue = true;
                }
            }
        }

        if (containsObligationKeyValue) {
            return true;
        } else {
            mismatchDescription.appendText("no entry in all obligations matched");
            return false;
        }
    }

}

/*
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
import java.util.function.Predicate;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

/**
 * Matcher class for AuthorizationDecision objects inspecting the resource
 * object.
 */
public class HasResourceMatching extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

    private final Predicate<? super JsonNode> predicate;

    /**
     * Checks if the resource fulfills a Predicate.
     * 
     * @param jsonPredicate a predicate on the Resource
     */
    public HasResourceMatching(Predicate<? super JsonNode> jsonPredicate) {
        super(AuthorizationDecision.class);
        this.predicate = Objects.requireNonNull(jsonPredicate);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("the decision has a resource matching the predicate");
    }

    @Override
    protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
        var resource = decision.getResource();
        if (resource.isEmpty()) {
            mismatchDescription.appendText("decision didn't contain a resource");
            return false;
        }

        var json = resource.get();
        if (this.predicate.test(json)) {
            return true;
        } else {
            mismatchDescription.appendText("was resource that matches the predicate");
            return false;
        }
    }

}

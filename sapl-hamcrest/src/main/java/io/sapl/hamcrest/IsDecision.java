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
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

/**
 * A Matcher for AuthorizationDecision objects.
 */
public class IsDecision extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

    private final Optional<Decision> expectedDecision;

    /**
     * Creates a matcher expecting a specific Decision in the AuthorizationDecision.
     *
     * @param expected expected Decision
     */
    public IsDecision(Decision expected) {
        super(AuthorizationDecision.class);
        this.expectedDecision = Optional.of(Objects.requireNonNull(expected));
    }

    /**
     * Creates a matcher checking, if the object is an AuthorizationDecision.
     */
    public IsDecision() {
        super(AuthorizationDecision.class);
        this.expectedDecision = Optional.empty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("the decision is ");
        this.expectedDecision.ifPresentOrElse(expected -> description.appendText(expected.name()),
                () -> description.appendText("any decision"));
    }

    @Override
    protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
        if (this.expectedDecision.isEmpty() || this.expectedDecision.get() == decision.getDecision()) {
            return true;
        } else {
            mismatchDescription.appendText("was decision of " + decision.getDecision().name());
            return false;
        }
    }

}

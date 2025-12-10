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
package io.sapl.test.steps;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * Hamcrest matchers for AuthorizationDecision used in test step verification.
 */
@UtilityClass
public class AuthorizationDecisionMatchers {

    public static Matcher<AuthorizationDecision> isPermit() {
        return isDecision(Decision.PERMIT);
    }

    public static Matcher<AuthorizationDecision> isDeny() {
        return isDecision(Decision.DENY);
    }

    public static Matcher<AuthorizationDecision> isIndeterminate() {
        return isDecision(Decision.INDETERMINATE);
    }

    public static Matcher<AuthorizationDecision> isNotApplicable() {
        return isDecision(Decision.NOT_APPLICABLE);
    }

    private static Matcher<AuthorizationDecision> isDecision(Decision expected) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.decision() == expected;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("decision is ").appendValue(expected);
            }
        };
    }

}

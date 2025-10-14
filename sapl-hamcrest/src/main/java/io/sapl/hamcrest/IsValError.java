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

import io.sapl.api.interpreter.Val;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Objects;

/**
 * Val Matcher to check for error Val values.
 */
public class IsValError extends TypeSafeDiagnosingMatcher<Val> {

    private final Matcher<? super String> stringMatcher;

    /**
     * Val Matcher to check for error Val values with a given String matcher.
     *
     * @param stringMatcher a String matcher
     */
    public IsValError(Matcher<? super String> stringMatcher) {
        super(Val.class);
        this.stringMatcher = Objects.requireNonNull(stringMatcher);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void describeTo(Description description) {
        description.appendText("an error with message that ").appendDescriptionOf(stringMatcher);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean matchesSafely(Val item, Description mismatchDescription) {
        if (!item.isError()) {
            mismatchDescription.appendText("a value that is ").appendValue(item);
            return false;
        }
        final var message = item.getMessage();
        if (stringMatcher.matches(message)) {
            return true;
        } else {
            mismatchDescription.appendText("was an error with a message that ");
            stringMatcher.describeMismatch(message, mismatchDescription);
            return false;
        }
    }

}

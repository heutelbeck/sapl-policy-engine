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

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;

/**
 * A Matcher for examining Val objects.
 */
public class IsVal extends TypeSafeDiagnosingMatcher<Val> {

    private final Optional<Matcher<? super JsonNode>> jsonMatcher;

    /**
     * Creates a Val matcher checking the contents with a JsonNode matcher.
     *
     * @param jsonMatcher a JsonNode matcher.
     */
    public IsVal(Matcher<? super JsonNode> jsonMatcher) {
        super(Val.class);
        this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
    }

    /**
     * Creates a Val matcher checking if the object is a Val.
     */
    public IsVal() {
        super(Val.class);
        this.jsonMatcher = Optional.empty();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("a val that ");
        jsonMatcher.ifPresentOrElse(description::appendDescriptionOf, () -> description.appendText("is any JsonNode"));
    }

    @Override
    protected boolean matchesSafely(Val item, Description mismatchDescription) {
        if (item.isError()) {
            mismatchDescription.appendText("an error that is '").appendText(item.getMessage()).appendText("'");
            return false;
        }
        if (item.isUndefined()) {
            mismatchDescription.appendText("undefined");
            return false;
        }
        final var json = item.get();
        if (jsonMatcher.isEmpty() || jsonMatcher.get().matches(json)) {
            return true;
        } else {
            mismatchDescription.appendText("was val that ");
            jsonMatcher.get().describeMismatch(json, mismatchDescription);
            return false;
        }
    }

}

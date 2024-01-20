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

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.sapl.api.interpreter.Val;

/**
 * Val Matcher to check for undefined Val values.
 */
public class IsValUndefined extends TypeSafeDiagnosingMatcher<Val> {

    /**
     * Creates a Val Matcher to check for undefined Val values.
     */
    public IsValUndefined() {
        super(Val.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void describeTo(Description description) {
        description.appendText("undefined");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean matchesSafely(Val item, Description mismatchDescription) {
        if (item.isUndefined()) {
            return true;
        } else {
            mismatchDescription.appendText("a Val that is ").appendValue(item);
            return false;
        }
    }

}

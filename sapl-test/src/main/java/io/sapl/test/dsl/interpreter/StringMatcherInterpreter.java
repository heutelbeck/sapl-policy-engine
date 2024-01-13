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

package io.sapl.test.dsl.interpreter;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.endsWithIgnoringCase;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.startsWithIgnoringCase;
import static org.hamcrest.Matchers.stringContainsInOrder;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.StringContains;
import io.sapl.test.grammar.sAPLTest.StringContainsInOrder;
import io.sapl.test.grammar.sAPLTest.StringEndsWith;
import io.sapl.test.grammar.sAPLTest.StringIsBlank;
import io.sapl.test.grammar.sAPLTest.StringIsEmpty;
import io.sapl.test.grammar.sAPLTest.StringIsEqualIgnoringCase;
import io.sapl.test.grammar.sAPLTest.StringIsEqualWithCompressedWhiteSpace;
import io.sapl.test.grammar.sAPLTest.StringIsNull;
import io.sapl.test.grammar.sAPLTest.StringIsNullOrBlank;
import io.sapl.test.grammar.sAPLTest.StringIsNullOrEmpty;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.StringMatchesRegex;
import io.sapl.test.grammar.sAPLTest.StringStartsWith;
import io.sapl.test.grammar.sAPLTest.StringWithLength;
import org.hamcrest.Matcher;

class StringMatcherInterpreter {
    Matcher<? super String> getHamcrestStringMatcher(final StringMatcher stringMatcher) {
        final var nullEmptyBlankCasesMatcher = getMatcherForNullEmptyBlankCases(stringMatcher);

        if (nullEmptyBlankCasesMatcher != null) {
            return nullEmptyBlankCasesMatcher;
        }

        return getMatcherForNonNullEmptyBlankCases(stringMatcher);
    }

    private Matcher<? super String> getMatcherForNullEmptyBlankCases(final StringMatcher stringMatcher) {
        if (stringMatcher instanceof StringIsNull) {
            return nullValue(String.class);
        } else if (stringMatcher instanceof StringIsBlank) {
            return blankString();
        } else if (stringMatcher instanceof StringIsEmpty) {
            return emptyString();
        } else if (stringMatcher instanceof StringIsNullOrEmpty) {
            return emptyOrNullString();
        } else if (stringMatcher instanceof StringIsNullOrBlank) {
            return blankOrNullString();
        }
        return null;
    }

    private Matcher<? super String> getMatcherForNonNullEmptyBlankCases(final StringMatcher stringMatcher) {
        if (stringMatcher instanceof StringIsEqualWithCompressedWhiteSpace stringIsEqualWithCompressedWhiteSpace) {
            return equalToCompressingWhiteSpace(stringIsEqualWithCompressedWhiteSpace.getValue());
        } else if (stringMatcher instanceof StringIsEqualIgnoringCase stringIsEqualIgnoringCase) {
            return equalToIgnoringCase(stringIsEqualIgnoringCase.getValue());
        } else if (stringMatcher instanceof StringMatchesRegex stringMatchesRegex) {
            return matchesRegex(stringMatchesRegex.getRegex());
        } else if (stringMatcher instanceof StringStartsWith stringStartsWith) {
            final var prefix = stringStartsWith.getPrefix();

            return stringStartsWith.isCaseInsensitive() ? startsWithIgnoringCase(prefix) : startsWith(prefix);
        } else if (stringMatcher instanceof StringEndsWith stringEndsWith) {
            final var postfix = stringEndsWith.getPostfix();

            return stringEndsWith.isCaseInsensitive() ? endsWithIgnoringCase(postfix) : endsWith(postfix);
        } else if (stringMatcher instanceof StringContains stringContains) {
            final var text = stringContains.getText();

            return stringContains.isCaseInsensitive() ? containsStringIgnoringCase(text) : containsString(text);
        } else if (stringMatcher instanceof StringContainsInOrder stringContainsInOrder) {
            return stringContainsInOrder(stringContainsInOrder.getSubstrings());
        } else if (stringMatcher instanceof StringWithLength stringWithLength) {
            return handleStringWithLength(stringWithLength);
        }

        throw new SaplTestException("Unknown type of StringMatcher");
    }

    private Matcher<? super String> handleStringWithLength(final StringWithLength stringWithLength) {
        try {
            final var length = stringWithLength.getLength().intValueExact();

            if (length < 0) {
                throw new ArithmeticException();
            }

            return hasLength(length);
        } catch (ArithmeticException exception) {
            throw new SaplTestException("String length needs to be an natural number");
        }
    }
}

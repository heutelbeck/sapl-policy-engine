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
package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.*;
import org.hamcrest.Matcher;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.hamcrest.Matchers.*;

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
            return handleStringMatchesRegex(stringMatchesRegex);
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

    private static Matcher<String> handleStringMatchesRegex(StringMatchesRegex stringMatchesRegex) {
        try {
            final var pattern = Pattern.compile(stringMatchesRegex.getRegex());
            return matchesRegex(pattern);
        } catch (PatternSyntaxException e) {
            throw new SaplTestException("The given regex has an invalid format", e);
        }
    }

    private Matcher<? super String> handleStringWithLength(final StringWithLength stringWithLength) {
        try {
            final var length = stringWithLength.getLength().intValueExact();

            if (length < 1) {
                throw new ArithmeticException();
            }

            return hasLength(length);
        } catch (ArithmeticException e) {
            throw new SaplTestException("String length needs to be an natural number larger than 0", e);
        }
    }
}

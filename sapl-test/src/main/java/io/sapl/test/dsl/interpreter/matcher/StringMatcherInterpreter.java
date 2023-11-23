package io.sapl.test.dsl.interpreter.matcher;


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

public class StringMatcherInterpreter {
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
        if (stringMatcher instanceof StringIsEqualWithCompressedWhiteSpace other) {
            return equalToCompressingWhiteSpace(other.getValue());
        } else if (stringMatcher instanceof StringIsEqualIgnoringCase other) {
            return equalToIgnoringCase(other.getValue());
        } else if (stringMatcher instanceof StringMatchesRegex regex) {
            return matchesRegex(regex.getRegex());
        } else if (stringMatcher instanceof StringStartsWith other) {
            final var start = other.getStart();
            return other.isCaseInsensitive() ? startsWithIgnoringCase(start) : startsWith(start);
        } else if (stringMatcher instanceof StringEndsWith other) {
            final var end = other.getEnd();
            return other.isCaseInsensitive() ? endsWithIgnoringCase(end) : endsWith(end);
        } else if (stringMatcher instanceof StringContains other) {
            final var value = other.getValue();
            return other.isCaseInsensitive() ? containsStringIgnoringCase(value) : containsString(other.getValue());
        } else if (stringMatcher instanceof StringContainsInOrder other) {
            return stringContainsInOrder(other.getSubstrings());
        } else if (stringMatcher instanceof StringWithLength other) {
            return hasLength(other.getLength().intValue());
        }
        throw new SaplTestException("Unknown type of StringMatcher");
    }
}

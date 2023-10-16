package io.sapl.test.dsl.interpreter.matcher;


import static org.hamcrest.Matchers.*;

import io.sapl.test.grammar.sAPLTest.*;
import org.hamcrest.Matcher;

public class StringMatcherInterpreter {
    Matcher<? super String> getHamcrestStringMatcher(final StringMatcher stringMatcher) {
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
        } else if (stringMatcher instanceof StringIsEqualWithCompressedWhiteSpace other) {
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
        return null;
    }
}

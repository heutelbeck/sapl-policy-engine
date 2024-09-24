package io.sapl.broker.impl;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Provides a pattern for checking syntactic correctness of names for policy
 * information points or functions.
 */
@UtilityClass
public class NameValidator {
    private static final String            REGEX   = "[a-zA-Z][a-zA-Z0-9]*\\.[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*){0,8}";
    private static final Predicate<String> PATTERN = Pattern.compile(REGEX).asMatchPredicate();

    /**
     * 
     * 
     * @param stringUnderTest a String for validation.
     * @throws IllegalArgumentException if the stringUnderTest does not match the
     *                                  pattern for fully qualified names.
     */
    public void requireValidName(String stringUnderTest) {
        if (!PATTERN.test(stringUnderTest)) {
            throw new IllegalArgumentException(String.format("""
                    The fully qualified name of a Policy Information Point or function must cosist of \
                    at least two Strings separated by a '.'. Each of the strings must not \
                    contain white spaces and must start with a letter. \
                    No special characters are allowed. \
                    At most ten segments are allowed. \
                    Name was: '%s'.
                    """, stringUnderTest));
        }
    }
}

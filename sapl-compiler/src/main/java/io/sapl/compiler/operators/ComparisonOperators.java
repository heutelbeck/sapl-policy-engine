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
package io.sapl.compiler.operators;

import io.sapl.api.model.*;
import io.sapl.compiler.SaplCompilerException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides equality and containment comparison operations for Value instances.
 * <p>
 * Supports equality testing, containment checking in collections, and regular
 * expression matching. All operations
 * preserve secret flags from operands.
 */
@UtilityClass
public class ComparisonOperators {

    private static final String ERROR_IN_OPERATOR_TYPE_MISMATCH   = "'in' operator supports value lookup in arrays or objects, as well as substring matching with two strings. But I got: %s in %s.";
    private static final String ERROR_REGEX_INVALID               = "Invalid regular expression: %s";
    private static final String ERROR_REGEX_MUST_BE_STRING        = "Regular expressions must be strings, but got: %s.";
    private static final String ERROR_REGEX_TARGET_MUST_BE_STRING = "Regular expressions can only be matched against strings, but got: %s.";

    /**
     * Tests two values for equality using Value.equals() semantics.
     *
     * @param a
     * the first value
     * @param b
     * the second value
     *
     * @return Value.TRUE if values are equal, Value.FALSE otherwise, with combined
     * secret flag
     */
    public static Value equals(Value a, Value b) {
        return preserveSecret(a.equals(b), a.secret() || b.secret());
    }

    /**
     * Tests two values for inequality.
     *
     * @param a
     * the first value
     * @param b
     * the second value
     *
     * @return Value.TRUE if values are not equal, Value.FALSE otherwise, with
     * combined secret flag
     */
    public static Value notEquals(Value a, Value b) {
        return preserveSecret(!a.equals(b), a.secret() || b.secret());
    }

    /**
     * Tests whether a value is contained in a collection or string.
     * <p>
     * Supports:
     * <ul>
     * <li>Value lookup in arrays (checks if needle equals any element)</li>
     * <li>Value lookup in objects (checks if needle equals any value)</li>
     * <li>Substring matching in strings</li>
     * </ul>
     *
     * @param needle
     * the value to search for
     * @param haystack
     * the collection or string to search in
     *
     * @return Value.TRUE if needle is found, Value.FALSE otherwise, or error if
     * type mismatch
     */
    public static Value isContainedIn(Value needle, Value haystack) {
        val secret = needle.secret() || haystack.secret();
        if (haystack instanceof ArrayValue array) {
            return preserveSecret(array.contains(needle), secret);
        }
        if (haystack instanceof ObjectValue object) {
            return preserveSecret(object.containsValue(needle), secret);
        }
        if (haystack instanceof TextValue textHaystack && needle instanceof TextValue textNeedle) {
            return preserveSecret(textHaystack.value().contains(textNeedle.value()), secret);
        }
        return Value.error(ERROR_IN_OPERATOR_TYPE_MISMATCH, needle, haystack);
    }

    /**
     * Tests whether a string matches a regular expression pattern.
     *
     * @param input
     * the string to test
     * @param regex
     * the regular expression pattern as a TextValue
     *
     * @return Value.TRUE if the string matches the pattern, Value.FALSE otherwise,
     * or error if types are invalid or
     * pattern is malformed
     */
    public static Value matchesRegularExpression(Value input, Value regex) {
        if (!(input instanceof TextValue inputText)) {
            return Value.error(ERROR_REGEX_TARGET_MUST_BE_STRING, input);
        }
        if (!(regex instanceof TextValue regexText)) {
            return Value.error(ERROR_REGEX_MUST_BE_STRING, regex);
        }
        val secret = input.secret() || regex.secret();
        try {
            return preserveSecret(Pattern.matches(regexText.value(), inputText.value()), secret);
        } catch (PatternSyntaxException e) {
            return Value.error(ERROR_REGEX_INVALID, regex);
        }
    }

    /**
     * Compiles a regular expression pattern into a reusable operator function.
     * <p>
     * Pre-compiles the pattern at compile time for efficient repeated matching at
     * runtime.
     *
     * @param regex
     * the regular expression pattern as a TextValue
     *
     * @return a function that tests input strings against the compiled pattern
     *
     * @throws SaplCompilerException
     * if regex is not a TextValue or pattern is malformed
     */
    public UnaryOperator<Value> compileRegularExpressionOperator(Value regex) {
        if (!(regex instanceof TextValue regexText)) {
            throw new SaplCompilerException(String.format(ERROR_REGEX_MUST_BE_STRING, regex));
        }
        try {
            val pattern     = Pattern.compile(regexText.value()).asMatchPredicate();
            val regexSecret = regex.secret();
            return input -> {
                if (!(input instanceof TextValue inputText)) {
                    return Value.error(ERROR_REGEX_TARGET_MUST_BE_STRING, input);
                }
                val secret = input.secret() || regexSecret;
                return preserveSecret(pattern.test(inputText.value()), secret);
            };
        } catch (IllegalArgumentException e) {
            throw new SaplCompilerException(String.format(ERROR_REGEX_INVALID, regex), e);
        }
    }

    /**
     * Creates a BooleanValue with secret handling, reusing constants.
     *
     * @param value
     * the boolean value
     * @param secret
     * whether the value should be marked as secret
     *
     * @return a BooleanValue with the specified value and secret flag
     */
    private static BooleanValue preserveSecret(boolean value, boolean secret) {
        if (secret) {
            return value ? BooleanValue.SECRET_TRUE : BooleanValue.SECRET_FALSE;
        } else {
            return value ? Value.TRUE : Value.FALSE;
        }
    }
}

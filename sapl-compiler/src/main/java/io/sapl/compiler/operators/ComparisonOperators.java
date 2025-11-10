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

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.value.*;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.grammar.sapl.Regex;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Provides equality comparison operations for Value instances.
 */
@UtilityClass
public class ComparisonOperators {

    public static Value equals(Value a, Value b) {
        return preserveSecret(a.equals(b), a.secret() || b.secret());
    }

    public static Value notEquals(Value a, Value b) {
        return preserveSecret(!a.equals(b), a.secret() || b.secret());
    }

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
        return Value.error(String.format(
                "'in' operator supports value lookup in arrays or objects, as well as substring matching with two strings. But I got: %s in %s.",
                needle, haystack));
    }

    public static Value matchesRegularExpression(Value input, Value regex) {
        if (!(input instanceof TextValue inputText)) {
            return Value.error(
                    String.format("Regular expressions can only be matched against strings, but got: %s.", input));
        }
        if (!(regex instanceof TextValue regexText)) {
            return Value.error(String.format("Regular expressions must be strings, but got: %s.", regex));
        }
        val secret = input.secret() || regex.secret();
        try {
            return preserveSecret(Pattern.matches(inputText.value(), regexText.value()), secret);
        } catch (PatternSyntaxException e) {
            return Value.error(String.format("Invalid regular expression: %s", regex));
        }
    }

    public UnaryOperator<Value> compileRegularExpressionOperation(Value regex) {
        if (!(regex instanceof TextValue regexText)) {
            throw new SaplCompilerException(String.format("Regular expressions must be strings, but got: %s.", regex));
        }
        try {
            val pattern     = Pattern.compile(regexText.value()).asMatchPredicate();
            val regexSecret = regex.secret();
            return input -> {
                if (!(input instanceof TextValue inputText)) {
                    return Value.error(String
                            .format("Regular expressions can only be matched against strings, but got: %s.", input));
                }
                val secret = input.secret() || regexSecret;
                return preserveSecret(pattern.test(inputText.value()), secret);
            };
        } catch (IllegalArgumentException e) {
            throw new SaplCompilerException(String.format("Invalid regular expression: %s", regex));
        }
    }

    /**
     * Creates a BooleanValue with secret handling, reusing constants.
     *
     * @param value the boolean value
     * @param secret whether the value should be marked as secret
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

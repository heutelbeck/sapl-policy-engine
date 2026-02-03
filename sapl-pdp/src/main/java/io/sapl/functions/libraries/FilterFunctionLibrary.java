/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Function library implementing content filtering functions for data
 * transformation in access control policies.
 * <p>
 * This library provides essential functions for redacting, replacing, and
 * removing sensitive data in authorization
 * decisions.
 */
@UtilityClass
@FunctionLibrary(name = FilterFunctionLibrary.NAME, description = FilterFunctionLibrary.DESCRIPTION)
public class FilterFunctionLibrary {

    public static final String NAME        = "filter";
    public static final String DESCRIPTION = "Essential functions for content filtering.";

    private static final String ERROR_ILLEGAL_PARAMETER_BLACKEN_LENGTH = "Illegal parameter for BLACKEN_LENGTH. Expecting a positive integer. Got: %s.";
    private static final String ERROR_ILLEGAL_PARAMETER_DISCLOSE_LEFT  = "Illegal parameter for DISCLOSE_LEFT. Expecting a positive integer. Got: %s.";
    private static final String ERROR_ILLEGAL_PARAMETER_DISCLOSE_RIGHT = "Illegal parameter for DISCLOSE_RIGHT. Expecting a positive integer. Got: %s.";
    private static final String ERROR_ILLEGAL_PARAMETER_REPLACEMENT    = "Illegal parameter for REPLACEMENT. Expecting a string. Got: %s.";
    private static final String ERROR_ILLEGAL_PARAMETER_STRING         = "Illegal parameter for STRING. Expecting a string. Got: %s.";
    private static final String ERROR_ILLEGAL_PARAMETERS_COUNT         = "Illegal number of parameters provided.";

    private static final int    ORIGINAL_STRING_INDEX                      = 0;
    private static final int    DISCLOSE_LEFT_INDEX                        = 1;
    private static final int    DISCLOSE_RIGHT_INDEX                       = 2;
    private static final int    REPLACEMENT_INDEX                          = 3;
    private static final int    BLACKEN_LENGTH_INDEX                       = 4;
    private static final int    MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN   = 5;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT  = 0;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT = 0;
    private static final String DEFAULT_REPLACEMENT                        = "X";

    /**
     * Replaces a section of a text with a fixed character.
     *
     * @param parameters
     * STRING (original textual Value), DISCLOSE_LEFT leave this number of
     * characters original on the left
     * side of the string, DISCLOSE_RIGHT leave this number of characters original
     * on the right side of the
     * string, REPLACEMENT the replacement characters, defaulting to X,
     * BLACKEN_LENGTH the number of
     * replacement characters to use, overriding the calculated length.
     *
     * @return the original Text value with the indicated characters replaced with
     * the replacement characters.
     */
    @Function(docs = "Blacken text by replacing characters with a replacement string")
    public static Value blacken(Value... parameters) {
        validateParameterCount(parameters);
        val originalString = extractOriginalText(parameters);
        val replacement    = extractReplacement(parameters);
        val discloseRight  = extractDiscloseRight(parameters);
        val discloseLeft   = extractDiscloseLeft(parameters);
        val blackenLength  = extractBlackenLength(parameters);
        return blacken(originalString, replacement, discloseRight, discloseLeft, blackenLength);
    }

    private static Value blacken(String originalString, String replacement, int discloseRight, int discloseLeft,
            Integer blackenLength) {
        return Value.of(blackenUtil(originalString, replacement, discloseRight, discloseLeft, blackenLength));
    }

    /**
     * Utility method to blacken a string with optional length override.
     *
     * @param originalString
     * the original string to blacken
     * @param replacement
     * the replacement character(s)
     * @param discloseRight
     * number of characters to keep on the right
     * @param discloseLeft
     * number of characters to keep on the left
     * @param blackenLength
     * override length for replacement characters, or null to use calculated length
     *
     * @return the blackened string
     */
    public static String blackenUtil(String originalString, String replacement, int discloseRight, int discloseLeft,
            Integer blackenLength) {
        if (discloseLeft + discloseRight >= originalString.length())
            return originalString;

        val result        = new StringBuilder();
        val replacedChars = originalString.length() - discloseLeft - discloseRight;

        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }

        val blackenFinalLength = blackenLength != null ? blackenLength : replacedChars;
        result.append(String.valueOf(replacement).repeat(blackenFinalLength));

        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }

        return result.toString();
    }

    /**
     * Extracts a positive integer parameter from the parameters array.
     *
     * @param parameters
     * the parameters array
     * @param index
     * the index of the parameter to extract
     * @param defaultValue
     * the default value if parameter is not present
     * @param errorMessage
     * the error message if parameter is invalid
     *
     * @return the extracted integer or the default value
     *
     * @throws IllegalArgumentException
     * if parameter is present but invalid
     */
    private static int extractPositiveIntParameter(Value[] parameters, int index, int defaultValue,
            String errorMessage) {
        if (hasNoParameterAtIndex(parameters.length, index)) {
            return defaultValue;
        }

        val parameter = parameters[index];
        if (!(parameter instanceof NumberValue(BigDecimal value)) || value.intValue() < 0) {
            throw new IllegalArgumentException(errorMessage.formatted(parameter));
        }
        return value.intValue();
    }

    private static int extractDiscloseLeft(Value... parameters) {
        return extractPositiveIntParameter(parameters, DISCLOSE_LEFT_INDEX, DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT,
                ERROR_ILLEGAL_PARAMETER_DISCLOSE_LEFT);
    }

    private static int extractDiscloseRight(Value... parameters) {
        return extractPositiveIntParameter(parameters, DISCLOSE_RIGHT_INDEX, DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT,
                ERROR_ILLEGAL_PARAMETER_DISCLOSE_RIGHT);
    }

    private static String extractReplacement(Value... parameters) {
        if (hasNoParameterAtIndex(parameters.length, REPLACEMENT_INDEX)) {
            return DEFAULT_REPLACEMENT;
        }

        if (!(parameters[REPLACEMENT_INDEX] instanceof TextValue(String value))) {
            throw new IllegalArgumentException(ERROR_ILLEGAL_PARAMETER_REPLACEMENT);
        }
        return value;
    }

    private static String extractOriginalText(Value... parameters) {
        if (hasNoParameterAtIndex(parameters.length, ORIGINAL_STRING_INDEX)
                || !(parameters[ORIGINAL_STRING_INDEX] instanceof TextValue(String value))) {
            throw new IllegalArgumentException(ERROR_ILLEGAL_PARAMETER_STRING.formatted(Arrays.toString(parameters)));
        }

        return value;
    }

    /**
     * Extracts the blacken length from parameters.
     *
     * @param parameters
     * the function parameters
     *
     * @return the blacken length if provided, otherwise null
     *
     * @throws IllegalArgumentException
     * if the length parameter is not a positive integer
     */
    private static Integer extractBlackenLength(Value... parameters) {
        if (hasNoParameterAtIndex(parameters.length, BLACKEN_LENGTH_INDEX)) {
            return null;
        }

        val parameter = parameters[BLACKEN_LENGTH_INDEX];
        if (!(parameter instanceof NumberValue(BigDecimal value)) || value.intValue() < 0) {
            throw new IllegalArgumentException(ERROR_ILLEGAL_PARAMETER_BLACKEN_LENGTH);
        }

        return value.intValue();
    }

    private static boolean hasNoParameterAtIndex(int parameterCount, int parameterIndex) {
        return parameterCount < parameterIndex + 1;
    }

    private static void validateParameterCount(Value... parameters) {
        if (parameters.length > MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN) {
            throw new IllegalArgumentException(ERROR_ILLEGAL_PARAMETERS_COUNT);
        }
    }

    /**
     * Replaces the original with another value.
     *
     * @param original
     * the original value, which is ignored unless it's an error.
     * @param replacement
     * a replacement value.
     *
     * @return the replacement value, or the original if it's an error.
     */
    @Function(docs = "Replace a value with another value (error bubbles up)")
    public static Value replace(Value original, Value replacement) {
        if (original instanceof ErrorValue) {
            return original;
        }
        return replacement;
    }

    /**
     * Replaces any value with UNDEFINED.
     *
     * @param original
     * some value
     *
     * @return Value.UNDEFINED
     */
    @Function(docs = "Remove a value by replacing it with undefined")
    public static Value remove(Value original) {
        return Value.UNDEFINED;
    }
}

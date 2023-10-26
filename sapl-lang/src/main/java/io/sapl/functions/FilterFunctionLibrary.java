/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;

/**
 * Function library implementing blacken, replace, and remove filter functions.
 */
@FunctionLibrary(name = FilterFunctionLibrary.NAME, description = FilterFunctionLibrary.DESCRIPTION)
public class FilterFunctionLibrary {

    /**
     * Library name and prefix
     */
    public static final String NAME = "filter";

    /**
     * Library description
     */
    public static final String DESCRIPTION = "Essential functions for content filtering.";

    private static final String DEFAULT_REPLACEMENT = "X";

    private static final int DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT = 0;

    private static final int DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT = 0;

    private static final String BLACKEN_DOC = "blacken(STRING, DISCLOSE_LEFT, DISCLOSE_RIGHT, REPLACEMENT): Assumes that DISCLOSE_LEFT and DISCLOSE_RIGHT are positive integers and STRING and REPLACEMENT are strings."
            + " Replaces each character in STRING by REPLACEMENT, leaving DISCLOSE_LEFT characters from the beginning and DISCLOSE_RIGHT characters from the end unchanged."
            + " Except for STRING, all parameters are optional."
            + " DISCLOSE_LEFT defaults to 0, DISCLOSE_RIGHT defaults to 0 and REPLACEMENT defaults to 'X'"
            + " Returns the modified STRING.";

    private static final String REPLACE_DOC = "replace(ORIGINAL, REPLACEMENT): Assumes that ORIGINAL and REPLACEMENT are JSON nodes. Returns REPLACEMENT.";

    private static final String REMOVE_DOC = "remove: Maps any value to 'undefined' If used in a filter, this will be removed from arrays or objects";

    private static final String ILLEGAL_PARAMETERS_COUNT = "Illegal number of parameters provided.";

    private static final String ILLEGAL_PARAMETER_DISCLOSE_LEFT = "Illegal parameter for DISCLOSE_LEFT. Expecting a positive integer.";

    private static final String ILLEGAL_PARAMETER_DISCLOSE_RIGHT = "Illegal parameter for DISCLOSE_RIGHT. Expecting a positive integer.";

    private static final String ILLEGAL_PARAMETER_REPLACEMENT = "Illegal parameter for REPLACEMENT. Expecting a string.";

    private static final String ILLEGAL_PARAMETER_STRING = "Illegal parameter for STRING. Expecting a string.";

    private static final int ORIGINAL_STRING_INDEX = 0;

    private static final int DISCLOSE_LEFT_INDEX = 1;

    private static final int DISCLOSE_RIGHT_INDEX = 2;

    private static final int REPLACEMENT_INDEX = 3;

    private static final int MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN = 4;

    /**
     * Even though there are only static methods in this class, the engine requires
     * an instance for registration.
     */
    public FilterFunctionLibrary() {
        // NOOP.
    }

    /**
     * Replaces a section of a text with a fixed character.
     * 
     * @param parameters STRING (original textual Val), DISCLOSE_LEFT leave this
     *                   number of characters original on the left side of the
     *                   string, DISCLOSE_RIGHT leave this number of characters
     *                   original on the right side of the string, REPLACEMENT the
     *                   replacement characters, defaulting to X.
     * @return the original Text value with the indicated characters replaced with
     *         the replacement characters.
     */
    @Function(docs = BLACKEN_DOC)
    public static Val blacken(Val... parameters) {
        validateNumberOfParametersIsAtMostFour(parameters);
        var originalString = extractOriginalTextFromParameters(parameters);
        var replacement    = extractReplacementStringFromParametersOrUseDefault(parameters);
        var discloseRight  = extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(parameters);
        int discloseLeft   = extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(parameters);
        return blacken(originalString, replacement, discloseRight, discloseLeft);
    }

    private static Val blacken(String originalString, String replacement, int discloseRight, int discloseLeft) {
        if (discloseLeft + discloseRight >= originalString.length())
            return Val.of(originalString);

        StringBuilder result = new StringBuilder();
        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }
        int replacedChars = originalString.length() - discloseLeft - discloseRight;
        result.append(String.valueOf(replacement).repeat(Math.max(0, replacedChars)));
        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }
        return Val.of(result.toString());
    }

    private static int extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(Val... parameters) {
        int discloseLeft = DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT;
        if (parameters.length >= DISCLOSE_RIGHT_INDEX) {
            if (!parameters[DISCLOSE_LEFT_INDEX].isNumber() || parameters[DISCLOSE_LEFT_INDEX].get().asInt() < 0) {
                throw new IllegalArgumentException(ILLEGAL_PARAMETER_DISCLOSE_LEFT);
            }
            discloseLeft = parameters[DISCLOSE_LEFT_INDEX].get().asInt();
        }
        return discloseLeft;
    }

    private static int extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(Val... parameters) {
        int discloseRight = DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT;
        if (parameters.length >= REPLACEMENT_INDEX) {
            if (!parameters[DISCLOSE_RIGHT_INDEX].isNumber() || parameters[DISCLOSE_RIGHT_INDEX].get().asInt() < 0) {
                throw new IllegalArgumentException(ILLEGAL_PARAMETER_DISCLOSE_RIGHT);
            }
            discloseRight = parameters[DISCLOSE_RIGHT_INDEX].get().asInt();
        }
        return discloseRight;
    }

    private static String extractReplacementStringFromParametersOrUseDefault(Val... parameters) {
        String replacement = DEFAULT_REPLACEMENT;
        if (parameters.length == MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN) {
            if (!parameters[REPLACEMENT_INDEX].isTextual()) {
                throw new IllegalArgumentException(ILLEGAL_PARAMETER_REPLACEMENT);
            }
            replacement = parameters[REPLACEMENT_INDEX].get().asText();
        }
        return replacement;
    }

    private static String extractOriginalTextFromParameters(Val... parameters) {
        if (!parameters[0].isTextual()) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_STRING);
        }
        return parameters[ORIGINAL_STRING_INDEX].get().asText();
    }

    private static void validateNumberOfParametersIsAtMostFour(Val... parameters) {
        if (parameters.length > MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETERS_COUNT);
        }
    }

    /**
     * Replaces the original with another value.
     * 
     * @param original    the original value, which is ignored.
     * @param replacement a replacement value.
     * @return the replacement value.
     */
    @Function(docs = REPLACE_DOC)
    public static Val replace(Val original, Val replacement) {
        return replacement;
    }

    /**
     * Replaces any value with UNDEFINED.
     * 
     * @param original some value
     * @return Val.UNDEFINED
     */
    @Function(docs = REMOVE_DOC)
    public static Val remove(Val original) {
        return Val.UNDEFINED;
    }

}

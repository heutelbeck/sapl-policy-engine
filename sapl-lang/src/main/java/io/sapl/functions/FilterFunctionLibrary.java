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
package io.sapl.functions;

import java.util.Optional;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

/**
 * Function library implementing blacken, replace, and remove filter functions.
 */
@UtilityClass
@FunctionLibrary(name = FilterFunctionLibrary.NAME, description = FilterFunctionLibrary.DESCRIPTION)
public class FilterFunctionLibrary {

    public static final String NAME        = "filter";
    public static final String DESCRIPTION = "Essential functions for content filtering.";

    private static final String ILLEGAL_PARAMETERS_COUNT         = "Illegal number of parameters provided.";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_LEFT  = "Illegal parameter for DISCLOSE_LEFT. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_RIGHT = "Illegal parameter for DISCLOSE_RIGHT. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_REPLACEMENT    = "Illegal parameter for REPLACEMENT. Expecting a string.";
    private static final String ILLEGAL_PARAMETER_BLACKEN_LENGTH = "Illegal parameter for BLACKEN_TYPE. Expecting a integer representing a type of blacken.";
    private static final String ILLEGAL_PARAMETER_STRING         = "Illegal parameter for STRING. Expecting a string.";

    private static final int    ORIGINAL_STRING_INDEX                      = 0;
    private static final int    DISCLOSE_LEFT_INDEX                        = 1;
    private static final int    DISCLOSE_RIGHT_INDEX                       = 2;
    private static final int    REPLACEMENT_INDEX                          = 3;
    private static final int    BLACKEN_TYPE_INDEX                         = 4;
    private static final int    MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN   = 5;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT  = 0;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT = 0;
    private static final String DEFAULT_REPLACEMENT                        = "X";

    /**
     * Replaces a section of a text with a fixed character.
     *
     * @param parameters STRING (original textual Val), DISCLOSE_LEFT leave this
     * number of characters original on the left side of the string, DISCLOSE_RIGHT
     * leave this number of characters original on the right side of the string,
     * REPLACEMENT the replacement characters, defaulting to X.
     * @return the original Text value with the indicated characters replaced with
     * the replacement characters.
     */
    @Function(docs = """
            ```blacken(TEXT original\\[, INTEGER>0 discloseLeft\\]\\[, INTEGER>0 discloseRight\\]\\[, TEXT replacement\\]
            \\[, INTEGER>0 length\\])```:
            This function can be used to partially blacken text in data.
            The function requires that ```discloseLeft```, ```discloseRight```, and ```length``` are in integers > 0.
            Also, ```original``` and ```replacement``` must be text strings.
            The function replaces each character in ```original``` by ```replacement```, while leaving ```discloseLeft```
            characters from the beginning and ```discloseRight``` characters from the end unchanged.
            If ```length``` is provided, the number of characters replaced is set to ```length```, e.g., for
            ensuring, that string length does not leak any information.
            If ```length``` is not provided it will just replace all characters that are not disclosed.
            Except for ```original```, all parameters are optional.
            Defaults: ```discloseLeft``` defaults to ```0```, ```discloseRight``` defaults to ```0```
            and ```replacement``` defaults to ```"X"```.
            The function returns the modified ```original```.

            Example: Given a subscription:
            ```
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```
            policy "test"
            permit
            transform resource |- {
                                    @.key1 : filter.blacken(1)
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```
            {
              "array" : [ null, true ],
              "key1"  : "aXXXX"
            }
            ```
            """)
    public static Val blacken(Val... parameters) {
        validateNumberOfParametersIsNotLongerThanMaximalAllowedNumberOfParameters(parameters);
        final var originalString = extractOriginalTextFromParameters(parameters);
        final var replacement    = extractReplacementStringFromParametersOrUseDefault(parameters);
        final var discloseRight  = extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(
                parameters);
        final var discloseLeft   = extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(
                parameters);
        final var blackenLength  = extractLengthOfBlackenOrUseDefault(parameters);
        return blacken(originalString, replacement, discloseRight, discloseLeft, blackenLength);
    }

    private static Val blacken(String originalString, String replacement, int discloseRight, int discloseLeft,
            Optional<Integer> blackenLength) {
        return Val.of((blackenUtil(originalString, replacement, discloseRight, discloseLeft, blackenLength)));
    }

    public static String blackenUtil(String originalString, String replacement, int discloseRight, int discloseLeft,
            Optional<Integer> blackenLength) {
        if (discloseLeft + discloseRight >= originalString.length())
            return originalString;

        StringBuilder result = new StringBuilder();
        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }

        int replacedChars = originalString.length() - discloseLeft - discloseRight;

        int blackenFinalLength = blackenLength.orElse(replacedChars);

        result.append(String.valueOf(replacement).repeat(blackenFinalLength));
        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }
        return result.toString();
    }

    private static int extractNumberOfCharactersToDiscloseOnTheLeftSideFromParametersOrUseDefault(Val... parameters) {
        if (!validateParameterSanity(parameters.length, DISCLOSE_LEFT_INDEX)) {
            return DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT;
        }

        if (!parameters[DISCLOSE_LEFT_INDEX].isNumber() || parameters[DISCLOSE_LEFT_INDEX].get().asInt() < 0) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_DISCLOSE_LEFT);
        }
        return parameters[DISCLOSE_LEFT_INDEX].get().asInt();
    }

    private static int extractNumberOfCharactersToDiscloseOnTheRightSideFromParametersOrUseDefault(Val... parameters) {
        if (!validateParameterSanity(parameters.length, DISCLOSE_RIGHT_INDEX)) {
            return DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT;
        }

        if (!parameters[DISCLOSE_RIGHT_INDEX].isNumber() || parameters[DISCLOSE_RIGHT_INDEX].get().asInt() < 0) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_DISCLOSE_RIGHT);
        }
        return parameters[DISCLOSE_RIGHT_INDEX].get().asInt();
    }

    private static String extractReplacementStringFromParametersOrUseDefault(Val... parameters) {
        if (!validateParameterSanity(parameters.length, REPLACEMENT_INDEX)) {
            return DEFAULT_REPLACEMENT;
        }

        if (!parameters[REPLACEMENT_INDEX].isTextual()) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_REPLACEMENT);
        }
        return parameters[REPLACEMENT_INDEX].get().asText();
    }

    private static String extractOriginalTextFromParameters(Val... parameters) {
        if (!validateParameterSanity(parameters.length, ORIGINAL_STRING_INDEX)
                || !parameters[ORIGINAL_STRING_INDEX].isTextual()) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_STRING);
        }

        return parameters[ORIGINAL_STRING_INDEX].get().asText();
    }

    /**
     * Extracts the length of blacken from the parameters or returns
     * {@link #BLACKEN_TYPE_INDEX}
     *
     * @param parameters the parameters to extract the length from (if possible)
     * @return the length of blacken or empty if the length is not provided
     * @throws IllegalArgumentException if the length is not a valid number
     */
    private static Optional<Integer> extractLengthOfBlackenOrUseDefault(Val... parameters) {
        if (!validateParameterSanity(parameters.length, BLACKEN_TYPE_INDEX)) {
            return Optional.empty();
        }

        if (!parameters[BLACKEN_TYPE_INDEX].isNumber() || parameters[BLACKEN_TYPE_INDEX].get().asInt() < 0) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_BLACKEN_LENGTH);
        }

        return Optional.of(parameters[BLACKEN_TYPE_INDEX].get().asInt());
    }

    private static boolean validateParameterSanity(int lengthParameters, int parameterIndex) {
        return lengthParameters >= parameterIndex + 1;
    }

    private static void validateNumberOfParametersIsNotLongerThanMaximalAllowedNumberOfParameters(Val... parameters) {
        if (parameters.length > MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETERS_COUNT);
        }
    }

    /**
     * Replaces the original with another value.
     *
     * @param original the original value, which is ignored.
     * @param replacement a replacement value.
     * @return the replacement value.
     */
    @Function(docs = """
            ```replace(originalValue, replacementValue)```:
            The function will map the ```originalValue``` to the replacement value.
            If the original value is an error, it will not be replaced and it bubbles up the evaluation chain.
            If the original value is ```undefined``` it will be replaced with the ```replacementValue```.

            Example: Given a subscription:
            ```
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```
            policy "test"
            permit
            transform resource |- {
                                    @.array[1] : filter.replace("***"),
                                    @.key1     : filter.replace(null)
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```
            {
              "array" : [ null, "***" ],
              "key1"  : null
            }
            ```
            """)
    public static Val replace(Val original, Val replacement) {
        if (original.isError()) {
            return original;
        }
        return replacement;
    }

    /**
     * Replaces any value with UNDEFINED.
     *
     * @param original some value
     * @return Val.UNDEFINED
     */
    @Function(docs = """
            ```remove(value)```: This function maps any ```value``` to ```undefined```.
            In filters, ```undefined``` elements of arrays and objects will be silently removed.

            Example:

            The expression ```[ 0, 1, 2, 3, 4, 5 ] |- { @[-2:] : filter.remove }``` results in ```[0, 1, 2, 3]```.

            Given a subscription:
            ```
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```
            policy "test"
            permit
            transform resource |- {
                                    @.key1     : filter.remove
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```
            {
              "array" : [ null, true ]
            }
            ```
            """)
    public static Val remove(Val original) {
        return Val.UNDEFINED;
    }
}

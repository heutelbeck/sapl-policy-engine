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
package io.sapl.spring.data.services;

import lombok.AllArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
public class SecurityExpressionService {

    private final MethodSecurityExpressionEvaluator     securityExpressionEvaluator;
    private final CustomMethodSecurityExpressionHandler expressionHandler = new CustomMethodSecurityExpressionHandler();

    // @formatter:off
	private final Set<String> spelMethods = Set.of(
			"denyAll",
			"hasRole",
			"permitAll",
			"hasAnyRole",
			"isAnonymous",
			"hasAuthority",
			"hasAnyAuthority",
			"isAuthenticated");

	private final Set<String> speLVariables = Set.of(
			"root",
			"args",
			"this",
			"cible",
			"target",
			"authentication");
	// @formatter:on

    private static final String ID            = "ID_";
    private static final String SPECIAL_SIGNS = " !@#$%^&*)-+=}{[]|\\;:'\"<>,/?";

    public String evaluateSpelMethods(String input, MethodInvocation methodInvocation) {

        if (input.isEmpty()) {
            return input;
        }

        final var expressionMap                = new HashMap<String, Object>();
        final var inputWithReplacedSpelMethods = extractSpelMethodsAndReplaceWithKeys(input, expressionMap);

        if (inputWithReplacedSpelMethods.equals(input)) {
            return input;
        }

        final var evaluatedSpelMethods = evaluateSpelMethods(expressionMap, methodInvocation);

        return replaceKeysWithEvaluatedValues(inputWithReplacedSpelMethods, evaluatedSpelMethods);
    }

    private String extractSpelMethodsAndReplaceWithKeys(String input, Map<String, Object> expressionMap) {

        var counter = 1;

        for (String method : spelMethods) {

            method = method + "(";
            var startIndex = input.indexOf(method);

            while (startIndex != -1) {
                final var endIndex = input.indexOf(')', startIndex);

                if (endIndex != -1) {
                    final var extractedText = input.substring(startIndex, endIndex + 1);
                    final var uniqueID      = ID + counter;

                    input = input.replaceFirst("\\Q" + extractedText + "\\E", uniqueID);
                    expressionMap.put(uniqueID, extractedText);

                    counter++;
                    startIndex = input.indexOf(method, endIndex + 1);
                } else {
                    startIndex = -1;
                }
            }
        }

        return input;
    }

    private Map<String, Object> evaluateSpelMethods(Map<String, Object> extractedStrings, MethodInvocation invocation) {

        final var evaluatedSpelMethods = new HashMap<String, Object>();

        for (Map.Entry<String, Object> entry : extractedStrings.entrySet()) {

            final var evaluatedValue = securityExpressionEvaluator.evaluate(entry.getValue().toString(), invocation);
            evaluatedSpelMethods.put(entry.getKey(), evaluatedValue);
        }

        return evaluatedSpelMethods;
    }

    public String evaluateSpelVariables(String input) {

        final var expressionMap                = new HashMap<String, Object>();
        final var inputWithReplacedSpelMethods = extractSpelVariablesAndReplaceWithKeys(input, expressionMap);

        if (inputWithReplacedSpelMethods.isEmpty()) {
            return input;
        }

        final var evaluatedSpelVariables = evaluateSpelVariables(expressionMap);

        return replaceKeysWithEvaluatedValues(inputWithReplacedSpelMethods, evaluatedSpelVariables);
    }

    private String extractSpelVariablesAndReplaceWithKeys(String input, Map<String, Object> expressionMap) {

        var idCounter = 1;

        for (String speLVariable : speLVariables) {

            if (input.contains(speLVariable)) {
                final var startIndex = input.indexOf(speLVariable);
                var       endIndex   = findNextSpecialSignIndex(input, SPECIAL_SIGNS, startIndex);

                if (endIndex == -1) {
                    endIndex = input.length();
                }

                final var expression  = input.substring(startIndex, endIndex);
                final var replacement = ID + idCounter;

                idCounter++;

                input = input.replace(expression, replacement);
                expressionMap.put(replacement, expression);
            }
        }

        return input;
    }

    private Map<String, Object> evaluateSpelVariables(Map<String, Object> extractedStrings) {

        final var evaluatedSpelVariables = new HashMap<String, Object>();

        for (Map.Entry<String, Object> entry : extractedStrings.entrySet()) {

            final var evaluatedValue = expressionHandler.evaluateExpression(String.valueOf(entry.getValue()));
            evaluatedSpelVariables.put(entry.getKey(), evaluatedValue);
        }

        return evaluatedSpelVariables;
    }

    private String replaceKeysWithEvaluatedValues(String input, Map<String, Object> evaluatedSpelMethods) {

        for (Map.Entry<String, Object> entry : evaluatedSpelMethods.entrySet()) {
            final var key   = entry.getKey();
            final var value = String.valueOf(entry.getValue());

            input = input.replace(key, value);
        }

        return input;
    }

    public static int findNextSpecialSignIndex(String input, String specialSigns, int startIndex) {
        for (int i = startIndex; i < input.length(); i++) {
            if (specialSigns.indexOf(input.charAt(i)) != -1) {
                return i + 1;
            }
        }
        return -1;
    }

}

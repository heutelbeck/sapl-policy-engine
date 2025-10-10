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
package io.sapl.playground.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Service for validating JSON documents in the SAPL playground.
 * Provides validation for variables and authorization subscriptions.
 */
@Service
@RequiredArgsConstructor
public class PlaygroundValidator {

    private static final int          MAX_VARIABLE_NAME_LENGTH      = 100;
    private static final Pattern      VALID_VARIABLE_NAME           = Pattern.compile("[a-zA-Z_]\\w*");
    private static final List<String> MANDATORY_SUBSCRIPTION_FIELDS = List.of("subject", "action", "resource");

    private final ObjectMapper mapper;

    /**
     * Validates variables JSON document structure and variable names.
     * Checks that the JSON is an object and all variable names conform to
     * identifier rules.
     *
     * @param jsonContent the JSON string to validate
     * @return validation result with status and message
     */
    public ValidationResult validateVariablesJson(String jsonContent) {
        try {
            val jsonNode = mapper.readTree(jsonContent);

            if (!jsonNode.isObject()) {
                return ValidationResult.error("Must be a JSON Object");
            }

            val invalidNameCount = new AtomicInteger(0);
            jsonNode.fieldNames().forEachRemaining(name -> {
                if (name.length() > MAX_VARIABLE_NAME_LENGTH) {
                    invalidNameCount.incrementAndGet();
                } else if (!VALID_VARIABLE_NAME.matcher(name).matches()) {
                    invalidNameCount.incrementAndGet();
                }
            });

            if (invalidNameCount.get() > 0) {
                val message = "Invalid variable names found: " + invalidNameCount.get() + " error(s)";
                return ValidationResult.error(message);
            }

            return ValidationResult.success();

        } catch (JsonProcessingException exception) {
            return ValidationResult.error("Invalid JSON. Last valid will be used.");
        }
    }

    /**
     * Validates authorization subscription JSON document structure and mandatory
     * fields.
     * Ensures the JSON is an object and contains all required fields (subject,
     * action, resource).
     *
     * @param jsonContent the JSON string to validate
     * @return validation result with status and message
     */
    public ValidationResult validateAuthorizationSubscription(String jsonContent) {
        try {
            val jsonNode = mapper.readTree(jsonContent);

            if (!jsonNode.isObject()) {
                return ValidationResult.error("Must be a JSON Object");
            }

            val missingFields = findMissingMandatoryFields(jsonNode);
            if (!missingFields.isEmpty()) {
                val message = "Missing mandatory fields: " + String.join(", ", missingFields);
                return ValidationResult.error(message);
            }

            return ValidationResult.success();

        } catch (JsonProcessingException exception) {
            return ValidationResult.error("Invalid Authorization Subscription.");
        }
    }

    /**
     * Finds missing mandatory fields in a subscription JSON node.
     *
     * @param jsonNode the JSON node to check
     * @return list of missing field names
     */
    private List<String> findMissingMandatoryFields(com.fasterxml.jackson.databind.JsonNode jsonNode) {
        val missingFields = new ArrayList<String>();
        for (val mandatoryField : MANDATORY_SUBSCRIPTION_FIELDS) {
            if (jsonNode.get(mandatoryField) == null) {
                missingFields.add(mandatoryField);
            }
        }
        return missingFields;
    }

    /**
     * Checks if a variable name is valid.
     * Valid names must start with a letter or underscore, followed by word
     * characters.
     *
     * @param name the variable name to check
     * @return true if the name is valid
     */
    public static boolean isValidVariableName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() > MAX_VARIABLE_NAME_LENGTH) {
            return false;
        }
        return VALID_VARIABLE_NAME.matcher(name).matches();
    }
}

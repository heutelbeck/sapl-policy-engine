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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.SaplVersion;
import io.sapl.vaadin.Issue;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.eclipse.xtext.diagnostics.Severity;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * Service for validating JSON documents in the SAPL playground.
 * Provides validation for variables documents and authorization subscriptions,
 * ensuring they conform to required structure and content rules.
 * <p>
 * This service validates:
 * - JSON syntax and structure
 * - Variable names conform to identifier rules
 * - Authorization subscriptions contain mandatory fields
 */
@Service
@RequiredArgsConstructor
public class PlaygroundValidator implements Serializable {
    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    /*
     * Maximum length for variable names.
     * Prevents excessively long identifiers.
     */
    private static final int MAX_VARIABLE_NAME_LENGTH = 100;

    /*
     * Pattern for valid variable names.
     * Must start with letter or underscore, followed by word characters.
     */
    private static final Pattern VALID_VARIABLE_NAME = Pattern.compile("[a-zA-Z_]\\w*");

    /*
     * Mandatory fields required in authorization subscriptions.
     * All authorization subscriptions must contain subject, action, and resource.
     */
    private static final List<String> MANDATORY_SUBSCRIPTION_FIELDS = List.of("subject", "action", "resource");

    /*
     * JSON object mapper for parsing and validating JSON content.
     */
    private final ObjectMapper mapper;

    /**
     * Validates variables JSON document structure and variable names.
     * Ensures the document is a valid JSON object and all field names
     * conform to identifier rules (start with letter/underscore, followed
     * by word characters, max 100 characters length).
     *
     * @param jsonContent the JSON string to validate
     * @return validation result with status and descriptive message
     */
    public ValidationResult validateVariablesJson(String jsonContent) {
        val jsonNodeOrError = parseAndValidateJsonObject(jsonContent, "Invalid JSON. Last valid will be used.");
        if (jsonNodeOrError.isInvalid()) {
            return jsonNodeOrError.error();
        }

        val invalidNames = countInvalidVariableNames(jsonNodeOrError.node());
        if (invalidNames > 0) {
            val message = "Invalid variable names found: " + invalidNames + " error(s)";
            return ValidationResult.error(message);
        }

        return ValidationResult.success();
    }

    /**
     * Validates authorization subscription JSON document structure and mandatory
     * fields.
     * Ensures the document is a valid JSON object containing all required fields:
     * subject, action, and resource.
     *
     * @param jsonContent the JSON string to validate
     * @return validation result with status and descriptive message
     */
    public ValidationResult validateAuthorizationSubscription(String jsonContent) {
        val jsonNodeOrError = parseAndValidateJsonObject(jsonContent, "Invalid Authorization Subscription.");
        if (jsonNodeOrError.isInvalid()) {
            return jsonNodeOrError.error();
        }

        val missingFields = findMissingMandatoryFields(jsonNodeOrError.node());
        if (!missingFields.isEmpty()) {
            val message = "Missing mandatory fields: " + String.join(", ", missingFields);
            return ValidationResult.error(message);
        }

        return ValidationResult.success();
    }

    /**
     * Checks if any validation issues have ERROR severity.
     * Filters through all issues and returns true if at least one error exists.
     *
     * @param issues array of validation issues to check
     * @return true if any issue has ERROR severity, false otherwise
     */
    public static boolean hasErrorSeverityIssues(Issue[] issues) {
        return Arrays.stream(issues).anyMatch(issue -> Severity.ERROR == issue.getSeverity());
    }

    /**
     * Counts the number of validation issues with ERROR severity.
     * Filters and counts all issues marked as errors.
     *
     * @param issues array of validation issues to count
     * @return count of issues with ERROR severity
     */
    public static long countErrorSeverityIssues(Issue[] issues) {
        return Arrays.stream(issues).filter(issue -> Severity.ERROR == issue.getSeverity()).count();
    }

    /**
     * Checks if a variable name is valid according to identifier rules.
     * Valid names must:
     * - Not be null or empty
     * - Not exceed 100 characters
     * - Start with letter or underscore
     * - Contain only word characters (letters, digits, underscores)
     *
     * @param name the variable name to validate
     * @return true if the name is valid, false otherwise
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

    /*
     * Parses JSON content and validates it is an object.
     * Returns a wrapper containing either the parsed node or an error result.
     */
    private JsonNodeOrError parseAndValidateJsonObject(String jsonContent, String errorMessage) {
        try {
            val jsonNode = mapper.readTree(jsonContent);

            if (!jsonNode.isObject()) {
                return new JsonNodeOrError(ValidationResult.error("Must be a JSON Object"));
            }

            return new JsonNodeOrError(jsonNode);

        } catch (JsonProcessingException exception) {
            return new JsonNodeOrError(ValidationResult.error(errorMessage));
        }
    }

    /*
     * Counts invalid variable names in a JSON object.
     * A name is invalid if it exceeds maximum length or doesn't match the pattern.
     */
    private int countInvalidVariableNames(JsonNode jsonNode) {
        return (int) StreamSupport.stream(((Iterable<String>) jsonNode::fieldNames).spliterator(), false).filter(
                name -> name.length() > MAX_VARIABLE_NAME_LENGTH || !VALID_VARIABLE_NAME.matcher(name).matches())
                .count();
    }

    /*
     * Finds missing mandatory fields in an authorization subscription.
     * Checks for presence of subject, action, and resource fields.
     */
    private List<String> findMissingMandatoryFields(JsonNode jsonNode) {
        val missingFields = new ArrayList<String>();
        for (val mandatoryField : MANDATORY_SUBSCRIPTION_FIELDS) {
            if (jsonNode.get(mandatoryField) == null) {
                missingFields.add(mandatoryField);
            }
        }
        return missingFields;
    }

    /*
     * Wrapper holding either a parsed JSON node or a validation error.
     * Allows returning both success and error cases from parsing.
     */
    private record JsonNodeOrError(JsonNode node, ValidationResult error) {

        /*
         * Creates wrapper with successful parse result.
         */
        JsonNodeOrError(JsonNode node) {
            this(node, null);
        }

        /*
         * Creates wrapper with error result.
         */
        JsonNodeOrError(ValidationResult error) {
            this(null, error);
        }

        /*
         * Checks if parsing was successful.
         */
        boolean isInvalid() {
            return error != null;
        }
    }
}

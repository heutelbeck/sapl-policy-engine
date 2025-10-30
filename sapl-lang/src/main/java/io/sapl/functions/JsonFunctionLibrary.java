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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

/**
 * Function library providing JSON marshalling and unmarshalling operations.
 * <p>
 * Enables bidirectional conversion between JSON text and SAPL values. Primarily
 * useful for parsing JSON from external sources (APIs, databases, configuration
 * files) and serializing SAPL values for obligations, logging, or external
 * system integration.
 * <p>
 * Values round-trip correctly through conversion:
 * {@code json.jsonToVal(json.valToJson(value)) == value}
 */
@UtilityClass
@FunctionLibrary(name = JsonFunctionLibrary.NAME, description = JsonFunctionLibrary.DESCRIPTION, libraryDocumentation = JsonFunctionLibrary.DOCUMENTATION)
public class JsonFunctionLibrary {

    public static final String NAME        = "json";
    public static final String DESCRIPTION = "Function library for JSON marshalling and unmarshalling operations.";

    public static final String DOCUMENTATION = """
            # JSON Function Library

            Provides bidirectional conversion between JSON text and SAPL values.

            Use json.jsonToVal to parse JSON strings from external sources such as API responses,
            configuration files, or database fields stored as JSON text.

            Use json.valToJson to serialize SAPL values into JSON strings for obligations,
            advice, or when passing data to external systems.

            ## Examples

            Parse stored configuration:
            ```sapl
            policy "check-feature-flags"
            permit
            where
              var config = json.jsonToVal(resource.configJson);
              config.featureEnabled == true;
              config.minVersion <= subject.appVersion;
            ```

            Parse embedded permissions:
            ```sapl
            policy "validate-permissions"
            permit resource.type == "document"
            where
              var userPerms = json.jsonToVal(subject.permissionsJson);
              userPerms.canRead == true;
            ```

            Generate structured obligation data:
            ```sapl
            policy "require-audit"
            permit
            obligation
              {
                "auditEntry": json.valToJson({
                  "userId": subject.id,
                  "resourceId": resource.id,
                  "action": action.method,
                  "timestamp": time.now()
                })
              }
            ```
            """;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Converts a well-formed JSON document into a SAPL value.
     *
     * @param json the JSON text to parse
     * @return a Val representing the parsed JSON content, or an error if parsing
     * fails
     */
    @Function(docs = """
            ```jsonToVal(TEXT json)```: Converts a well-formed JSON document ```json``` into a SAPL
            value representing the content of the JSON document.
            Returns an error if the JSON is malformed.

            **Example:**
            ```sapl
            policy "check-embedded-role"
            permit action.method == "read"
            where
              var userMetadata = json.jsonToVal(subject.metadataJson);
              userMetadata.role == "admin";
            ```
            """)
    public static Val jsonToVal(@Text Val json) {
        try {
            return Val.ofJson(json.getText());
        } catch (Exception exception) {
            return Val.error("Failed to parse JSON: %s".formatted(exception.getMessage()));
        }
    }

    /**
     * Converts a SAPL value into a JSON string representation.
     *
     * @param value the value to convert to JSON
     * @return a Val containing the JSON string representation, or an error if
     * serialization fails
     */
    @Function(docs = """
            ```valToJson(value)```: Converts a SAPL ```value``` into a JSON string representation.
            Returns an error if serialization fails. Undefined and error values are returned unchanged.

            **Example:**
            ```sapl
            policy "log-decision-context"
            permit
            obligation
              {
                "type": "audit",
                "context": json.valToJson(subject)
              }
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToJson(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        try {
            return Val.of(JSON_MAPPER.writeValueAsString(value.get()));
        } catch (JsonProcessingException exception) {
            return Val.error("Failed to serialize to JSON: %s".formatted(exception.getMessage()));
        }
    }

}

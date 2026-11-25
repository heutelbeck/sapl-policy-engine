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
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Function library providing JSON marshalling and unmarshalling operations.
 * <p>
 * Enables bidirectional conversion between JSON text and SAPL values. Primarily
 * useful for parsing JSON from external
 * sources (APIs, databases, configuration files) and serializing SAPL values
 * for obligations, logging, or external
 * system integration.
 * <p>
 * Values round-trip correctly through conversion:
 * {@code json.jsonToVal(json.valToJson(value)) == value}
 */
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

            A single leading U+FEFF byte order mark is accepted before the JSON document.
            The input length limit includes this mark. U+FEFF inside a JSON string is preserved.

            ## Examples

            Parse stored configuration:
            ```sapl
            policy "check-feature-flags"
            permit
              var config = json.jsonToVal(resource.configJson);
              config.featureEnabled == true;
              config.minVersion <= subject.appVersion;
            ```

            Parse embedded permissions:
            ```sapl
            policy "validate-permissions"
            permit resource.type == "document";
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

            ## Limits

            To bound memory and computation on untrusted input, the following limits apply:

            - The input is limited to 1 MB.
            - Arrays and objects can nest at most 499 levels, including empty containers. Numbers allow 1000 coefficient digits and up to 1010 characters including sign, decimal point, and exponent.
            - Serialization output is limited to 10000000 characters.

            These limits apply because this input may originate from the authorization subscription or from policy information points, which are not vetted to the same degree as the policies and variables shipped with the PDP configuration.
            """;

    private static final String ERROR_FAILED_TO_SERIALIZE = "Failed to serialize to JSON: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * Converts a well-formed JSON document into a SAPL value.
     *
     * @param json
     * the JSON text to parse
     *
     * @return the parsed JSON content as a Value, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```jsonToVal(TEXT json)```: Converts a well-formed JSON document into a SAPL value
            representing the content of the JSON document. Returns an error if the JSON is malformed.

            Input longer than 1 MB (1048576 characters) is rejected with an error. Arrays and
            objects can nest at most 499 levels, including empty containers.
            A single leading U+FEFF byte order mark is accepted. The input length limit includes
            this mark. U+FEFF inside a JSON string is preserved.

            **Example:**
            ```sapl
            policy "check-embedded-role"
            permit action.method == "read";
              var userMetadata = json.jsonToVal(subject.metadataJson);
              userMetadata.role == "admin";
            ```
            """)
    public static Value jsonToVal(TextValue json) {
        val text = json.value();
        if (TextParseLimits.exceedsMaxInput(text)) {
            return Value.error(TextParseLimits.ERROR_INPUT_TOO_LARGE, TextParseLimits.MAX_INPUT_CHARS);
        }
        return ValueJsonMarshaller.json(text.startsWith("\uFEFF") ? text.substring(1) : text);
    }

    /**
     * Converts a SAPL value into a JSON string representation.
     *
     * @param value
     * the value to convert to JSON
     *
     * @return a TextValue containing the JSON string representation, or an
     * ErrorValue if serialization fails
     */
    @Function(docs = """
            ```valToJson(value)```: Converts a SAPL value into a JSON string representation.
            Returns an error if serialization fails.

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
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value valToJson(Value value) {
        try {
            val jsonNode   = ValueJsonMarshaller.toJsonNode(value);
            val jsonString = TextOutputLimits.write(writer -> JSON_MAPPER.writeValue(writer, jsonNode));
            return Value.of(jsonString);
        } catch (TextOutputLimits.OutputLimitExceededException exception) {
            return Value.error(exception.getMessage());
        } catch (JacksonException | IllegalArgumentException | IOException exception) {
            return Value.error(ERROR_FAILED_TO_SERIALIZE, exception.getMessage());
        }
    }
}

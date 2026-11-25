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
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.io.IOException;

/**
 * Function library providing YAML marshalling and unmarshalling operations.
 */
@FunctionLibrary(name = YamlFunctionLibrary.NAME, description = YamlFunctionLibrary.DESCRIPTION, libraryDocumentation = YamlFunctionLibrary.DOCUMENTATION)
public class YamlFunctionLibrary {

    public static final String NAME        = "yaml";
    public static final String DESCRIPTION = "Function library for YAML marshalling and unmarshalling operations.";

    public static final String DOCUMENTATION = """
            # YAML Function Library

            Enables YAML processing in SAPL policies for configuration-based authorization systems
            and cloud-native environments. Parse YAML configurations into SAPL values for policy
            evaluation, or serialize authorization decisions into YAML format for integration with
            infrastructure-as-code and configuration management systems.

            Reading uses YAML 1.2 core scalars, including octal and hexadecimal integers and exact
            decimal numbers. Quoted scalars stay text unless explicitly tagged. The reader accepts
            the standard string, integer, float, boolean, null, sequence, and mapping tags.
            Aliases resolve to their anchored values. A `<<` key stays an ordinary object key.
            In a flow collection, write an explicitly tagged empty string as `[!!str ""]`.
            Rust also reads `[!!str]` as a list containing an empty string. The Java YAML parser rejects this spelling.

            ## Limits

            To bound memory and computation on untrusted input, the following limits apply:

            - The input is limited to 1 MB.
            - Parsing is bounded to a maximum nesting depth of 500 and a maximum number length of 1000 characters.
            - At most 50 aliases may refer to collections in one document. Recursive values are rejected.
            - Serialization output is limited to 10000000 characters.

            These limits apply because this input may originate from the authorization subscription or from policy information points, which are not vetted to the same degree as the policies and variables shipped with the PDP configuration.
            """;

    private static final String ERROR_FAILED_TO_CONVERT = "Failed to convert value to YAML: %s";
    private static final String ERROR_FAILED_TO_PARSE   = "Failed to parse YAML: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    /**
     * Converts a well-formed YAML document into a SAPL value.
     *
     * @param yaml
     * the YAML text to parse
     *
     * @return the parsed YAML content as a Value, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```yamlToVal(TEXT yaml)```: Reads one YAML document using the YAML 1.2 core scalar types.
            Decimals keep their exact digits. Numbers outside the SAPL number domain reject the whole
            document. Each scalar key keeps its written text after its tag is validated. Aliases use
            the value of the referenced anchor.

            Input longer than 1 MB (1048576 characters) is rejected with an error, and nesting
            depth is bounded, so a hostile attribute value cannot exhaust the evaluation thread.

            **Example:**
            ```sapl
            policy "permit_resource_owner"
            permit
               var resourceConfig = "owner: alice\\nclassification: CONFIDENTIAL\\naccessLevel: 3";
               var parsedResource = yaml.yamlToVal(resourceConfig);
               parsedResource.owner == subject.name;
            ```
            """)
    public static Value yamlToVal(TextValue yaml) {
        if (TextParseLimits.exceedsMaxInput(yaml.value())) {
            return Value.error(TextParseLimits.ERROR_INPUT_TOO_LARGE, TextParseLimits.MAX_INPUT_CHARS);
        }
        try {
            return YamlValueReader.read(yaml.value());
        } catch (YamlEngineException | NumberFormatException exception) {
            return Value.error(ERROR_FAILED_TO_PARSE, exception.getMessage());
        }
    }

    /**
     * Converts a SAPL value into a YAML string representation.
     *
     * @param value
     * the value to convert to YAML
     *
     * @return a TextValue containing the YAML string representation, or an
     * ErrorValue if conversion fails
     */
    @Function(docs = """
            ```valToYaml(value)```: Writes one YAML document in block style, with two spaces per level
            and a final line break. Strings use JSON quotes and escapes. An ASCII identifier
            used as a key stays plain unless it denotes a YAML boolean or null. Numbers use SAPL
            wire notation. Empty arrays and objects are written as `[]` and `{}`.

            **Example:**
            ```sapl
            policy "export_audit_log"
            permit
               var auditEntry = {"user":"bob","action":"READ","resource":"/api/data","timestamp":"2025-01-15T10:30:00Z"};
               var auditYaml = yaml.valToYaml(auditEntry);
               // auditYaml contains YAML-formatted audit log entry
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value valToYaml(Value value) {
        try {
            return Value.of(YamlValueWriter.write(value));
        } catch (IllegalArgumentException | IOException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        }
    }
}

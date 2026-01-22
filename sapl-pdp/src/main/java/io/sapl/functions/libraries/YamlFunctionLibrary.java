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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Function library providing YAML marshalling and unmarshalling operations.
 */
@UtilityClass
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
            """;

    private static final String ERROR_FAILED_TO_CONVERT = "Failed to convert value to YAML: %s.";
    private static final String ERROR_FAILED_TO_PARSE   = "Failed to parse YAML: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * Converts a well-formed YAML document into a SAPL value.
     *
     * @param yaml
     * the YAML text to parse
     *
     * @return the parsed YAML content as a Value, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```yamlToVal(TEXT yaml)```: Converts a well-formed YAML document into a SAPL value
            representing the content of the YAML document.

            **Example:**
            ```sapl
            policy "permit_resource_owner"
            permit
               var resourceConfig = "owner: alice\\nclassification: CONFIDENTIAL\\naccessLevel: 3";
               var resource = yaml.yamlToVal(resourceConfig);
               resource.owner == subject.name;
            ```
            """)
    public static Value yamlToVal(TextValue yaml) {
        try {
            val jsonNode = YAML_MAPPER.readTree(yaml.value());
            return ValueJsonMarshaller.fromJsonNode(jsonNode);
        } catch (JsonProcessingException exception) {
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
            ```valToYaml(value)```: Converts a SAPL value into a YAML string representation.

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
            val jsonNode   = ValueJsonMarshaller.toJsonNode(value);
            val yamlString = YAML_MAPPER.writeValueAsString(jsonNode);
            return Value.of(yamlString);
        } catch (JsonProcessingException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        }
    }
}

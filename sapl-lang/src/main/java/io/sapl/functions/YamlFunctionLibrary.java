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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

/**
 * Function library providing YAML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = YamlFunctionLibrary.NAME, description = YamlFunctionLibrary.DESCRIPTION, libraryDocumentation = YamlFunctionLibrary.LIBRARY_DOCUMENTATION)
public class YamlFunctionLibrary {

    public static final String NAME        = "yaml";
    public static final String DESCRIPTION = "Function library for YAML marshalling and unmarshalling operations.";

    public static final String LIBRARY_DOCUMENTATION = """
            ## YAML Functions

            Enables YAML processing in SAPL policies for configuration-based authorization systems and cloud-native environments.
            Parse YAML configurations into SAPL values for policy evaluation, or serialize authorization decisions into YAML
            format for integration with infrastructure-as-code and configuration management systems.
            """;

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * Converts a well-formed YAML document into a SAPL value.
     *
     * @param yaml the YAML text to parse
     * @return a Val representing the parsed YAML content, or an error if parsing
     * fails
     */
    @Function(docs = """
            ```yamlToVal(TEXT yaml)```: Converts a well-formed YAML document ```yaml``` into a SAPL
            value representing the content of the YAML document.

            **Example:**
            ```sapl
            policy "permit_resource_owner"
            permit
            where
               var resourceConfig = "owner: alice\\nclassification: CONFIDENTIAL\\naccessLevel: 3";
               var resource = yaml.yamlToVal(resourceConfig);
               resource.owner == subject.name;
            ```
            """)
    public static Val yamlToVal(@Text Val yaml) {
        try {
            return Val.of(YAML_MAPPER.readTree(yaml.getText()));
        } catch (Exception exception) {
            return Val.error("Failed to parse YAML: %s".formatted(exception.getMessage()));
        }
    }

    /**
     * Converts a SAPL value into a YAML string representation.
     *
     * @param value the value to convert to YAML
     * @return a Val containing the YAML string representation, or an error if
     * conversion fails
     */
    @Function(docs = """
            ```valToYaml(value)```: Converts a SAPL ```value``` into a YAML string representation.

            **Example:**
            ```sapl
            policy "export_audit_log"
            permit
            where
               var auditEntry = {"user":"bob","action":"READ","resource":"/api/data","timestamp":"2025-01-15T10:30:00Z"};
               var auditYaml = yaml.valToYaml(auditEntry);
               // auditYaml contains YAML-formatted audit log entry
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToYaml(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        try {
            return Val.of(YAML_MAPPER.writeValueAsString(value.get()));
        } catch (Exception exception) {
            return Val.error("Failed to convert value to YAML: %s".formatted(exception.getMessage()));
        }
    }

}

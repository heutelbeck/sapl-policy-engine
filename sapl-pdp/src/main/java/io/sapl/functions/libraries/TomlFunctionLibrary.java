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
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Function library providing TOML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = TomlFunctionLibrary.NAME, description = TomlFunctionLibrary.DESCRIPTION, libraryDocumentation = TomlFunctionLibrary.DOCUMENTATION)
public class TomlFunctionLibrary {

    public static final String NAME        = "toml";
    public static final String DESCRIPTION = "Function library for TOML marshalling and unmarshalling operations.";

    public static final String DOCUMENTATION = """
            # TOML Function Library

            Enables TOML configuration file processing in SAPL policies for systems using TOML-based
            configuration management. Parse TOML configuration files into SAPL values for policy
            evaluation, or serialize authorization configurations into TOML format for application
            configuration files and infrastructure management.
            """;

    private static final String ERROR_FAILED_TO_CONVERT = "Failed to convert value to TOML: %s.";
    private static final String ERROR_FAILED_TO_PARSE   = "Failed to parse TOML: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    private static final TomlMapper TOML_MAPPER = new TomlMapper();

    /**
     * Converts a well-formed TOML document into a SAPL value.
     *
     * @param toml
     * the TOML text to parse
     *
     * @return the parsed TOML content as a Value, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```tomlToVal(TEXT toml)```: Converts a well-formed TOML document into a SAPL value
            representing the content of the TOML document.

            **Example:**
            ```sapl
            policy "permit_based_on_config"
            permit
               var configToml = "[resource]\\nowner = \\"alice\\"\\nclassification = \\"CONFIDENTIAL\\"\\naccessLevel = 3";
               var config = toml.tomlToVal(configToml);
               config.resource.owner == subject.name;
            ```
            """)
    public static Value tomlToVal(TextValue toml) {
        try {
            val jsonNode = TOML_MAPPER.readTree(toml.value());
            return ValueJsonMarshaller.fromJsonNode(jsonNode);
        } catch (JsonProcessingException exception) {
            return Value.error(ERROR_FAILED_TO_PARSE, exception.getMessage());
        }
    }

    /**
     * Converts a SAPL value into a TOML string representation.
     *
     * @param value
     * the value to convert to TOML
     *
     * @return a TextValue containing the TOML string representation, or an
     * ErrorValue if conversion fails
     */
    @Function(docs = """
            ```valToToml(value)```: Converts a SAPL value into a TOML string representation.

            **Example:**
            ```sapl
            policy "export_policy_config"
            permit
               var policyConfig = {"permissions":{"user":"bob","actions":["READ","WRITE"],"resources":["/api/data"]}};
               var configToml = toml.valToToml(policyConfig);
               // configToml contains TOML-formatted configuration
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value valToToml(Value value) {
        try {
            val jsonNode   = ValueJsonMarshaller.toJsonNode(value);
            val tomlString = TOML_MAPPER.writeValueAsString(jsonNode);
            return Value.of(tomlString);
        } catch (JsonProcessingException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        }
    }
}

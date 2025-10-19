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
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Function library providing YAML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = YamlFunctionLibrary.NAME, description = YamlFunctionLibrary.DESCRIPTION)
public class YamlFunctionLibrary {

    public static final String NAME        = "yaml";
    public static final String DESCRIPTION = "Function library for YAML marshalling and unmarshalling operations.";

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /**
     * Converts a well-formed YAML document into a SAPL value.
     *
     * @param yaml the YAML text to parse
     * @return a Val representing the parsed YAML content
     */
    @SneakyThrows
    @Function(docs = """
            ```yamlToVal(TEXT yaml)```: Converts a well-formed YAML document ```yaml``` into a SAPL
            value representing the content of the YAML document.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
               var yamlText = "name: Poppy\\ncolor: RED\\npetals: 9";
               yaml.yamlToVal(yamlText) == {"name":"Poppy","color":"RED","petals":9};
            ```
            """)
    public static Val yamlToVal(@Text Val yaml) {
        return Val.of(YAML_MAPPER.readTree(yaml.getText()));
    }

    /**
     * Converts a SAPL value into a YAML string representation.
     *
     * @param value the value to convert to YAML
     * @return a Val containing the YAML string representation
     */
    @SneakyThrows
    @Function(docs = """
            ```valToYaml(value)```: Converts a SAPL ```value``` into a YAML string representation.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
               var object = {"name":"Poppy","color":"RED","petals":9};
               var expected = "---\\nname: \\"Poppy\\"\\ncolor: \\"RED\\"\\npetals: 9\\n";
               yaml.valToYaml(object) == expected;
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToYaml(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        return Val.of(YAML_MAPPER.writeValueAsString(value.get()));
    }

}

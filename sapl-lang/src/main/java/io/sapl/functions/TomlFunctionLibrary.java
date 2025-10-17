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

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Function library providing TOML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = TomlFunctionLibrary.NAME, description = TomlFunctionLibrary.DESCRIPTION)
public class TomlFunctionLibrary {

    public static final String NAME        = "toml";
    public static final String DESCRIPTION = "Function library for TOML marshalling and unmarshalling operations.";

    private static final TomlMapper TOML_MAPPER = new TomlMapper();

    /**
     * Converts a well-formed TOML document into a SAPL value.
     *
     * @param toml the TOML text to parse
     * @return a Val representing the parsed TOML content
     */
    @SneakyThrows
    @Function(docs = """
            ```tomlToVal(TEXT toml)```: Converts a well-formed TOML document ```toml``` into a SAPL
            value representing the content of the TOML document.

            **Example:**
            ```
            import toml.*
            policy "example"
            permit
            where
               var tomlText = "[flower]\\nname = \\"Poppy\\"\\ncolor = \\"RED\\"\\npetals = 9";
               tomlToVal(tomlText) == {"flower":{"name":"Poppy","color":"RED","petals":9}};
            ```
            """)
    public static Val tomlToVal(@Text Val toml) {
        return Val.of(TOML_MAPPER.readTree(toml.getText()));
    }

    /**
     * Converts a SAPL value into a TOML string representation.
     *
     * @param value the value to convert to TOML
     * @return a Val containing the TOML string representation
     */
    @SneakyThrows
    @Function(docs = """
            ```valToToml(value)```: Converts a SAPL ```value``` into a TOML string representation.

            **Example:**
            ```
            import toml.*
            policy "example"
            permit
            where
               var object = {"flower":{"name":"Poppy","color":"RED","petals":9}};
               var expected = "[flower]\\nname = \\"Poppy\\"\\ncolor = \\"RED\\"\\npetals = 9\\n";
               valToToml(object) == expected;
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToToml(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        return Val.of(TOML_MAPPER.writeValueAsString(value.get()));
    }

}

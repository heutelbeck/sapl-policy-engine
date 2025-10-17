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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Function library providing JSON marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = JsonFunctionLibrary.NAME, description = JsonFunctionLibrary.DESCRIPTION)
public class JsonFunctionLibrary {

    public static final String NAME        = "json";
    public static final String DESCRIPTION = "Function library for JSON marshalling and unmarshalling operations.";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Converts a well-formed JSON document into a SAPL value.
     *
     * @param json the JSON text to parse
     * @return a Val representing the parsed JSON content
     */
    @SneakyThrows
    @Function(docs = """
            ```jsonToVal(TEXT json)```: Converts a well-formed JSON document ```json``` into a SAPL
            value representing the content of the JSON document.

            **Example:**
            ```
            import json.*
            policy "example"
            permit
            where
               var jsonText = "{ \\"hello\\": \\"world\\" }";
               jsonToVal(jsonText) == { "hello":"world" };
            ```
            """)
    public static Val jsonToVal(@Text Val json) {
        return Val.ofJson(json.getText());
    }

    /**
     * Converts a SAPL value into a JSON string representation.
     *
     * @param value the value to convert to JSON
     * @return a Val containing the JSON string representation
     */
    @SneakyThrows
    @Function(docs = """
            ```valToJson(value)```: Converts a SAPL ```value``` into a JSON string representation.

            **Example:**
            ```
            import json.*
            policy "example"
            permit
            where
               var object = { "hello":"world" };
               valToJson(object) == "{\\"hello\\":\\"world\\"}";
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToJson(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        return Val.of(JSON_MAPPER.writeValueAsString(value.get()));
    }

}

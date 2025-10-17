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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Function library providing XML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = XmlFunctionLibrary.NAME, description = XmlFunctionLibrary.DESCRIPTION)
public class XmlFunctionLibrary {

    public static final String NAME        = "xml";
    public static final String DESCRIPTION = "Function library for XML marshalling and unmarshalling operations.";

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    /**
     * Converts a well-formed XML document into a SAPL value.
     *
     * @param xml the XML text to parse
     * @return a Val representing the parsed XML content
     */
    @SneakyThrows
    @Function(docs = """
            ```xmlToVal(TEXT xml)```: Converts a well-formed XML document ```xml``` into a SAPL
            value representing the content of the XML document.

            **Example:**
            ```
            import xml.*
            policy "example"
            permit
            where
               var xmlText = "<Flower><name>Poppy</name><color>RED</color><petals>9</petals></Flower>";
               xmlToVal(xmlText) == {"n":"Poppy","color":"RED","petals":"9"};
            ```
            """)
    public static Val xmlToVal(@Text Val xml) {
        return Val.of(XML_MAPPER.readTree(xml.getText()));
    }

    /**
     * Converts a SAPL value into an XML string representation.
     *
     * @param value the value to convert to XML
     * @return a Val containing the XML string representation
     */
    @SneakyThrows
    @Function(docs = """
            ```valToXml(value)```: Converts a SAPL ```value``` into an XML string representation.

            **Example:**
            ```
            import xml.*
            policy "example"
            permit
            where
               var object = {"name":"Poppy","color":"RED","petals":9};
               var expected = "<LinkedHashMap><name>Poppy</name><color>RED</color><petals>9</petals></LinkedHashMap>";
               valToXml(object) == expected;
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToXml(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        return Val.of(XML_MAPPER.writeValueAsString(value.get()));
    }

}

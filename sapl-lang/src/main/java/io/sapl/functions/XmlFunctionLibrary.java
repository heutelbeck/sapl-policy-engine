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
import lombok.experimental.UtilityClass;

/**
 * Function library providing XML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = XmlFunctionLibrary.NAME, description = XmlFunctionLibrary.DESCRIPTION, libraryDocumentation = XmlFunctionLibrary.LIBRARY_DOCUMENTATION)
public class XmlFunctionLibrary {

    public static final String NAME        = "xml";
    public static final String DESCRIPTION = "Function library for XML marshalling and unmarshalling operations.";

    public static final String LIBRARY_DOCUMENTATION = """
            ## XML Functions

            Enables XML processing in SAPL policies for systems that exchange authorization-relevant data in XML format.
            Parse XML from external systems into SAPL values for policy evaluation, or serialize policy decisions and
            context into XML for logging or integration.
            """;

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    /**
     * Converts a well-formed XML document into a SAPL value.
     *
     * @param xml the XML text to parse
     * @return a Val representing the parsed XML content, or an error if parsing
     * fails
     */
    @Function(docs = """
            ```xmlToVal(TEXT xml)```: Converts a well-formed XML document ```xml``` into a SAPL
            value representing the content of the XML document.

            **Example:**
            ```sapl
            policy "permit_with_resource_attributes"
            permit
            where
               var resourceXml = "<Resource><owner>alice</owner><classification>PUBLIC</classification></Resource>";
               var resource = xml.xmlToVal(resourceXml);
               resource.owner == subject.name;
            ```
            """)
    public static Val xmlToVal(@Text Val xml) {
        try {
            return Val.of(XML_MAPPER.readTree(xml.getText()));
        } catch (Exception exception) {
            return Val.error("Failed to parse XML: %s".formatted(exception.getMessage()));
        }
    }

    /**
     * Converts a SAPL value into an XML string representation.
     *
     * @param value the value to convert to XML
     * @return a Val containing the XML string representation, or an error if
     * conversion fails
     */
    @Function(docs = """
            ```valToXml(value)```: Converts a SAPL ```value``` into an XML string representation.

            **Example:**
            ```sapl
            policy "log_access_attempt"
            permit
            where
               var accessLog = {"user":"bob","resource":"/documents/report.pdf","action":"READ","timestamp":"2025-01-15T10:30:00Z"};
               var logXml = xml.valToXml(accessLog);
               // logXml contains: <LinkedHashMap><user>bob</user><resource>/documents/report.pdf</resource>...
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToXml(Val value) {
        if (value.isError() || value.isUndefined()) {
            return value;
        }
        try {
            return Val.of(XML_MAPPER.writeValueAsString(value.get()));
        } catch (Exception exception) {
            return Val.error("Failed to convert value to XML: %s".formatted(exception.getMessage()));
        }
    }

}

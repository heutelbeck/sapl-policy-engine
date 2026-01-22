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
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Function library providing XML marshalling and unmarshalling operations.
 */
@UtilityClass
@FunctionLibrary(name = XmlFunctionLibrary.NAME, description = XmlFunctionLibrary.DESCRIPTION, libraryDocumentation = XmlFunctionLibrary.DOCUMENTATION)
public class XmlFunctionLibrary {

    public static final String NAME        = "xml";
    public static final String DESCRIPTION = "Function library for XML marshalling and unmarshalling operations.";

    public static final String DOCUMENTATION = """
            # XML Function Library

            Enables XML processing in SAPL policies for systems that exchange authorization-relevant
            data in XML format. Parse XML from external systems into SAPL values for policy evaluation,
            or serialize policy decisions and context into XML for logging or integration.
            """;

    private static final String ERROR_FAILED_TO_CONVERT = "Failed to convert value to XML: %s.";
    private static final String ERROR_FAILED_TO_PARSE   = "Failed to parse XML: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    private static final XmlMapper XML_MAPPER = new XmlMapper();

    /**
     * Converts a well-formed XML document into a SAPL value.
     *
     * @param xml
     * the XML text to parse
     *
     * @return the parsed XML content as a Value, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```xmlToVal(TEXT xml)```: Converts a well-formed XML document into a SAPL value
            representing the content of the XML document.

            **Example:**
            ```sapl
            policy "permit_with_resource_attributes"
            permit
               var resourceXml = "<Resource><owner>alice</owner><classification>PUBLIC</classification></Resource>";
               var resource = xml.xmlToVal(resourceXml);
               resource.owner == subject.name;
            ```
            """)
    public static Value xmlToVal(TextValue xml) {
        try {
            val jsonNode = XML_MAPPER.readTree(xml.value());
            return ValueJsonMarshaller.fromJsonNode(jsonNode);
        } catch (JsonProcessingException exception) {
            return Value.error(ERROR_FAILED_TO_PARSE, exception.getMessage());
        }
    }

    /**
     * Converts a SAPL value into an XML string representation.
     *
     * @param value
     * the value to convert to XML
     *
     * @return a TextValue containing the XML string representation, or an
     * ErrorValue if conversion fails
     */
    @Function(docs = """
            ```valToXml(value)```: Converts a SAPL value into an XML string representation.

            **Example:**
            ```sapl
            policy "log_access_attempt"
            permit
               var accessLog = {"user":"bob","resource":"/documents/report.pdf","action":"READ","timestamp":"2025-01-15T10:30:00Z"};
               var logXml = xml.valToXml(accessLog);
               // logXml contains XML-formatted access log
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value valToXml(Value value) {
        try {
            val jsonNode  = ValueJsonMarshaller.toJsonNode(value);
            val xmlString = XML_MAPPER.writeValueAsString(jsonNode);
            return Value.of(xmlString);
        } catch (JsonProcessingException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        }
    }
}

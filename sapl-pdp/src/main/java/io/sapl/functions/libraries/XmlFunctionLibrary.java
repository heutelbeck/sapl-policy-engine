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
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;

import javax.xml.stream.XMLInputFactory;
import java.io.IOException;

/**
 * Function library providing XML marshalling and unmarshalling operations.
 */
@FunctionLibrary(name = XmlFunctionLibrary.NAME, description = XmlFunctionLibrary.DESCRIPTION, libraryDocumentation = XmlFunctionLibrary.DOCUMENTATION)
public class XmlFunctionLibrary {

    public static final String NAME        = "xml";
    public static final String DESCRIPTION = "Function library for XML marshalling and unmarshalling operations.";

    public static final String DOCUMENTATION = """
            # XML Function Library

            Enables XML processing in SAPL policies for systems that exchange authorization-relevant
            data in XML format. Parse XML from external systems into SAPL values for policy evaluation,
            or serialize policy decisions and context into XML for logging or integration.

            ## Limits

            To bound memory and computation on untrusted input, the following limits apply:

            - The input is limited to 1 MB.
            - Parsing is bounded to a maximum nesting depth of 500 and a maximum number length of 1000 characters.
            - Serialization output is limited to 10000000 characters.

            DTD processing and external entity resolution are disabled, so XXE and entity-expansion payloads are rejected with an error.

            These limits apply because this input may originate from the authorization subscription or from policy information points, which are not vetted to the same degree as the policies and variables shipped with the PDP configuration.
            """;

    private static final String ERROR_FAILED_TO_CONVERT = "Failed to convert value to XML: %s.";
    private static final String ERROR_FAILED_TO_PARSE   = "Failed to parse XML: %s.";

    private static final String SCHEMA_RETURNS_TEXT = """
            {
              "type": "string"
            }
            """;

    private static final XmlMapper XML_MAPPER = hardenedXmlMapper();

    /**
     * Builds an XmlMapper on an explicitly hardened StAX input factory. DTDs and
     * external entities are disabled on the parser the engine owns, so XXE,
     * external-entity SSRF, and entity-expansion payloads fail closed regardless
     * of third-party default changes.
     */
    private static XmlMapper hardenedXmlMapper() {
        val inputFactory = XMLInputFactory.newFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return new XmlMapper(XmlFactory.builder().xmlInputFactory(inputFactory)
                .streamReadConstraints(TextParseLimits.STREAM_READ_CONSTRAINTS).build());
    }

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

            DTD processing and external entity resolution are disabled. Documents that declare
            or reference entities, such as XXE or entity-expansion payloads, are rejected with
            an error. Plain data XML without a document type definition is supported.

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
        if (TextParseLimits.exceedsMaxInput(xml.value())) {
            return Value.error(TextParseLimits.ERROR_INPUT_TOO_LARGE, TextParseLimits.MAX_INPUT_CHARS);
        }
        try {
            val jsonNode = XML_MAPPER.readTree(xml.value());
            return ValueJsonMarshaller.fromJsonNode(jsonNode);
        } catch (JacksonException exception) {
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
        if (!ValueJsonMarshaller.isJsonCompatible(value)) {
            return Value.error(ERROR_FAILED_TO_CONVERT, value);
        }
        try {
            val jsonNode  = ValueJsonMarshaller.toJsonNode(value);
            val xmlString = TextOutputLimits.write(writer -> XML_MAPPER.writeValue(writer, jsonNode));
            return Value.of(xmlString);
        } catch (TextOutputLimits.OutputLimitExceededException exception) {
            return Value.error(exception.getMessage());
        } catch (JacksonException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        } catch (IOException exception) {
            return Value.error(ERROR_FAILED_TO_CONVERT, exception.getMessage());
        }
    }
}

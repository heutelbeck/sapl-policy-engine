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

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("XML document writing")
class XmlWriterContractTests {
    @MethodSource("documents")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Object data uses the documented XML form")
    void whenNormalObjectsAreWrittenThenXmlUsesTheDocumentedShape(String name, String input, String xml) {
        val broker = new DefaultFunctionBroker();
        broker.load(new XmlFunctionLibrary());
        val value = ValueJsonMarshaller.json(input);
        assertThat(broker.evaluateFunction(new FunctionInvocation("xml.valToXml", List.of(value))))
                .isEqualTo(Value.of(xml));
    }

    static Stream<Arguments> documents() {
        return Stream.of(arguments("invoice",
                "{\"invoice\":{\"title\":\"Pens & paper <West>\",\"items\":[{\"title\":\"Pen\",\"quantity\":2},{\"title\":\"Paper\",\"quantity\":1}],\"note\":null}}",
                "<root><invoice><title>Pens &amp; paper &lt;West></title><items><title>Pen</title><quantity>2</quantity></items><items><title>Paper</title><quantity>1</quantity></items><note/></invoice></root>"),
                arguments("single field", "{\"title\":\"Invoice\"}", "<root><title>Invoice</title></root>"),
                arguments("repeated items", "{\"items\":[\"Pen\",\"Paper\"]}",
                        "<root><items>Pen</items><items>Paper</items></root>"),
                arguments("empty array", "{\"items\":[]}", "<root/>"), arguments("empty object", "{}", "<root/>"),
                arguments("null and empty text", "{\"note\":null,\"title\":\"\"}",
                        "<root><note/><title></title></root>"),
                arguments("normal XML names", "{\"line-item\":\"Pen\",\"_title\":\"Invoice\",\"caf\\u00e9\":\"West\"}",
                        "<root><line-item>Pen</line-item><_title>Invoice</_title><caf\u00e9>West</caf\u00e9></root>"),
                arguments("closing brackets in text", "{\"title\":\"West ]]> East\"}",
                        "<root><title>West ]]&gt; East</title></root>"),
                arguments("canonical decimals", "{\"amount\":2.50,\"total\":1e20}",
                        "<root><amount>2.5</amount><total>100000000000000000000</total></root>"));
    }

    @MethodSource("unsupportedValues")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Unsupported structures return an error")
    void whenXmlCannotRepresentTheValueThenTheWriterReturnsAnError(String input, String reason) {
        val broker = new DefaultFunctionBroker();
        broker.load(new XmlFunctionLibrary());
        val value = ValueJsonMarshaller.json(input);
        assertThat(broker.evaluateFunction(new FunctionInvocation("xml.valToXml", List.of(value))))
                .isEqualTo(Value.error("Failed to convert value to XML: %s", reason));
    }

    static Stream<Arguments> unsupportedValues() {
        return Stream.of(arguments("\"Invoice\"", "The XML value must be an object."),
                arguments("[\"Pen\",\"Paper\"]", "The XML value must be an object."),
                arguments("null", "The XML value must be an object."),
                arguments("{\"items\":[[\"Pen\"]]}", "XML arrays must not contain arrays."),
                arguments("{\"order title\":\"Invoice\"}", "The key 'order title' is no XML name."));
    }

    @Test
    @DisplayName("Text objects retain their data on a normal roundtrip")
    void whenTextObjectsAreWrittenAndReadThenTheDataIsPreserved() {
        val broker = new DefaultFunctionBroker();
        broker.load(new XmlFunctionLibrary());
        val original = ValueJsonMarshaller
                .json("{\"title\":\"Invoice\",\"summary\":\"Pens & paper\",\"customer\":{\"name\":\"West\"}}");
        val xml      = broker.evaluateFunction(new FunctionInvocation("xml.valToXml", List.of(original)));
        assertThat(broker.evaluateFunction(new FunctionInvocation("xml.xmlToVal", List.of(xml)))).isEqualTo(original);
    }

}

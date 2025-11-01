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

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class XmlFunctionLibraryTests {

    private static final String CULTIST_RECORD = """
            <CultistRecord>
                <name>Wilbur Whateley</name>
                <role>ACOLYTE</role>
                <securityLevel>3</securityLevel>
            </CultistRecord>
            """;

    private static final String HTML_DOCUMENT = """
            <!DOCTYPE html>
            <html>
            <body>
            <p>First</p>
            <p>Second</p>
            </body>
            </html>
            """;

    @Test
    void xmlToValParsesSimpleXml() {
        val result = XmlFunctionLibrary.xmlToVal(Val.of(CULTIST_RECORD));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesHtmlDocument() {
        val result = XmlFunctionLibrary.xmlToVal(Val.of(HTML_DOCUMENT));
        assertThat(result.get().get("body").get("p").get(0).asText()).isEqualTo("First");
        assertThat(result.get().get("body").get("p").get(1).asText()).isEqualTo("Second");
    }

    @Test
    void xmlToValParsesNestedElements() {
        val xml    = """
                <Ritual>
                    <invocation>
                        <chant>Ia! Ia! Cthulhu fhtagn!</chant>
                    </invocation>
                </Ritual>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("invocation").get("chant").asText()).isEqualTo("Ia! Ia! Cthulhu fhtagn!");
    }

    @Test
    void xmlToValParsesSelfClosingTags() {
        val xml    = "<Artifact><sealed/></Artifact>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesMultipleChildrenWithSameName() {
        val xml    = """
                <Grimoire>
                    <chapter>The Shadow Over Innsmouth</chapter>
                    <chapter>The Dunwich Horror</chapter>
                    <chapter>At the Mountains of Madness</chapter>
                </Grimoire>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("chapter").isArray()).isTrue();
        assertThat(result.get().get("chapter").size()).isEqualTo(3);
    }

    @Test
    void xmlToValParsesAttributes() {
        val xml    = "<Cultist name=\"Lavinia Whateley\" dangerLevel=\"EXTREME\"/>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValParsesEmptyElement() {
        val xml    = "<VoidSpace></VoidSpace>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.isDefined()).isTrue();
    }

    @Test
    void xmlToValHandlesWhitespace() {
        val xml    = """
                <Prophecy>
                    <text>   When the stars are right   </text>
                </Prophecy>
                """;
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("text").asText()).contains("When the stars are right");
    }

    @Test
    void xmlToValHandlesSpecialCharacters() {
        val xml    = "<Warning><message>R&apos;lyeh &amp; Carcosa</message></Warning>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("message").asText()).isEqualTo("R'lyeh & Carcosa");
    }

    @Test
    void xmlToValHandlesUnicodeCharacters() {
        val xml    = "<Inscription><text>Ph'nglui mglw'nafh 世界 🌙</text></Inscription>";
        val result = XmlFunctionLibrary.xmlToVal(Val.of(xml));
        assertThat(result.get().get("text").asText()).isEqualTo("Ph'nglui mglw'nafh 世界 🌙");
    }

    @ParameterizedTest
    @ValueSource(strings = { "<invalid", "<Necronomicon><unclosed>", "}NOT/><XML", "<root></wrong>", "not xml at all" })
    void xmlToValReturnsErrorForInvalidXml(String invalidXml) {
        val result = XmlFunctionLibrary.xmlToVal(Val.of(invalidXml));
        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).startsWith("Failed to parse XML:");
    }

    @Test
    void valToXmlConvertsObjectToXmlString() {
        val object = Val.JSON.objectNode();
        object.put("name", "Azathoth");
        object.put("title", "Daemon Sultan");
        object.put("threatLevel", 9);

        val result = XmlFunctionLibrary.valToXml(Val.of(object));
        assertThat(result.getText()).contains("<name>Azathoth</name>").contains("<title>Daemon Sultan</title>")
                .contains("<threatLevel>9</threatLevel>");
    }

    @Test
    void valToXmlHandlesNestedObjects() {
        val ritual   = Val.JSON.objectNode();
        val location = Val.JSON.objectNode();
        location.put("place", "Miskatonic University");
        ritual.set("location", location);

        val result = XmlFunctionLibrary.valToXml(Val.of(ritual));
        assertThat(result.getText()).contains("<location>").contains("<place>Miskatonic University</place>");
    }

    @Test
    void valToXmlReturnsErrorForErrorValue() {
        val error  = Val.error("Summoning failed.");
        val result = XmlFunctionLibrary.valToXml(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToXmlReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = XmlFunctionLibrary.valToXml(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void valToXmlHandlesEmptyObject() {
        val result = XmlFunctionLibrary.valToXml(Val.ofEmptyObject());
        assertThat(result.getText()).isNotEmpty();
    }

    @Test
    void valToXmlHandlesPrimitiveValues() {
        val result = XmlFunctionLibrary.valToXml(Val.of("The King in Yellow"));
        assertThat(result.getText()).contains("The King in Yellow");
    }

    @Test
    void valToXmlHandlesNumbers() {
        val result = XmlFunctionLibrary.valToXml(Val.of(1928));
        assertThat(result.getText()).contains("1928");
    }

    @Test
    void valToXmlHandlesBooleans() {
        val result = XmlFunctionLibrary.valToXml(Val.TRUE);
        assertThat(result.getText()).contains("true");
    }

}

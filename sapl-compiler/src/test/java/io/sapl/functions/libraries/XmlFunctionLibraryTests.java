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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class XmlFunctionLibraryTests {

    @Test
    void whenSimpleElement_thenParsesCorrectly() {
        val xml    = "<cultist>Wilbur Whateley</cultist>";
        val result = XmlFunctionLibrary.xmlToVal(Value.of(xml));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).containsEntry("", Value.of("Wilbur Whateley"));
    }

    @Test
    void whenNestedElements_thenParsesCorrectly() {
        val xml    = "<entity><name>Azathoth</name><title>Daemon Sultan</title></entity>";
        val result = XmlFunctionLibrary.xmlToVal(Value.of(xml));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val obj = (ObjectValue) result;
        assertThat(obj).containsEntry("name", Value.of("Azathoth")).containsEntry("title", Value.of("Daemon Sultan"));
    }

    @Test
    void whenDeeplyNestedElements_thenParsesCorrectly() {
        val xml    = "<ritual><name>Summoning</name><location><site>Miskatonic University</site></location></ritual>";
        val result = XmlFunctionLibrary.xmlToVal(Value.of(xml));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val ritual   = (ObjectValue) result;
        val location = (ObjectValue) ritual.get("location");
        assertThat(ritual).containsEntry("name", Value.of("Summoning"));
        assertThat(location).containsEntry("site", Value.of("Miskatonic University"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void whenBooleanContent_thenParsesAsText(String boolValue) {
        val xml    = "<sealed>" + boolValue + "</sealed>";
        val result = XmlFunctionLibrary.xmlToVal(Value.of(xml));

        assertThat(result).isInstanceOf(ObjectValue.class);
        assertThat((ObjectValue) result).containsEntry("", Value.of(boolValue));
    }

    @Test
    void whenEmptyElement_thenParsesCorrectly() {
        val xml    = "<empty></empty>";
        val result = XmlFunctionLibrary.xmlToVal(Value.of(xml));

        assertThat(result).isInstanceOf(ObjectValue.class);
        assertThat((ObjectValue) result).isEmpty();
    }

    @Test
    void whenInvalidXml_thenReturnsError() {
        val result = XmlFunctionLibrary.xmlToVal(Value.of("<unclosed"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).startsWith("Failed to parse XML:");
    }

    @Test
    void whenObjectToXml_thenConvertsCorrectly() {
        val object = ObjectValue.builder().put("name", Value.of("Nyarlathotep"))
                .put("title", Value.of("Crawling Chaos")).build();

        val result = XmlFunctionLibrary.valToXml(object);

        assertThat(result).isInstanceOf(TextValue.class);
        val xmlText = ((TextValue) result).value();
        assertThat(xmlText).contains("name").contains("Nyarlathotep").contains("title").contains("Crawling Chaos");
    }

    @Test
    void whenNestedObjectToXml_thenConvertsCorrectly() {
        val location = ObjectValue.builder().put("site", Value.of("R'lyeh")).build();
        val ritual   = ObjectValue.builder().put("location", location).build();

        val result = XmlFunctionLibrary.valToXml(ritual);

        assertThat(result).isInstanceOf(TextValue.class);
        val xmlText = ((TextValue) result).value();
        assertThat(xmlText).contains("location").contains("site").contains("R'lyeh");
    }

    @Test
    void whenEmptyObject_thenConvertsToEmptyXml() {
        val result = XmlFunctionLibrary.valToXml(Value.EMPTY_OBJECT);

        assertThat(result).isInstanceOf(TextValue.class);
    }

    @Test
    void whenRoundTrip_thenPreservesData() {
        val original   = "<investigator><name>Carter</name><sanity>77</sanity></investigator>";
        val parsed     = XmlFunctionLibrary.xmlToVal(Value.of(original));
        val serialized = XmlFunctionLibrary.valToXml(parsed);
        val reparsed   = XmlFunctionLibrary.xmlToVal((TextValue) serialized);

        assertThat(reparsed).isInstanceOf(ObjectValue.class);
        val investigator = (ObjectValue) reparsed;
        assertThat(investigator).containsEntry("name", Value.of("Carter")).containsEntry("sanity", Value.of("77"));
    }
}

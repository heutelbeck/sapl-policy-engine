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
package io.sapl.api.documentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void whenSerializingLibraryDocumentation_thenJsonIsValid() throws Exception {
        val documentation = createSampleLibraryDocumentation();

        val json = objectMapper.writeValueAsString(documentation);

        assertThat(json).contains("\"type\":\"FUNCTION_LIBRARY\"").contains("\"name\":\"filter\"")
                .contains("\"entries\":");
    }

    @Test
    void whenDeserializingLibraryDocumentation_thenObjectIsRestored() throws Exception {
        val original = createSampleLibraryDocumentation();
        val json     = objectMapper.writeValueAsString(original);

        val restored = objectMapper.readValue(json, LibraryDocumentation.class);

        assertThat(restored.type()).isEqualTo(original.type());
        assertThat(restored.name()).isEqualTo(original.name());
        assertThat(restored.description()).isEqualTo(original.description());
        assertThat(restored.entries()).hasSameSizeAs(original.entries());
    }

    @Test
    void whenSerializingDocumentationBundle_thenJsonIsValid() throws Exception {
        val bundle = new DocumentationBundle(List.of(createSampleLibraryDocumentation()));

        val json = objectMapper.writeValueAsString(bundle);

        assertThat(json).contains("\"libraries\":");
    }

    @Test
    void whenDeserializingDocumentationBundle_thenObjectIsRestored() throws Exception {
        val original = new DocumentationBundle(List.of(createSampleLibraryDocumentation()));
        val json     = objectMapper.writeValueAsString(original);

        val restored = objectMapper.readValue(json, DocumentationBundle.class);

        assertThat(restored.libraries()).hasSize(1);
        assertThat(restored.libraries().getFirst().name()).isEqualTo("filter");
    }

    @Test
    void whenSerializingEntryDocumentation_thenAllFieldsArePresent() throws Exception {
        val entry = new EntryDocumentation(EntryType.FUNCTION, "testFunction", "Test documentation",
                "{\"type\":\"string\"}", List.of(new ParameterDocumentation("param1", List.of("Text"), false, null)));

        val json = objectMapper.writeValueAsString(entry);

        assertThat(json).contains("\"type\":\"FUNCTION\"").contains("\"name\":\"testFunction\"")
                .contains("\"documentation\":\"Test documentation\"")
                .contains("\"schema\":\"{\\\"type\\\":\\\"string\\\"}\"").contains("\"parameters\":");
    }

    @Test
    void whenSerializingParameterDocumentation_thenAllFieldsArePresent() throws Exception {
        val param = new ParameterDocumentation("testParam", List.of("Text", "Number"), true, "{\"type\":\"string\"}");

        val json = objectMapper.writeValueAsString(param);

        assertThat(json).contains("\"name\":\"testParam\"").contains("\"allowedTypes\":[\"Text\",\"Number\"]")
                .contains("\"varArgs\":true").contains("\"schema\":\"{\\\"type\\\":\\\"string\\\"}\"");
    }

    private static LibraryDocumentation createSampleLibraryDocumentation() {
        val blackenParams = List.of(new ParameterDocumentation("text", List.of("Text"), false, null),
                new ParameterDocumentation("replacement", List.of("Text"), false, null));
        val blackenEntry  = new EntryDocumentation(EntryType.FUNCTION, "blacken",
                "Blacken text by replacing characters", null, blackenParams);

        return new LibraryDocumentation(LibraryType.FUNCTION_LIBRARY, "filter", "Functions for filtering data",
                "Detailed documentation here", List.of(blackenEntry));
    }

}

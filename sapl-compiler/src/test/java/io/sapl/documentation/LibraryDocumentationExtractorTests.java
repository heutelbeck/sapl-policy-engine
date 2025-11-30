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
package io.sapl.documentation;

import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryType;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryDocumentationExtractorTests {

    @Test
    void whenExtractingFunctionLibrary_thenLibraryMetadataIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(FilterFunctionLibrary.class);

        assertThat(documentation.type()).isEqualTo(LibraryType.FUNCTION_LIBRARY);
        assertThat(documentation.name()).isEqualTo("filter");
        assertThat(documentation.description()).isEqualTo(FilterFunctionLibrary.DESCRIPTION);
    }

    @Test
    void whenExtractingFunctionLibrary_thenFunctionEntriesAreExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(FilterFunctionLibrary.class);

        assertThat(documentation.entries()).isNotEmpty();

        val blackenEntry = documentation.findEntry("blacken");
        assertThat(blackenEntry).isNotNull();
        assertThat(blackenEntry.type()).isEqualTo(EntryType.FUNCTION);
        assertThat(blackenEntry.documentation())
                .isEqualTo("Blacken text by replacing characters with a replacement string");
    }

    @Test
    void whenExtractingClassWithoutAnnotation_thenExceptionIsThrown() {
        assertThatThrownBy(() -> LibraryDocumentationExtractor.extractFunctionLibrary(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not annotated with @FunctionLibrary");
    }

    @Test
    void whenExtractingPipWithoutAnnotation_thenExceptionIsThrown() {
        assertThatThrownBy(() -> LibraryDocumentationExtractor.extractPolicyInformationPoint(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not annotated with @PolicyInformationPoint");
    }

    @Test
    void whenExtractingFunctionWithTypedParameters_thenTypeConstraintsAreExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("typedFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(2);

        val firstParam = entry.parameters().getFirst();
        assertThat(firstParam.name()).isEqualTo("textParam");
        assertThat(firstParam.allowedTypes()).containsExactly("Text");

        val secondParam = entry.parameters().get(1);
        assertThat(secondParam.name()).isEqualTo("numberParam");
        assertThat(secondParam.allowedTypes()).containsExactly("Number");
    }

    @Test
    void whenExtractingFunctionWithVarArgs_thenVarArgsIsDetected() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(VarArgsLibrary.class);
        val entry         = documentation.findEntry("varArgsFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().varArgs()).isTrue();
    }

    @Test
    void whenExtractingPolicyInformationPoint_thenPipMetadataIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);

        assertThat(documentation.type()).isEqualTo(LibraryType.POLICY_INFORMATION_POINT);
        assertThat(documentation.name()).isEqualTo("testPip");
        assertThat(documentation.description()).isEqualTo("A test PIP for documentation extraction");
    }

    @Test
    void whenExtractingEnvironmentAttribute_thenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("envAttribute");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ENVIRONMENT_ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("An environment attribute for testing");
    }

    @Test
    void whenExtractingEntityAttribute_thenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("entityAttribute");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("An entity attribute for testing");
    }

    @Test
    void whenExtractingAttributeWithParameters_thenParametersExcludeEntityAndVariables() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("attributeWithParams");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("additionalParam");
    }

    @Test
    void whenGeneratingCodeTemplate_thenTemplateIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("typedFunction");

        assertThat(entry.codeTemplate("typed")).isEqualTo("typed.typedFunction(textParam, numberParam)");
    }

    @Test
    void whenGeneratingAttributeCodeTemplate_thenTemplateHasAngleBrackets() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("envAttribute");

        assertThat(entry.codeTemplate("testPip")).isEqualTo("<testPip.envAttribute>");
    }

    @Test
    void whenExtractingLibraryWithDefaultName_thenClassNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(DefaultNameLibrary.class);

        assertThat(documentation.name()).isEqualTo("DefaultNameLibrary");
    }

    @Test
    void whenExtractingFunctionWithDefaultName_thenMethodNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(DefaultNameLibrary.class);
        val entry         = documentation.findEntry("defaultNameFunction");

        assertThat(entry).isNotNull();
    }

    @Test
    void whenExtractingFunctionWithSchema_thenSchemaIsIncluded() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(SchemaLibrary.class);
        val entry         = documentation.findEntry("schemaFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.schema()).isEqualTo("{\"type\":\"string\"}");
    }

    // Test fixtures

    @FunctionLibrary(name = "typed", description = "Library with typed parameters")
    static class TypedParameterLibrary {
        @Function(docs = "A function with typed parameters")
        public static Value typedFunction(@Text Value textParam, @Number Value numberParam) {
            return Value.UNDEFINED;
        }
    }

    @FunctionLibrary(name = "varargs", description = "Library with varargs")
    static class VarArgsLibrary {
        @Function(docs = "A function with varargs")
        public static Value varArgsFunction(Value... values) {
            return Value.UNDEFINED;
        }
    }

    @PolicyInformationPoint(name = "testPip", description = "A test PIP for documentation extraction")
    static class TestPip {
        @EnvironmentAttribute(docs = "An environment attribute for testing")
        public Flux<Value> envAttribute() {
            return Flux.just(Value.UNDEFINED);
        }

        @Attribute(docs = "An entity attribute for testing")
        public Flux<Value> entityAttribute(Value entity) {
            return Flux.just(Value.UNDEFINED);
        }

        @Attribute(docs = "An attribute with additional parameters")
        public Flux<Value> attributeWithParams(Value entity, Map<String, Value> variables, Value additionalParam) {
            return Flux.just(Value.UNDEFINED);
        }
    }

    @FunctionLibrary(description = "Library without explicit name")
    static class DefaultNameLibrary {
        @Function
        public static Value defaultNameFunction() {
            return Value.UNDEFINED;
        }
    }

    @FunctionLibrary(name = "schema", description = "Library with schema")
    static class SchemaLibrary {
        @Function(docs = "Function with schema", schema = "{\"type\":\"string\"}")
        public static Value schemaFunction() {
            return Value.UNDEFINED;
        }
    }

}

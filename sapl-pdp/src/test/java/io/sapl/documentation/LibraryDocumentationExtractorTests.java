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
package io.sapl.documentation;

import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.documentation.LibraryType;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("LibraryDocumentationExtractor")
class LibraryDocumentationExtractorTests {

    @Test
    void whenExtractingFunctionLibraryThenLibraryMetadataIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(FilterFunctionLibrary.class);

        assertThat(documentation.type()).isEqualTo(LibraryType.FUNCTION_LIBRARY);
        assertThat(documentation.name()).isEqualTo("filter");
        assertThat(documentation.description()).isEqualTo(FilterFunctionLibrary.DESCRIPTION);
    }

    @Test
    void whenExtractingFunctionLibraryThenFunctionEntriesAreExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(FilterFunctionLibrary.class);

        assertThat(documentation.entries()).isNotEmpty();

        val blackenEntry = documentation.findEntry("blacken");
        assertThat(blackenEntry).isNotNull();
        assertThat(blackenEntry.type()).isEqualTo(EntryType.FUNCTION);
        assertThat(blackenEntry.documentation())
                .isEqualTo("Blacken text by replacing characters with a replacement string");
    }

    @Test
    void whenExtractingClassWithoutAnnotationThenExceptionIsThrown() {
        assertThatThrownBy(() -> LibraryDocumentationExtractor.extractFunctionLibrary(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not annotated with @FunctionLibrary");
    }

    @Test
    void whenExtractingPipWithoutAnnotationThenExceptionIsThrown() {
        assertThatThrownBy(() -> LibraryDocumentationExtractor.extractPolicyInformationPoint(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not annotated with @PolicyInformationPoint");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameterTypeDerivationCases")
    void whenExtractingFunctionParameterThenTypeIsDerivedFromValueClass(String functionName, String expectedType) {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry(functionName);

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly(expectedType);
    }

    static Stream<Arguments> parameterTypeDerivationCases() {
        return Stream.of(arguments("textFunction", "Text"), arguments("numberFunction", "Number"),
                arguments("boolFunction", "Bool"), arguments("arrayFunction", "Array"),
                arguments("objectFunction", "Object"), arguments("genericFunction", "Value"));
    }

    @Test
    void whenExtractingFunctionWithNamedParameterThenParameterNameIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry("textFunction");

        assertThat(entry.parameters().getFirst().name()).isEqualTo("incantation");
    }

    @Test
    void whenExtractingFunctionWithMixedParametersThenEachTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry("mixedFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(3);
        assertThat(entry.parameters().get(0).allowedTypes()).containsExactly("Text");
        assertThat(entry.parameters().get(1).allowedTypes()).containsExactly("Number");
        assertThat(entry.parameters().get(2).allowedTypes()).containsExactly("Bool");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("varArgsCases")
    void whenExtractingFunctionWithVarArgsThenVarArgsIsDetectedAndTypeIsDerived(String functionName,
            String expectedType) {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry(functionName);

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().varArgs()).isTrue();
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly(expectedType);
    }

    static Stream<Arguments> varArgsCases() {
        return Stream.of(arguments("typedVarArgs", "Text"), arguments("genericVarArgs", "Value"));
    }

    @Test
    void whenExtractingPolicyInformationPointThenPipMetadataIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);

        assertThat(documentation.type()).isEqualTo(LibraryType.POLICY_INFORMATION_POINT);
        assertThat(documentation.name()).isEqualTo("cultist");
        assertThat(documentation.description()).isEqualTo("Provides cultist-related attributes");
    }

    @Test
    void whenExtractingEnvironmentAttributeThenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("moonPhase");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ENVIRONMENT_ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("Current phase of the moon");
    }

    @Test
    void whenExtractingEntityAttributeThenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("madnessLevel");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("Retrieves the madness level of an entity");
    }

    @Test
    void whenExtractingAttributeWithParametersThenParametersExcludeEntityAndVariables() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("forbiddenName");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("realm");
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Text");
    }

    @Test
    void whenExtractingEnvironmentAttributeWithParametersThenParametersExcludeVariables() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("eldritchHour");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("timezone");
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Number");
    }

    @Test
    void whenGeneratingFunctionCodeTemplateThenTemplateIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry("mixedFunction");

        assertThat(entry.codeTemplate("test")).isEqualTo("test.mixedFunction(name, power, isActive)");
    }

    @Test
    void whenGeneratingAttributeCodeTemplateThenTemplateHasAngleBrackets() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("moonPhase");

        assertThat(entry.codeTemplate("cultist")).isEqualTo("<cultist.moonPhase>");
    }

    @Test
    void whenExtractingLibraryWithDefaultNameThenClassNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(DefaultNameLibrary.class);

        assertThat(documentation.name()).isEqualTo("DefaultNameLibrary");
    }

    @Test
    void whenExtractingFunctionWithDefaultNameThenMethodNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(DefaultNameLibrary.class);

        assertThat(documentation.findEntry("defaultNameFunction")).isNotNull();
    }

    @Test
    void whenExtractingFunctionWithCustomNameThenCustomNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);

        assertThat(documentation.findEntry("chant")).isNotNull();
    }

    @Test
    void whenExtractingFunctionWithSchemaThenSchemaIsIncluded() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TestFunctionLibrary.class);
        val entry         = documentation.findEntry("schemaFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.schema()).isEqualTo("{\"type\":\"string\"}");
    }

    // Test fixtures

    @FunctionLibrary(name = "test", description = "Test function library")
    static class TestFunctionLibrary {

        @Function(docs = "Text parameter function")
        public static TextValue textFunction(TextValue incantation) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Number parameter function")
        public static NumberValue numberFunction(NumberValue count) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Boolean parameter function")
        public static BooleanValue boolFunction(BooleanValue isOpen) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Array parameter function")
        public static ArrayValue arrayFunction(ArrayValue signs) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Object parameter function")
        public static ObjectValue objectFunction(ObjectValue metadata) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Generic parameter function")
        public static Value genericFunction(Value offering) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Mixed parameter function")
        public static Value mixedFunction(TextValue name, NumberValue power, BooleanValue isActive) {
            throw new UnsupportedOperationException();
        }

        @Function(name = "chant", docs = "Custom named function")
        public static TextValue ritualChant(TextValue... components) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Typed varargs function")
        public static TextValue typedVarArgs(TextValue... incantations) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Generic varargs function")
        public static Value genericVarArgs(Value... offerings) {
            throw new UnsupportedOperationException();
        }

        @Function(docs = "Function with schema", schema = "{\"type\":\"string\"}")
        public static Value schemaFunction() {
            throw new UnsupportedOperationException();
        }
    }

    @PolicyInformationPoint(name = "cultist", description = "Provides cultist-related attributes")
    static class TestPip {

        @EnvironmentAttribute(docs = "Current phase of the moon")
        public Flux<TextValue> moonPhase(Map<String, Value> variables) {
            return Flux.empty();
        }

        @EnvironmentAttribute(docs = "Eldritch hour in given timezone")
        public Flux<NumberValue> eldritchHour(Map<String, Value> variables, NumberValue timezone) {
            return Flux.empty();
        }

        @Attribute(docs = "Retrieves the madness level of an entity")
        public Flux<NumberValue> madnessLevel(Value entity, Map<String, Value> variables) {
            return Flux.empty();
        }

        @Attribute(docs = "Gets the entity's forbidden name in a realm")
        public Flux<TextValue> forbiddenName(Value entity, Map<String, Value> variables, TextValue realm) {
            return Flux.empty();
        }
    }

    @FunctionLibrary(description = "Library without explicit name")
    static class DefaultNameLibrary {

        @Function
        public static Value defaultNameFunction() {
            throw new UnsupportedOperationException();
        }
    }

}

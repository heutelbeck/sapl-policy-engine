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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

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
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import lombok.val;
import reactor.core.publisher.Flux;

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
    void whenExtractingFunctionWithTextValueParameter_thenTextTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("textFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("incantation");
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Text");
    }

    @Test
    void whenExtractingFunctionWithNumberValueParameter_thenNumberTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("numberFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Number");
    }

    @Test
    void whenExtractingFunctionWithBooleanValueParameter_thenBoolTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("boolFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Bool");
    }

    @Test
    void whenExtractingFunctionWithArrayValueParameter_thenArrayTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("arrayFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Array");
    }

    @Test
    void whenExtractingFunctionWithObjectValueParameter_thenObjectTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("objectFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Object");
    }

    @Test
    void whenExtractingFunctionWithGenericValueParameter_thenValueTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("genericFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Value");
    }

    @Test
    void whenExtractingFunctionWithMixedParameters_thenEachTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("mixedFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(3);
        assertThat(entry.parameters().get(0).allowedTypes()).containsExactly("Text");
        assertThat(entry.parameters().get(1).allowedTypes()).containsExactly("Number");
        assertThat(entry.parameters().get(2).allowedTypes()).containsExactly("Bool");
    }

    @Test
    void whenExtractingFunctionWithVarArgs_thenVarArgsIsDetectedAndTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(VarArgsLibrary.class);
        val entry         = documentation.findEntry("varArgsFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().varArgs()).isTrue();
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Text");
    }

    @Test
    void whenExtractingFunctionWithGenericVarArgs_thenValueTypeIsDerived() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(VarArgsLibrary.class);
        val entry         = documentation.findEntry("genericVarArgsFunction");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().varArgs()).isTrue();
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Value");
    }

    @Test
    void whenExtractingPolicyInformationPoint_thenPipMetadataIsExtracted() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);

        assertThat(documentation.type()).isEqualTo(LibraryType.POLICY_INFORMATION_POINT);
        assertThat(documentation.name()).isEqualTo("cultist");
        assertThat(documentation.description()).isEqualTo("Provides cultist-related attributes");
    }

    @Test
    void whenExtractingEnvironmentAttribute_thenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("moonPhase");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ENVIRONMENT_ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("Current phase of the moon");
    }

    @Test
    void whenExtractingEntityAttribute_thenEntryTypeIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("madnessLevel");

        assertThat(entry).isNotNull();
        assertThat(entry.type()).isEqualTo(EntryType.ATTRIBUTE);
        assertThat(entry.documentation()).isEqualTo("Retrieves the madness level of an entity");
    }

    @Test
    void whenExtractingAttributeWithParameters_thenParametersExcludeEntityAndVariables() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("forbiddenName");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("realm");
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Text");
    }

    @Test
    void whenExtractingEnvironmentAttributeWithParameters_thenParametersExcludeVariables() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("eldritchHour");

        assertThat(entry).isNotNull();
        assertThat(entry.parameters()).hasSize(1);
        assertThat(entry.parameters().getFirst().name()).isEqualTo("timezone");
        assertThat(entry.parameters().getFirst().allowedTypes()).containsExactly("Number");
    }

    @Test
    void whenGeneratingCodeTemplate_thenTemplateIsCorrect() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("mixedFunction");

        assertThat(entry.codeTemplate("typed")).isEqualTo("typed.mixedFunction(name, power, isActive)");
    }

    @Test
    void whenGeneratingAttributeCodeTemplate_thenTemplateHasAngleBrackets() {
        val documentation = LibraryDocumentationExtractor.extractPolicyInformationPoint(TestPip.class);
        val entry         = documentation.findEntry("moonPhase");

        assertThat(entry.codeTemplate("cultist")).isEqualTo("<cultist.moonPhase>");
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
    void whenExtractingFunctionWithCustomName_thenCustomNameIsUsed() {
        val documentation = LibraryDocumentationExtractor.extractFunctionLibrary(TypedParameterLibrary.class);
        val entry         = documentation.findEntry("chant");

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

        @Function(docs = "Summons text from the void")
        public static TextValue textFunction(TextValue incantation) {
            return incantation;
        }

        @Function(docs = "Calculates tentacle count")
        public static NumberValue numberFunction(NumberValue count) {
            return count;
        }

        @Function(docs = "Checks portal status")
        public static BooleanValue boolFunction(BooleanValue isOpen) {
            return isOpen;
        }

        @Function(docs = "Lists elder signs")
        public static ArrayValue arrayFunction(ArrayValue signs) {
            return signs;
        }

        @Function(docs = "Retrieves tome metadata")
        public static ObjectValue objectFunction(ObjectValue metadata) {
            return metadata;
        }

        @Function(docs = "Accepts any offering")
        public static Value genericFunction(Value offering) {
            return offering;
        }

        @Function(docs = "Combines multiple elements")
        public static Value mixedFunction(TextValue name, NumberValue power, BooleanValue isActive) {
            return name;
        }

        @Function(name = "chant", docs = "Performs ritual chant")
        public static TextValue ritualChant(TextValue... components) {
            return components[0];
        }
    }

    @FunctionLibrary(name = "varargs", description = "Library with varargs")
    static class VarArgsLibrary {

        @Function(docs = "A function with typed varargs")
        public static TextValue varArgsFunction(TextValue... incantations) {
            return incantations[0];
        }

        @Function(docs = "A function with generic varargs")
        public static Value genericVarArgsFunction(Value... offerings) {
            return offerings[0];
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

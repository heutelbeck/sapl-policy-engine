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
package io.sapl.interpreter.functions;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static io.sapl.hamcrest.Matchers.valError;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import lombok.NoArgsConstructor;

class AnnotationFunctionContextTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void failToInitializeNonFunctionLibraryAnnotatedClass() {
        assertThrows(InitializationException.class, () -> new AnnotationFunctionContext(() -> List.of(""), List::of));
    }

    @Test
    void givenLibraryWithNoExplicitNameInAnnotationWhenDocumentationIsReadThenReturnsClassName()
            throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new FunctionLibraryWithoutName()), List::of);
        assertThat(context.getDocumentation(), contains(pojo(LibraryDocumentation.class).withProperty("name",
                is(FunctionLibraryWithoutName.class.getSimpleName()))));
    }

    @Test
    void givenStaticLibraryThenContainsFunctions() throws InitializationException {
        final var context = new AnnotationFunctionContext(List::of, () -> List.of(LoggingFunctionLibrary.class));
        assertThat(context.getDocumentation(),
                contains(pojo(LibraryDocumentation.class).withProperty("name", is("log"))));
    }

    @Test
    void givenMockLibraryWhenListingFunctionsTheMockFunctionIsAvailable() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.providedFunctionsOfLibrary(MockLibrary.LIBRARY_NAME), hasItems(MockLibrary.FUNCTION_NAME));
    }

    @Test
    void givenNoLibrariesWhenListingFunctionForALibraryCollectionIsEmpty() {
        assertThat(new AnnotationFunctionContext().providedFunctionsOfLibrary("unknown"), empty());
    }

    @Test
    void simpleFunctionCallNoParameters() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.evaluate(null, MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME),
                is(MockLibrary.RETURN_VALUE));
    }

    @Test
    void simpleFunctionCallWithParameters() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.evaluate(null, MockLibrary.LIBRARY_NAME + ".helloTwoArgs", Val.TRUE, Val.FALSE),
                is(MockLibrary.RETURN_VALUE));
    }

    @Test
    void simpleFunctionCallWithVarArgsParameters() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(
                context.evaluate(null, MockLibrary.LIBRARY_NAME + ".helloVarArgs", Val.TRUE, Val.FALSE, Val.UNDEFINED),
                is(MockLibrary.RETURN_VALUE));
    }

    @Test
    void validationForFixedParametersFailsOnWrongInput() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new ValidationLibrary()), List::of);
        assertThat(context.evaluate(null, "validate.fixed", Val.of(0)), valError());
    }

    @Test
    void validationForVarArgsParametersFailsOnWrongInput() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new ValidationLibrary()), List::of);
        assertThat(context.evaluate(null, "validate.varArgs", Val.of(""), Val.of(1)), valError());
    }

    @Test
    void callingFunctionReturningExceptionReturnsError() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.evaluate(null, MockLibrary.LIBRARY_NAME + ".helloFailure", Val.TRUE, Val.TRUE, Val.TRUE),
                valError());
    }

    @Test
    void simpleFunctionCallNoParametersBadParameterNumberReturnsError() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.evaluate(null, MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME, Val.TRUE),
                valError());
    }

    @Test
    void loadedFunctionShouldBeProvided() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertAll(
                () -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME),
                        is(Boolean.TRUE)),
                () -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + ".helloTwoArgs"),
                        is(Boolean.TRUE)),
                () -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + ".helloVarArgs"),
                        is(Boolean.TRUE)));
    }

    @Test
    void libsTest() {
        final var context = new AnnotationFunctionContext();
        assertThat(context.evaluate(null, "i.am.not.a.function"), valError());
    }

    @Test
    void failToInitializeWithBadParametersInLibrary() {
        assertThrows(InitializationException.class,
                () -> new AnnotationFunctionContext(() -> List.of(new BadParameterTypeFunctionLibrary()), List::of));
    }

    @Test
    void failToInitializeWithBadParametersInLibraryVarArgs() {
        assertThrows(InitializationException.class,
                () -> new AnnotationFunctionContext(() -> List.of(new BadParameterTypeFunctionLibraryVarArgs()),
                        List::of));
    }

    @Test
    void failToInitializeWithBadReturnTypeInLibrary() {
        assertThrows(InitializationException.class,
                () -> new AnnotationFunctionContext(() -> List.of(new BadReturnTypeFunctionLibrary()), List::of));
    }

    @Test
    void failToInitializeWithMultipleSchemasInLibrary() {
        assertThrows(InitializationException.class,
                () -> new AnnotationFunctionContext(() -> List.of(new InvalidAnnotationLibrary()), List::of));
    }

    @Test
    void loadedLibrariesShouldBeReturned() throws InitializationException {
        final var context = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        assertThat(context.getAvailableLibraries().contains(MockLibrary.LIBRARY_NAME), is(Boolean.TRUE));
    }

    @Test
    void loadedLibrariesReturnEmptyListWhenNotLoaded() {
        final var context = new AnnotationFunctionContext();
        assertThat(context.getAvailableLibraries().size(), is(0));
    }

    @Test
    void codeTemplatesAreGenerated() throws InitializationException {
        @FunctionLibrary(name = "test", description = "library docs")
        class TestLib {

            @Function(docs = "doc1")
            public Val hello() {
                return Val.TRUE;
            }

            @Function(docs = "doc2")
            public Val helloVarArgs(Val... aVarArgs) {
                return Val.TRUE;
            }

            @Function(docs = "doc3")
            public Val helloTwoArgs(Val arg1, Val arg2) {
                return Val.TRUE;
            }

            @Function(docs = "doc4")
            public Val helloThreeArgs(Val arg1, Val arg2, Val arg3) {
                throw new PolicyEvaluationException();
            }

        }
        final var context              = new AnnotationFunctionContext(() -> List.of(new TestLib()), List::of);
        final var actualFullyQualified = context.getAllFullyQualifiedFunctions();
        assertThat(actualFullyQualified,
                containsInAnyOrder("test.helloThreeArgs", "test.helloVarArgs", "test.helloTwoArgs", "test.hello"));

        final var simpleTemplates = context.getCodeTemplates();
        assertThat(simpleTemplates, containsInAnyOrder("test.hello()", "test.helloThreeArgs(arg1, arg2, arg3)",
                "test.helloTwoArgs(arg1, arg2)", "test.helloVarArgs(aVarArgs...)"));
        final var actualTemplates = context.getDocumentedCodeTemplates();
        assertThat(actualTemplates.values(),
                containsInAnyOrder("library docs", "doc1", "doc1", "doc2", "doc2", "doc3", "doc3", "doc4", "doc4"));
    }

    @Test
    void documentationIsAddedToTheLibrary() throws InitializationException {
        final var context   = new AnnotationFunctionContext(() -> List.of(new MockLibrary()), List::of);
        final var templates = context.getDocumentedCodeTemplates();
        assertThat(templates, hasEntry(MockLibrary.LIBRARY_NAME, MockLibrary.LIBRARY_DOC));
    }

    @FunctionLibrary(name = "validate")
    public static class ValidationLibrary {

        @Function
        public static Val fixed(@Text Val arg) {
            return Val.UNDEFINED;
        }

        @Function
        public static Val varArgs(@Text Val... args) {
            return Val.UNDEFINED;
        }

    }

    @Test
    void schemaIsReturned() throws InitializationException, JsonProcessingException {
        final JsonNode personSchema = MAPPER.readValue("""
                {  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://example.com/schemas/regions",
                  "type": "object",
                  "properties": {
                  "name": { "type": "string" }
                  }
                }""", JsonNode.class);

        final var context         = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var functionSchemas = context.getFunctionSchemas();
        assertThat(functionSchemas, hasEntry("annotation.schemaFromJson", personSchema));
    }

    @Test
    void badParameterSchemaDetected() throws InitializationException {
        final var context = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        assertThat(context.evaluate(null, "annotation.schemaFromBadJson", Val.of("123")), valError());
    }

    @Test
    void schemaIsNotReturned() throws InitializationException {
        final var context         = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var functionSchemas = context.getFunctionSchemas();
        assertThat(functionSchemas, not(hasEntry("annotation.schemaFromJson", "{}")));
    }

    @Test
    void typeAnnotationsWithoutSchema() throws InitializationException {
        final var context   = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var parameter = true;
        assertThat(context.evaluate(null, "annotation.noSchemaWithMultipleParameterAnnotations", Val.of(parameter)),
                is(Val.of(true)));
    }

    @Test
    void typeAnnotationSchemaDoesNotMatchParameter() throws InitializationException, JsonProcessingException {
        final var context   = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var mapper    = new ObjectMapper();
        final var parameter = mapper.readTree("{\"name\": 23}");
        assertThat(context.evaluate(null, "annotation.schemaInParameterAnnotation", Val.of(parameter)), valError());
    }

    @Test
    void typeAnnotationBoolAsJsonSchemaDoesNotMatchParameter() throws InitializationException, JsonProcessingException {
        final var context   = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var mapper    = new ObjectMapper();
        final var parameter = mapper.readTree("{\"name\": 23}");
        assertThat(context.evaluate(null, "annotation.boolAnnotatedParameter", Val.of(parameter)), valError());
    }

    @Test
    void typeAnnotationJsonValueSchemaMatchesParameter() throws InitializationException {
        final var context = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        assertThat(context.evaluate(null, "annotation.jsonValueSchemaInParameterAnnotation", Val.of("test")),
                is(Val.of(true)));
    }

    private static final String[] TEST_CASES = { "annotation.schemaInParameterAnnotation",
            "annotation.emptySchemaInParameterAnnotation",
            "annotation.multipleParameterAnnotationsWithSchemaAtTheFront",
            "annotation.multipleParameterAnnotationsWithSchemaAtTheEnd" };

    static Stream<String> parameterProviderForTypeAnnotationSchemaTests() {
        return Stream.of(TEST_CASES);
    }

    @ParameterizedTest
    @MethodSource("parameterProviderForTypeAnnotationSchemaTests")
    void typeAnnotationSchemaTests(String function) throws InitializationException, JsonProcessingException {
        final var context   = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var mapper    = new ObjectMapper();
        final var parameter = mapper.readTree("{\"name\": \"Joe\"}");
        assertThat(context.evaluate(null, function, Val.of(parameter)), is(Val.of(true)));
    }

    private static final String[] TEST_CASES_PARAM_LOCATION = { "annotation.boolAnnotatedParameter",
            "annotation.multipleParameterAnnotationsWithNonmatchingSchemaAtTheFront",
            "annotation.multipleParameterAnnotationsWithNonmatchingSchemaAtTheEnd" };

    static Stream<String> parameterProviderForParamLocationTests() {
        return Stream.of(TEST_CASES_PARAM_LOCATION);
    }

    @ParameterizedTest
    @MethodSource("parameterProviderForParamLocationTests")
    void paramLocationSchemaTests(String function) throws InitializationException {
        final var context = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        assertThat(context.evaluate(null, function, Val.of(true)), is(Val.of(true)));
    }

    @Test
    void customErrorForSchemaInParameterAnnotation() throws InitializationException, JsonProcessingException {
        final var context   = new AnnotationFunctionContext(
                () -> List.of(new AnnotationFunctionContextTests.AnnotationLibrary()), List::of);
        final var mapper    = new ObjectMapper();
        final var parameter = mapper.readTree("{\"name\": 23}");
        assertThat(context.evaluate(null, "annotation.customErrorForSchemaInParameterAnnotation", Val.of(parameter)),
                valError("Parameter jsonObject needs to comply with the given schema."));
    }

    @Test
    void nonInstanceWithNonStaticMethodFailsLoading() {
        @FunctionLibrary
        class Lib {
            @Function
            public Val helloTest() {
                return Val.of("Hello");
            }
        }
        assertThatThrownBy(() -> new AnnotationFunctionContext(List::of, () -> List.of(Lib.class)))
                .isInstanceOf(InitializationException.class);
    }

    @Test
    void when_nonJsonSchema_then_failLoading() {
        @FunctionLibrary
        class Lib {
            @Function(schema = "][")
            public static Val helloTest() {
                return Val.of("Hello");
            }
        }
        assertThatThrownBy(() -> new AnnotationFunctionContext(List::of, () -> List.of(Lib.class)))
                .isInstanceOf(InitializationException.class);
    }

    @Test
    void when_nonExistingResourceSchema_then_failLoading() {
        @FunctionLibrary
        class Lib {
            @Function(pathToSchema = "/I_do_not_exist.json")
            public static Val helloTest() {
                return Val.of("Hello");
            }
        }
        assertThatThrownBy(() -> new AnnotationFunctionContext(List::of, () -> List.of(Lib.class)))
                .isInstanceOf(InitializationException.class);
    }

    @Test
    void when_existingResourceSchema_then_load() {
        @FunctionLibrary
        class Lib {
            @Function(pathToSchema = "schemas/person_schema.json")
            public static Val helloTest() {
                return Val.of("Hello");
            }
        }
        assertDoesNotThrow(() -> new AnnotationFunctionContext(List::of, () -> List.of(Lib.class)));
    }

    @Test
    void when_badResourceSchema_then_fail() {
        @FunctionLibrary
        class Lib {
            @Function(pathToSchema = "schemas/bad_schema.json")
            public static Val helloTest() {
                return Val.of("Hello");
            }
        }
        assertThatThrownBy(() -> new AnnotationFunctionContext(List::of, () -> List.of(Lib.class)))
                .isInstanceOf(InitializationException.class);
    }

    @Test
    void when_overloading_then_collision() {
        @FunctionLibrary(name = "lib")
        class OverloadingLibrary {
            private OverloadingLibrary() {
            }

            @Function
            public static Val fun(Val param) {
                return param;
            }

            @Function
            public static Val fun(Val param1, Val param2) {
                return param1;
            }

        }
        final var sut = new AnnotationFunctionContext();
        assertThatThrownBy(() -> sut.loadLibrary(OverloadingLibrary.class)).isInstanceOf(InitializationException.class)
                .hasMessage(String.format(AnnotationFunctionContext.FUNCTION_NAME_COLLISION_ERROR, "lib.fun"));
    }

    @FunctionLibrary(name = MockLibrary.LIBRARY_NAME, description = MockLibrary.LIBRARY_DOC)
    public static class MockLibrary {

        public static final String FUNCTION_DOC  = "docs for helloTest";
        public static final String FUNCTION_NAME = "helloTest";
        public static final Val    RETURN_VALUE  = Val.of("HELLO TEST");
        public static final String LIBRARY_NAME  = "test.lib";
        public static final String LIBRARY_DOC   = "docs of my lib";

        @Function(name = FUNCTION_NAME, docs = FUNCTION_DOC)
        public static Val helloTest() {
            return RETURN_VALUE;
        }

        @Function
        public static Val helloVarArgs(Val... args) {
            return RETURN_VALUE;
        }

        @Function
        public static Val helloTwoArgs(Val arg1, Val arg2) {
            return RETURN_VALUE;
        }

        @Function
        public static Val helloFailure(Val arg1, Val arg2, Val arg3) {
            throw new PolicyEvaluationException();
        }

    }

    @FunctionLibrary(name = "annotation")
    public static class AnnotationLibrary {

        static final String PERSON_SCHEMA = """
                {  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://example.com/schemas/regions",
                  "type": "object",
                  "properties": {
                  "name": { "type": "string" }
                  }
                }""";

        @Function(pathToSchema = "schemas/person_schema.json")
        public static Val schemaFromFile() {
            return Val.of(true);
        }

        @Function(schema = PERSON_SCHEMA)
        public static Val schemaFromJson() {
            return Val.of(true);
        }

        @Function()
        public static Val schemaFromBadJson(@Schema("][}{") Val param) {
            return Val.of(true);
        }

        @Function
        public static Val boolAnnotatedParameter(@Schema("{\"type\": \"boolean\"}") Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val noSchemaWithMultipleParameterAnnotations(@JsonObject @Text @Bool Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val schemaInParameterAnnotation(@Schema(value = PERSON_SCHEMA) Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val emptySchemaInParameterAnnotation(@Schema() Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val multipleParameterAnnotationsWithSchemaAtTheFront(
                @Schema(value = PERSON_SCHEMA) @JsonObject Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val multipleParameterAnnotationsWithSchemaAtTheEnd(
                @JsonObject @Schema(value = PERSON_SCHEMA) Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val multipleParameterAnnotationsWithNonmatchingSchemaAtTheFront(
                @Schema(value = PERSON_SCHEMA) @Bool Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val multipleParameterAnnotationsWithNonmatchingSchemaAtTheEnd(
                @Bool @Schema(value = PERSON_SCHEMA) Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val jsonValueSchemaInParameterAnnotation(
                @Schema(value = "{\"type\": \"string\"}") Val jsonObject) {
            return Val.of(true);
        }

        @Function
        public static Val customErrorForSchemaInParameterAnnotation(
                @Schema(value = PERSON_SCHEMA, errorText = "Parameter jsonObject needs to comply with the given schema.") Val jsonObject) {
            return Val.of(true);
        }

    }

    @FunctionLibrary(name = "InvalidAnnotationLibrary")
    public static class InvalidAnnotationLibrary {

        @Function(schema = "{}", pathToSchema = "schemas/person_schema.json")
        public static Val multipleSchemaFunctionAnnotations() {
            return Val.of(true);
        }

    }

    @FunctionLibrary
    @NoArgsConstructor
    public static class FunctionLibraryWithoutName {

    }

    @FunctionLibrary
    @NoArgsConstructor
    public static class BadParameterTypeFunctionLibrary {

        @Function
        public Val fun(String param) {
            return Val.UNDEFINED;
        }

    }

    @FunctionLibrary
    @NoArgsConstructor
    public static class BadParameterTypeFunctionLibraryVarArgs {

        @Function
        public Val fun(String... param) {
            return Val.UNDEFINED;
        }

    }

    @FunctionLibrary
    @NoArgsConstructor
    public static class BadReturnTypeFunctionLibrary {

        @Function
        public String fun(Val param) {
            return param.toString();
        }

    }

}

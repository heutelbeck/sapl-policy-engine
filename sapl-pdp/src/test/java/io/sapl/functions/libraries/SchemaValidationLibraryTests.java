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

import com.github.valfirst.slf4jtest.TestLogger;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.keyword.PatternValidator;
import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.compiler.util.SchemaReferenceFixtures.UNRESOLVABLE_REFERENCE;
import static io.sapl.functions.libraries.SchemaValidationLibrary.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SchemaValidationLibrary")
class SchemaValidationLibraryTests {

    private static final String INVALID_REGULAR_EXPRESSION = "The schema holds an invalid regular expression: %s.";
    private static final String SCHEMA_VALIDATION_FAILED   = "Schema validation failed: %s.";

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new SchemaValidationLibrary())).doesNotThrowAnyException();
    }

    @Test
    @Timeout(15)
    @DisplayName("a schema pattern that backtracking engines take exponential time for answers in linear time")
    void whenSchemaPatternWouldBacktrackCatastrophicallyThenAnswersQuickly() {
        val schema  = ObjectValue.builder().put("type", Value.of("string")).put("pattern", Value.of("(.*a){30}$"))
                .build();
        val hostile = Value.of("a".repeat(29) + "b".repeat(10_000));

        val result = isCompliant(hostile, schema);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("a schema pattern matches any part of the text, and $ only at the end of the text")
    void whenSchemaPatternMatchedThenSemanticsOfJsonSchemaApply(String description, String pattern, String text,
            boolean compliant) {
        val schema = ObjectValue.builder().put("type", Value.of("string")).put("pattern", Value.of(pattern)).build();
        assertThat(isCompliant(Value.of(text), schema)).isEqualTo(Value.of(compliant));
    }

    static Stream<Arguments> whenSchemaPatternMatchedThenSemanticsOfJsonSchemaApply() {
        return Stream.of(arguments("a part of the text", "[0-9]{5}", "zip 12345", true),
                arguments("an anchored pattern", "^[0-9]{5}$", "12345", true),
                arguments("a line feed before the end", "^[0-9]{5}$", "12345\n", false),
                arguments("a long form Unicode property", "^\\p{Letter}+$", "caf\u00e9", true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("a schema with a pattern RE2 refuses is an error in every function, never a compliant subject")
    void whenSchemaPatternRefusedThenEveryFunctionReturnsError(String description, String schema, String subject,
            String reason) {
        val jsonSchema        = (ObjectValue) json(schema);
        val validationSubject = json(subject);

        assertThat(List.of(isCompliant(validationSubject, jsonSchema),
                isCompliantWithExternalSchemas(validationSubject, jsonSchema, Value.EMPTY_ARRAY),
                validate(validationSubject, jsonSchema),
                validateWithExternalSchemas(validationSubject, jsonSchema, Value.EMPTY_ARRAY)))
                .allSatisfy(result -> assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertThat(error.message()).isEqualTo(INVALID_REGULAR_EXPRESSION.formatted(reason))));
    }

    static Stream<Arguments> whenSchemaPatternRefusedThenEveryFunctionReturnsError() {
        return Stream.of(arguments("a look-ahead in pattern", """
                {"type": "string", "pattern": "^(?=.*[0-9]).{8,}$"}
                """, "\"password1\"", "invalid or unsupported Perl syntax"),
                arguments("a backreference in a nested pattern", """
                        {"type": "object", "properties": {"code": {"type": "string", "pattern": "(a)\\\\1"}}}
                        """, "{}", "invalid escape sequence"), arguments("a look-behind in a patternProperties key", """
                        {"type": "object", "patternProperties": {"(?<=x)y": {"type": "number"}}}
                        """, "{\"other\": 1}", "invalid named capture"),
                arguments("a look-ahead in a branch the subject does not need", """
                        {"anyOf": [{"type": "number"}, {"type": "string", "pattern": "(?!admin)"}]}
                        """, "1", "invalid or unsupported Perl syntax"), arguments("a pattern too large to compile", """
                        {"type": "string", "pattern": "(?:a{1000}){1000}"}
                        """, "\"a\"", "expression too large"));
    }

    @Test
    @DisplayName("an external schema with a pattern RE2 refuses is an error, never a compliant subject")
    void whenExternalSchemaPatternRefusedThenError() {
        val externals = (ArrayValue) json("""
                [{"$id": "https://example.com/password", "type": "string", "pattern": "^(?=.*[0-9]).{8,}$"}]
                """);
        val schema    = (ObjectValue) json("""
                {"$ref": "https://example.com/password"}
                """);

        assertThat(isCompliantWithExternalSchemas(Value.of("password1"), schema, externals))
                .isInstanceOfSatisfying(ErrorValue.class, error -> assertThat(error.message())
                        .isEqualTo(INVALID_REGULAR_EXPRESSION.formatted("invalid or unsupported Perl syntax")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("a schema pattern RE2 refuses leaves no trace in the log, as subscriptions may supply schemas")
    void whenSchemaPatternRefusedThenNothingIsLogged(String description, String schema) {
        val jsonSchema    = (ObjectValue) json(schema);
        val networkntLogs = List.of(getTestLogger(PatternValidator.class), getTestLogger(Dialect.class));
        networkntLogs.forEach(TestLogger::clearAll);

        val result = validate(Value.of("text"), jsonSchema);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(networkntLogs).allSatisfy(log -> assertThat(log.getAllLoggingEvents()).isEmpty());
    }

    static Stream<Arguments> whenSchemaPatternRefusedThenNothingIsLogged() {
        return Stream.of(arguments("a look-ahead in pattern", """
                {"type": "string", "pattern": "^(?=.*[0-9]).{8,}$"}
                """), arguments("a look-behind in a patternProperties key", """
                {"type": "object", "patternProperties": {"(?<=x)y": {"type": "number"}}}
                """), arguments("a look-behind in a patternProperties key next to additionalProperties", """
                {"type": "object", "additionalProperties": false, "patternProperties": {"(?<=x)y": {"type": "number"}}}
                """), arguments("a look-ahead in pattern under the draft-07 dialect", """
                {"$schema": "http://json-schema.org/draft-07/schema#", "type": "string", "pattern": "(?!admin)"}
                """));
    }

    @Test
    @DisplayName("a validator failure outside the expected exception types yields an ErrorValue, not an escape")
    void whenValidatorThrowsUnexpectedlyThenErrorValue() {
        // A self-referential $ref drives networknt into a StackOverflowError, which is
        // neither SchemaException nor IllegalArgumentException.
        val selfReferential = ObjectValue.builder().put("$ref", Value.of("#")).build();

        val result = validateWithExternalSchemas(Value.of("x"), selfReferential, Value.EMPTY_ARRAY);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "when a schema names {1} by {0} then every function returns an error")
    @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#foreignReferences")
    @DisplayName("a schema may name only the built-in meta-schemas and the provided schemas, and a refusal leaves no trace in the log")
    void whenSchemaNamesForeignReferenceThenEveryFunctionReturnsErrorAndNothingIsLogged(String keyword,
            String reference) {
        val jsonSchema   = ObjectValue.builder().put(keyword, Value.of(reference)).put("type", Value.of("string"))
                .build();
        val subject      = Value.of("text");
        val networkntLog = getTestLogger(SchemaRegistry.class);
        networkntLog.clearAll();

        val results = List.of(isCompliant(subject, jsonSchema),
                isCompliantWithExternalSchemas(subject, jsonSchema, Value.EMPTY_ARRAY), validate(subject, jsonSchema),
                validateWithExternalSchemas(subject, jsonSchema, Value.EMPTY_ARRAY));

        assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message())
                        .isEqualTo(SCHEMA_VALIDATION_FAILED.formatted(UNRESOLVABLE_REFERENCE.formatted(reference)))));
        assertThat(networkntLog.getAllLoggingEvents()).isEmpty();
    }

    @ParameterizedTest(name = "when a schema names {0} then subjects are validated against it")
    @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#builtInReferences")
    void whenSchemaNamesBuiltInMetaSchemaThenSubjectsAreValidated(String description, String schema, String compliant,
            String nonCompliant) {
        val jsonSchema = (ObjectValue) json(schema);
        assertThat(List.of(isCompliant(json(compliant), jsonSchema), isCompliant(json(nonCompliant), jsonSchema)))
                .containsExactly(Value.TRUE, Value.FALSE);
    }

    @Test
    @DisplayName("an unresolvable $ref makes the schema un-compilable and yields an ErrorValue, not a false validation result")
    void whenSchemaHasUnresolvableRefThenErrorValue() {
        val brokenSchema = ObjectValue.builder().put("$ref", Value.of("#/$defs/missing")).build();

        val result = validateWithExternalSchemas(Value.of("x"), brokenSchema, Value.EMPTY_ARRAY);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenValidatingCompliantValueThenReturnsTrue() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.TRUE, schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    void whenValidatingNonCompliantValueThenReturnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("boolean")).build();

        val result = isCompliant(Value.of(123), schema);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
    }

    @Test
    void whenValidatingUndefinedValueThenReturnsFalse() {
        val schema = ObjectValue.builder().put("type", Value.of("string")).build();

        val result = validate(Value.UNDEFINED, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.FALSE);
        assertThat((ArrayValue) resultObj.get("errors")).isEmpty();
    }

    @Test
    void whenValidatingErrorValueThenPropagatesError() {
        val schema     = ObjectValue.builder().put("type", Value.of("string")).build();
        val errorValue = Value.error("test error");

        val result = validate(errorValue, schema);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenValidateReturnsCompliantThenStructuredResultCorrect() {
        val schema = ObjectValue.builder().put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("name", ObjectValue.builder().put("type", Value.of("string")).build())
                                .put("age", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val subject = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of(25)).build();

        val result = validate(subject, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj).containsKey("valid").containsKey("errors");
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.TRUE);
        assertThat((ArrayValue) resultObj.get("errors")).isEmpty();
    }

    @Test
    void whenValidateReturnsNonCompliantThenStructuredResultWithErrors() {
        val schema = ObjectValue.builder().put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("name", ObjectValue.builder().put("type", Value.of("string")).build())
                                .put("age", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val subject = ObjectValue.builder().put("name", Value.of("Alice")).put("age", Value.of("25")) // Should be
                                                                                                      // integer, not
                                                                                                      // string
                .build();

        val result = validate(subject, schema);

        assertThat(result).isInstanceOf(ObjectValue.class);
        val resultObj = (ObjectValue) result;
        assertThat(resultObj.get("valid")).isNotNull().isEqualTo(Value.FALSE);
        val errors = (ArrayValue) resultObj.get("errors");
        assertThat(errors).isNotEmpty();

        val firstError = (ObjectValue) errors.getFirst();
        assertThat(firstError.containsKey("path")).isTrue();
        assertThat(firstError.containsKey("message")).isTrue();
        assertThat(firstError.containsKey("type")).isTrue();
        assertThat(firstError.containsKey("schemaPath")).isTrue();
    }

    @Test
    void whenValidateWithExternalSchemasThenReferencesAreResolved() {
        val coordinatesSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/coordinates"))
                .put("$schema", Value.of("https://json-schema.org/draft/2020-12/schema"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("x", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("y", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("z", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val externals = ArrayValue.builder().add(coordinatesSchema).build();

        val triangleSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/triangle"))
                .put("type", Value.of("object"))
                .put("properties", ObjectValue.builder()
                        .put("A",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .put("B",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .put("C",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                        .build())
                .build();

        val validTriangle = ObjectValue.builder()
                .put("A",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .put("B",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .put("C",
                        ObjectValue.builder().put("x", Value.of(1)).put("y", Value.of(2)).put("z", Value.of(3)).build())
                .build();

        val result = isCompliantWithExternalSchemas(validTriangle, triangleSchema, externals);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.TRUE);
    }

    @Test
    @Timeout(30)
    @DisplayName("when many array elements each match a backtracking pattern then the aggregate validation is bounded and aborts")
    void whenManyElementsMatchBacktrackingPatternThenValidationAnswersQuickly() {
        // A schema "pattern" keyword is matched once per array element. Each match takes
        // time linear in the length of the element, so the whole validation scales
        // linearly with the element count.
        val itemSchema  = ObjectValue.builder().put("type", Value.of("string")).put("pattern", Value.of("(.*a){30}$"))
                .build();
        val arraySchema = ObjectValue.builder().put("type", Value.of("array")).put("items", itemSchema).build();

        val hostileElement = Value.of("a".repeat(24));
        val arrayBuilder   = ArrayValue.builder();
        for (int i = 0; i < 1_000; i++) {
            arrayBuilder.add(hostileElement);
        }
        val hostileArray = arrayBuilder.build();

        val result = isCompliant(hostileArray, arraySchema);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void whenValidateWithExternalSchemasAndInvalidDataThenReturnsFalse() {
        val coordinatesSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/coordinates"))
                .put("$schema", Value.of("https://json-schema.org/draft/2020-12/schema"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("x", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("y", ObjectValue.builder().put("type", Value.of("integer")).build())
                                .put("z", ObjectValue.builder().put("type", Value.of("integer")).build()).build())
                .build();

        val externals = ArrayValue.builder().add(coordinatesSchema).build();

        val triangleSchema = ObjectValue.builder().put("$id", Value.of("https://example.com/triangle"))
                .put("type", Value.of("object"))
                .put("properties",
                        ObjectValue.builder().put("A",
                                ObjectValue.builder().put("$ref", Value.of("https://example.com/coordinates")).build())
                                .build())
                .build();

        val invalidTriangle = ObjectValue.builder().put("A", ObjectValue.builder().put("x", Value.of("not a number"))
                .put("y", Value.of(2)).put("z", Value.of(3)).build()).build();

        val result = isCompliantWithExternalSchemas(invalidTriangle, triangleSchema, externals);

        assertThat(result).isInstanceOf(BooleanValue.class).isEqualTo(Value.FALSE);
    }
}

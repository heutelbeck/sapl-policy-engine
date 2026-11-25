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
import io.sapl.compiler.util.LinearRegex;
import io.sapl.compiler.util.JsonSchemaPreparation;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private static final String MATCHING_FAILED            = "Matching the regular expression '.*' of the schema failed: text too long.";
    private static final String NAME_TOO_LONG              = "Schema validation failed: property name too long.";
    private static final String TEXT_BEYOND_LIMIT          = "a".repeat(LinearRegex.LIMITS.maxTextBytes() + 1);
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
    @DisplayName("a schema with a pattern the regex engine refuses is an error in every function, never a compliant subject")
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
                """, "\"password1\"", "look-around, including look-ahead and look-behind, is not supported"),
                arguments("a backreference in a nested pattern", """
                        {"type": "object", "properties": {"code": {"type": "string", "pattern": "(a)\\\\1"}}}
                        """, "{}", "backreferences are not supported"),
                arguments("a look-behind in a patternProperties key", """
                        {"type": "object", "patternProperties": {"(?<=x)y": {"type": "number"}}}
                        """, "{\"other\": 1}", "look-around, including look-ahead and look-behind, is not supported"),
                arguments("a look-ahead in a branch the subject does not need", """
                        {"anyOf": [{"type": "number"}, {"type": "string", "pattern": "(?!admin)"}]}
                        """, "1", "look-around, including look-ahead and look-behind, is not supported"),
                arguments("a pattern too large to compile", """
                        {"type": "string", "pattern": "(?:a{1000}){1000}"}
                        """, "\"a\"", "expression too large"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("a subject the regex engine cannot search is an error in every function, never a compliant subject")
    void whenSubjectCannotBeSearchedThenEveryFunctionReturnsError(String description, String schema,
            Value validationSubject, String message) {
        val jsonSchema = (ObjectValue) json(schema);

        assertThat(List.of(isCompliant(validationSubject, jsonSchema),
                isCompliantWithExternalSchemas(validationSubject, jsonSchema, Value.EMPTY_ARRAY),
                validate(validationSubject, jsonSchema),
                validateWithExternalSchemas(validationSubject, jsonSchema, Value.EMPTY_ARRAY)))
                .allSatisfy(result -> assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertThat(error.message()).isEqualTo(message)));
    }

    static Stream<Arguments> whenSubjectCannotBeSearchedThenEveryFunctionReturnsError() {
        return Stream.of(arguments("a negated pattern on a text beyond the text limit", """
                {"not": {"pattern": ".*"}}
                """, Value.of(TEXT_BEYOND_LIMIT), MATCHING_FAILED),
                arguments("a patternProperties key on a name beyond the text limit", """
                        {"type": "object", "patternProperties": {".*": false}}
                        """, ObjectValue.builder().put(TEXT_BEYOND_LIMIT, Value.of(1)).build(), NAME_TOO_LONG),
                arguments("a pattern behind a keyword that already failed", """
                        {"type": "number", "pattern": ".*"}
                        """, Value.of(TEXT_BEYOND_LIMIT), MATCHING_FAILED),
                arguments("a pattern behind a subschema that already failed", """
                        {"allOf": [{"type": "number"}, {"pattern": ".*"}]}
                        """, Value.of(TEXT_BEYOND_LIMIT), MATCHING_FAILED),
                arguments("a name beyond the text limit that no pattern of the schema reaches", """
                        {"anyOf": [{"type": "array"}, {"items": {"patternProperties": {".*": false}}}]}
                        """,
                        ArrayValue.builder().add(ObjectValue.builder().put(TEXT_BEYOND_LIMIT, Value.of(1)).build())
                                .build(),
                        NAME_TOO_LONG),
                arguments("a name beyond the text limit under a schema without patterns", """
                        {"type": "object"}
                        """, ObjectValue.builder().put(TEXT_BEYOND_LIMIT, Value.of(1)).build(), NAME_TOO_LONG));
    }

    @Test
    @DisplayName("a subject the regex engine cannot search leaves no trace in the log")
    void whenSubjectCannotBeSearchedThenNothingIsLogged() {
        val networkntLogs = List.of(getTestLogger(PatternValidator.class), getTestLogger(Dialect.class));
        networkntLogs.forEach(TestLogger::clearAll);

        val result = validate(Value.of(TEXT_BEYOND_LIMIT), (ObjectValue) json("""
                {"not": {"pattern": ".*"}}
                """));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(networkntLogs).allSatisfy(log -> assertThat(log.getAllLoggingEvents()).isEmpty());
    }

    @Test
    @DisplayName("an external schema with a pattern the regex engine refuses is an error, never a compliant subject")
    void whenExternalSchemaPatternRefusedThenError() {
        val externals = (ArrayValue) json("""
                [{"$id": "https://example.com/password", "type": "string", "pattern": "^(?=.*[0-9]).{8,}$"}]
                """);
        val schema    = (ObjectValue) json("""
                {"$ref": "https://example.com/password"}
                """);

        assertThat(isCompliantWithExternalSchemas(Value.of("password1"), schema, externals)).isInstanceOfSatisfying(
                ErrorValue.class, error -> assertThat(error.message()).isEqualTo(INVALID_REGULAR_EXPRESSION
                        .formatted("look-around, including look-ahead and look-behind, is not supported")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("a schema pattern the regex engine refuses leaves no trace in the log, as subscriptions may supply schemas")
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

    @ParameterizedTest(name = "the pattern {0} under the keyword {3}")
    @MethodSource
    @DisplayName("a pattern that only a reference reaches is read as written, whatever it looks like")
    void whenPatternStandsWhereOnlyReferenceReachesItThenItIsReadAsWritten(String pattern, String matching,
            String other, String place) {
        val code       = ObjectValue.builder().put("pattern", Value.of(pattern)).build();
        val jsonSchema = ObjectValue.builder().put("allOf",
                ArrayValue.builder().add(ObjectValue.builder().put("$ref", Value.of("#/" + place + "/code")).build())
                        .add(ObjectValue.builder().put("$ref", Value.of("#/definitions/code")).build()).build())
                .put(place, ObjectValue.builder().put("code", code).build())
                .put("definitions", ObjectValue.builder().put("code", code).build()).build();

        assertThat(List.of(isCompliant(Value.of(matching), jsonSchema), isCompliant(Value.of(other), jsonSchema)))
                .containsExactly(Value.TRUE, Value.FALSE);
    }

    static Stream<Arguments> whenPatternStandsWhereOnlyReferenceReachesItThenItIsReadAsWritten() {
        return Stream.of("elsewhere", "default")
                .flatMap(place -> Stream.of(arguments("\\u{5C}\\u{64}", "\\d", "5", place),
                        arguments("sapl0\\u{5C}\\u{64}", "sapl0\\d", "5", place),
                        arguments("sapl0\\u{61}", "sapl0a", "a", place),
                        arguments("sapl1\\u{61}", "sapl1a", "a", place), arguments("^\\d$", "5", "\\d", place)));
    }

    @Test
    @DisplayName("a host-relative reference is refused without loading its resource")
    void whenReferenceHasHostRelativeFormThenSchemaLoadingFails() {
        val schema = (ObjectValue) json("""
                {"$ref":"//127.0.0.1:9/schema"}
                """);
        assertThat(isCompliant(Value.of("text"), schema)).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).isEqualTo(SCHEMA_VALIDATION_FAILED
                        .formatted(UNRESOLVABLE_REFERENCE.formatted("mem://inline//127.0.0.1:9/schema"))));
    }

    @ParameterizedTest(name = "when a schema names a foreign schema by {0} then every function returns an error")
    @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#foreignReferenceSchemas")
    @DisplayName("a schema is refused for a foreign reference wherever and however it names it, and nothing is loaded")
    void whenSchemaNamesForeignReferenceIndirectlyThenEveryFunctionReturnsError(String description, String schema,
            String externalSchemas, String reference) {
        val jsonSchema = (ObjectValue) json(schema);
        val externals  = json(externalSchemas);
        val subject    = Value.of("text");

        assertThat(List.of(isCompliantWithExternalSchemas(subject, jsonSchema, externals),
                validateWithExternalSchemas(subject, jsonSchema, externals)))
                .allSatisfy(result -> assertThat(result).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertThat(error.message()).isEqualTo(
                                SCHEMA_VALIDATION_FAILED.formatted(UNRESOLVABLE_REFERENCE.formatted(reference)))));
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

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("numeric schema constraints compare exact quantities in subjects and schemas")
    void whenNumbersAreValidatedThenExactQuantitiesDetermineCompliance(String description, Value subject,
            ObjectValue schema, boolean compliant) {
        val external  = ObjectValue.builder().putAll(schema).put("$id", Value.of("https://example.com/exact-number"))
                .build();
        val externals = Value.ofArray(external);
        val reference = ObjectValue.builder().put("$ref", Value.of("https://example.com/exact-number")).build();

        assertThat(List.of(isCompliant(subject, schema), isCompliantWithExternalSchemas(subject, reference, externals)))
                .containsOnly(Value.of(compliant));
        assertThat(List.of(validate(subject, schema), validateWithExternalSchemas(subject, reference, externals)))
                .allSatisfy(result -> assertThat(result).isInstanceOfSatisfying(ObjectValue.class,
                        object -> assertThat(object).containsEntry("valid", Value.of(compliant)).hasEntrySatisfying(
                                "errors", errors -> assertThat(errors).isInstanceOfSatisfying(ArrayValue.class,
                                        entries -> assertThat(entries.isEmpty()).isEqualTo(compliant)))));
    }

    static Stream<Arguments> whenNumbersAreValidatedThenExactQuantitiesDetermineCompliance() {
        return Stream.of(
                numericConstraint("a decimal above a maximum", "0.10000000000000000001", "maximum", "0.1", false),
                numericConstraint("a decimal at a maximum", "0.10000000000000000001", "maximum",
                        "0.10000000000000000001", true),
                numericConstraint("a precise maximum below an integer", "1", "maximum", "0.99999999999999999", false),
                numericConstraint("a decimal below a minimum", "0.09999999999999999999", "minimum", "0.1", false),
                numericConstraint(
                        "a precise minimum above a decimal", "0.1", "minimum", "0.10000000000000000001", false),
                numericConstraint("a decimal at a minimum", "0.1", "minimum", "0.10", true),
                numericConstraint("an integer below an exclusive maximum", "1", "exclusiveMaximum",
                        "1.0000000000000000001", true),
                numericConstraint("a decimal at an exclusive maximum", "1.0000000000000000001", "exclusiveMaximum",
                        "1.0000000000000000001", false),
                numericConstraint("a decimal above an exclusive maximum", "1.0000000000000000002", "exclusiveMaximum",
                        "1.0000000000000000001", false),
                numericConstraint("an integer above an exclusive minimum", "1", "exclusiveMinimum",
                        "0.99999999999999999", true),
                numericConstraint("a decimal at an exclusive minimum", "0.99999999999999999", "exclusiveMinimum",
                        "0.99999999999999999", false),
                numericConstraint("a decimal below an exclusive minimum", "0.99999999999999998", "exclusiveMinimum",
                        "0.99999999999999999", false),
                numericConstraint("a decimal different from an integer const", "1.0000000000000000001", "const", "1",
                        false),
                numericConstraint("an integer different from a decimal const", "1", "const", "1.0000000000000000001",
                        false),
                numericConstraint("equal decimals with different scales", "1.2300", "const", "123e-2", true),
                numericConstraint("adjacent integers beyond u64", "18446744073709551617", "const",
                        "18446744073709551616", false),
                numericConstraint("the integer above u64 maximum", "18446744073709551616", "const",
                        "18446744073709551615", false),
                numericConstraint("the integer above i64 maximum", "9223372036854775808", "const",
                        "9223372036854775807", false),
                numericConstraint("the integer below i64 minimum", "-9223372036854775809", "const",
                        "-9223372036854775808", false),
                numericConstraint("adjacent negative integers beyond i64", "-9223372036854775810", "maximum",
                        "-9223372036854775809", true),
                numericConstraint("a negative integer above a maximum beyond i64", "-9223372036854775809", "maximum",
                        "-9223372036854775810", false),
                numericConstraint("adjacent integers beyond the f64 exact range", "9007199254740993", "const",
                        "9007199254740992", false),
                numericConstraint("adjacent negative integers beyond the f64 exact range", "-9007199254740993", "const",
                        "-9007199254740992", false),
                numericConstraint("an integer above a maximum beyond u64", "18446744073709551617", "maximum",
                        "18446744073709551616", false),
                numericConstraint("an integer below a minimum beyond u64", "18446744073709551616", "minimum",
                        "18446744073709551617", false),
                numericConstraint("an exact decimal multiple", "0.3", "multipleOf", "0.1", true),
                numericConstraint("an inexact decimal multiple", "1.0000000000000000001", "multipleOf", "0.1", false),
                numericConstraint("a precise divisor different from one tenth", "1", "multipleOf",
                        "0.10000000000000000001", false),
                numericConstraint("an odd integer beyond u64", "18446744073709551617", "multipleOf", "2", false),
                numericConstraint("an even integer beyond u64", "18446744073709551616", "multipleOf", "2", true),
                numericConstraint("a positive number at the smallest allowed scale", "1e-1000", "maximum", "0", false),
                numericConstraint("zero below the smallest positive scale", "0", "minimum", "1e-1000", false),
                numericConstraint("equal numbers at the smallest allowed scale", "1e-1000", "const", "1E-1000", true),
                numericConstraint("a negative number at the smallest allowed scale", "-1e-1000", "minimum", "0", false),
                numericConstraint("a number at the largest allowed exponent", "1e1000", "maximum", "9e999", false),
                numericConstraint("a number at the largest allowed exponent equals itself", "1e1000", "const", "1e1000",
                        true),
                arguments("a decimal outside an enum", exactNumber("1.0000000000000000001"),
                        ObjectValue.builder().put("enum", Value.ofArray(exactNumber("1"), exactNumber("2"))).build(),
                        false),
                arguments("an integer outside an enum beyond u64", exactNumber("18446744073709551617"),
                        ObjectValue.builder()
                                .put("enum", Value.ofArray(exactNumber("18446744073709551616"), exactNumber("0")))
                                .build(),
                        false),
                arguments("equal enum members with different scales", exactNumber("1.2300"),
                        ObjectValue.builder().put("enum", Value.ofArray(exactNumber("123e-2"), exactNumber("0")))
                                .build(),
                        true),
                arguments("distinct precise decimals are unique",
                        Value.ofArray(exactNumber("1"), exactNumber("1.0000000000000000001")),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), true),
                arguments("equal decimals with different scales are not unique",
                        Value.ofArray(exactNumber("1.2300"), exactNumber("123e-2")),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), false),
                arguments("distinct integers beyond u64 are unique",
                        Value.ofArray(exactNumber("18446744073709551616"), exactNumber("18446744073709551617")),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), true),
                arguments("nested numeric differences do not equal a const",
                        ObjectValue.builder().put("amount", Value.ofArray(exactNumber("1.0000000000000000001")))
                                .build(),
                        ObjectValue.builder()
                                .put("const",
                                        ObjectValue.builder().put("amount", Value.ofArray(exactNumber("1"))).build())
                                .build(),
                        false),
                arguments("nested equal numbers with different scales are not unique",
                        Value.ofArray(ObjectValue.builder().put("amount", Value.ofArray(exactNumber("1.2300"))).build(),
                                ObjectValue.builder().put("amount", Value.ofArray(exactNumber("123e-2"))).build()),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), false),
                arguments("a precise fraction is not an integer", exactNumber("1.0000000000000000001"),
                        ObjectValue.builder().put("type", Value.of("integer")).build(), false),
                arguments("a large scaled whole number is an integer", exactNumber("18446744073709551616.00"),
                        ObjectValue.builder().put("type", Value.of("integer")).build(), true),
                arguments("distinct precise decimals in a longer array are unique",
                        Value.ofArray(exactNumber("1"), exactNumber("1.0000000000000000001"), Value.of(0), Value.of(2),
                                Value.of(3), Value.of(4), Value.of(5), Value.of(6), Value.of(7), Value.of(8),
                                Value.of(9), Value.of(10), Value.of(11), Value.of(12), Value.of(13), Value.of(14),
                                Value.of(15)),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), true),
                arguments("equal decimals in a longer array are not unique",
                        Value.ofArray(exactNumber("1.2300"), exactNumber("123e-2"), Value.of(0), Value.of(2),
                                Value.of(3), Value.of(4), Value.of(5), Value.of(6), Value.of(7), Value.of(8),
                                Value.of(9), Value.of(10), Value.of(11), Value.of(12), Value.of(13), Value.of(14),
                                Value.of(15)),
                        ObjectValue.builder().put("uniqueItems", Value.TRUE).build(), false));
    }

    private static Arguments numericConstraint(String description, String subject, String keyword, String operand,
            boolean compliant) {
        val schema = ObjectValue.builder().put(keyword, exactNumber(operand)).build();
        return arguments(description, exactNumber(subject), schema, compliant);
    }

    private static Value exactNumber(String number) {
        return Value.of(new BigDecimal(number));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#schemaLoadingErrors")
    @DisplayName("invalid recursive references and unresolved unused definitions fail during schema loading")
    void whenSchemaLoadingFailsThenEveryFunctionReturnsError(String description, String source, String external) {
        val schema    = (ObjectValue) json(source);
        val resources = json(external);
        val subject   = Value.of("text");
        val results   = new ArrayList<Value>();
        results.add(isCompliantWithExternalSchemas(subject, schema, resources));
        results.add(validateWithExternalSchemas(subject, schema, resources));
        if (Value.EMPTY_ARRAY.equals(resources)) {
            results.add(isCompliant(subject, schema));
            results.add(validate(subject, schema));
        }
        assertThat(results).allSatisfy(value -> assertThat(value).isInstanceOf(ErrorValue.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#schemaLoadingControls")
    @DisplayName("schema locations and inherited or explicit dialects retain their validation meaning")
    void whenSchemaLoadsThenItsDialectDeterminesValidation(String description, String source, String external,
            String input, boolean valid) {
        val schema    = (ObjectValue) json(source);
        val resources = json(external);
        val subject   = json(input);
        assertThat(isCompliantWithExternalSchemas(subject, schema, resources)).isEqualTo(Value.of(valid));
        assertThat(validateWithExternalSchemas(subject, schema, resources)).isInstanceOfSatisfying(ObjectValue.class,
                report -> assertThat(report.get("valid")).isEqualTo(Value.of(valid)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "$ref", "$dynamicRef" })
    @DisplayName("a loaded supplied resource cannot hide an unresolved ordinary reference in an unused definition")
    void whenALoadedResourceHasAnUnusedOrdinaryReferenceThenValidationReportsAnError(String keyword) {
        val schema    = ObjectValue.builder().put("$schema", Value.of("https://json-schema.org/draft/2020-12/schema"))
                .put(keyword, Value.of("urn:example:active")).build();
        val resources = json("""
                [{"$id":"urn:example:active","type":"string",
                  "$defs":{"unused":{"$ref":"urn:example:missing"}}}]
                """);
        val subject   = Value.of("text");
        val results   = List.of(isCompliantWithExternalSchemas(subject, schema, resources),
                validateWithExternalSchemas(subject, schema, resources));
        assertThat(results).allSatisfy(value -> assertThat(value).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("urn:example:missing")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dynamicReferenceCases")
    @DisplayName("dynamic references use their declared dialect and supplied resources")
    void whenSchemaContainsDynamicReferencesThenValidationUsesItsDialect(String description, String source,
            String external, String subject, boolean valid) {
        val registry = JsonSchemaPreparation.registry(json(external));
        val prepared = JsonSchemaPreparation.prepare(registry, (ObjectValue) json(source));
        assertThat(prepared.validate(ValueJsonMarshaller.toJsonNode(json(subject))).isEmpty()).isEqualTo(valid);
    }

    static Stream<Arguments> dynamicReferenceCases() {
        val provided = """
                [{"$id":"urn:example:active","type":"string"}]
                """;
        return Stream.of(arguments("ordinary schema", """
                {"type":"string"}
                """, "[]", "\"text\"", true), arguments("local dynamic anchor", """
                {"$dynamicRef":"#kind","$defs":{"code":{"$dynamicAnchor":"kind","type":"string"}}}
                """, "[]", "\"text\"", true), arguments("legacy annotation", """
                {"$schema":"http://json-schema.org/draft-04/schema#","$dynamicRef":"urn:example:absent"}
                """, "[]", "\"text\"", true), arguments("dynamic target behind ordinary reference", """
                {"$ref":"urn:example:outer"}
                """, """
                [{"$id":"urn:example:outer","$dynamicRef":"urn:example:active"},
                 {"$id":"urn:example:active","type":"string"}]
                """, "\"text\"", true), arguments("local fragment reaches an ordinary then dynamic reference", """
                {"$dynamicRef":"#/$defs/start","$defs":{"start":{"$ref":"urn:example:outer"}}}
                """, """
                [{"$id":"urn:example:outer","$dynamicRef":"urn:example:active"},
                 {"$id":"urn:example:active","type":"string"}]
                """, "\"text\"", true), arguments("nested dynamic reference", """
                {"properties":{"child":{"$dynamicRef":"urn:example:active"}}}
                """, provided, """
                {"child":12}
                """, false), arguments("explicit modern subschema in a legacy resource", """
                {"$schema":"http://json-schema.org/draft-04/schema#",
                 "properties":{"child":{"$schema":"https://json-schema.org/draft/2020-12/schema",
                   "$dynamicRef":"urn:example:active"}}}
                """, provided, """
                {"child":12}
                """, false));
    }

}

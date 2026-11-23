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
package io.sapl.compiler.policy;

import io.sapl.api.model.*;
import io.sapl.ast.*;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.SchemaValidatorCompiler.CombinedElementValidator;
import io.sapl.compiler.policy.SchemaValidatorCompiler.PrecompiledSchemaValidator;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.compiler.policy.SchemaValidatorCompiler.compileValidator;
import static io.sapl.compiler.util.SchemaReferenceFixtures.UNRESOLVABLE_REFERENCE;
import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SchemaValidatorCompiler")
class SchemaValidatorCompilerTests {

    private static final String INVALID_REGULAR_EXPRESSION = "The schema holds an invalid regular expression: %s.";
    private static final String NOT_A_JSON_SCHEMA          = "The schema is not a valid JSON Schema: ";

    private static final ObjectValue STRING_SCHEMA  = stringSchema();
    private static final ObjectValue NUMBER_SCHEMA  = numberSchema();
    private static final ObjectValue BOOLEAN_SCHEMA = booleanSchema();

    private static ObjectValue stringSchema() {
        return (ObjectValue) obj("type", Value.of("string"));
    }

    private static ObjectValue numberSchema() {
        return (ObjectValue) obj("type", Value.of("number"));
    }

    private static ObjectValue booleanSchema() {
        return (ObjectValue) obj("type", Value.of("boolean"));
    }

    private static SchemaStatement enforcedSchema(SubscriptionElement element, Value schema) {
        return new SchemaStatement(element, true, new Literal(schema, TEST_LOCATION), TEST_LOCATION);
    }

    private static SchemaStatement enforcedVariableSchema(SubscriptionElement element, String variableName) {
        return new SchemaStatement(element, true, new Identifier(variableName, TEST_LOCATION), TEST_LOCATION);
    }

    private static SchemaStatement nonEnforcedSchema(SubscriptionElement element, Value schema) {
        return new SchemaStatement(element, false, new Literal(schema, TEST_LOCATION), TEST_LOCATION);
    }

    /**
     * Evaluates a CompiledExpression, handling both Value and PureOperator.
     * The validator compilation never returns StreamOperator.
     */
    private static Value evaluate(CompiledExpression expr, EvaluationContext ctx) {
        return switch (expr) {
        case Value v         -> v;
        case PureOperator po -> po.evaluate(ctx);
        default              -> throw new IllegalStateException("Unexpected expression type: " + expr);
        };
    }

    @Nested
    @DisplayName("compileValidator")
    class CompileValidatorTests {

        @Test
        @DisplayName("when schema condition is null then returns constant TRUE")
        void whenSchemaConditionIsNullThenReturnsConstantTrue() {
            val result = compileValidator((SchemaCondition) null, compilationContext());
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when only non-enforced schemas then returns constant TRUE")
        void whenOnlyNonEnforcedSchemasThenReturnsConstantTrue() {
            val schemas = List.of(nonEnforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    nonEnforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when single enforced schema then returns PrecompiledSchemaValidator")
        void whenSingleEnforcedSchemaThenReturnsPrecompiledSchemaValidator() {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(PrecompiledSchemaValidator.class);
        }

        @Test
        @DisplayName("when multiple enforced schemas for different elements then returns CombinedElementValidator")
        void whenMultipleEnforcedSchemasForDifferentElementsThenReturnsCombinedElementValidator() {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(CombinedElementValidator.class);
        }

        @Test
        @DisplayName("when mixed enforced and non-enforced then only enforced are compiled")
        void whenMixedEnforcedAndNonEnforcedThenOnlyEnforcedAreCompiled() {
            val schemas = List.of(nonEnforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA),
                    nonEnforcedSchema(SubscriptionElement.RESOURCE, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(PrecompiledSchemaValidator.class);
        }
    }

    @Nested
    @DisplayName("Compile-time validation errors")
    class CompileTimeValidationErrorTests {

        @Test
        @DisplayName("when schema is an undefined variable reference then throws SaplCompilerException")
        void whenSchemaIsVariableReferenceThenThrowsCompilerException() {
            val schemas = List.of(enforcedVariableSchema(SubscriptionElement.SUBJECT, "mySchema"));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Schema must be an object, got: UndefinedValue");
        }

        @ParameterizedTest(name = "when schema is {0} then throws SaplCompilerException")
        @MethodSource("nonObjectSchemas")
        void whenSchemaNotObjectValueThenThrowsCompilerException(String description, Value schema) {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Schema must be an object");
        }

        static Stream<Arguments> nonObjectSchemas() {
            return Stream.of(arguments("TextValue", Value.of("not a schema")), arguments("NumberValue", Value.of(42)),
                    arguments("BooleanValue", Value.TRUE), arguments("ArrayValue", array(Value.of(1))),
                    arguments("NullValue", Value.NULL));
        }

        @Test
        @DisplayName("when schema contains unresolvable $ref then throws SaplCompilerException")
        void whenSchemaContainsUnresolvableRefThenThrowsCompilerException() {
            val schemaWithRef = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("location", obj("$ref", Value.of("https://example.com/coordinates"))));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schemaWithRef));
            val ctx           = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA).hasMessageNotContaining("Exception");
        }

        @Test
        @DisplayName("when schema contains nested unresolvable $ref in array then throws SaplCompilerException")
        void whenSchemaContainsNestedUnresolvableRefInArrayThenThrowsCompilerException() {
            val schemaWithNestedRef = (ObjectValue) obj("anyOf",
                    array(obj("type", Value.of("string")), obj("$ref", Value.of("https://example.com/other"))));
            val schemas             = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schemaWithNestedRef));
            val ctx                 = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA).hasMessageNotContaining("Exception");
        }

        @ParameterizedTest(name = "when schema pattern is {0} then throws SaplCompilerException naming the reason")
        @MethodSource("invalidSchemaPatterns")
        void whenSchemaPatternInvalidThenThrowsCompilerException(String description, String pattern, String reason) {
            val schema  = (ObjectValue) obj("type", Value.of("string"), "pattern", Value.of(pattern));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessage(INVALID_REGULAR_EXPRESSION.formatted(reason));
        }

        static Stream<Arguments> invalidSchemaPatterns() {
            return Stream.of(arguments("a look-ahead", "^(?=.*[0-9]).{8,}$", "invalid or unsupported Perl syntax"),
                    arguments("too large to compile", "(?:a{1000}){1000}", "expression too large"));
        }

        @ParameterizedTest(name = "when schema holds {0} then the document is refused at compile time")
        @MethodSource
        void whenSchemaHoldsPatternRefusedByRe2ThenThrowsCompilerException(String description, String schema,
                String reason) {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, json(schema)));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessage(INVALID_REGULAR_EXPRESSION.formatted(reason));
        }

        static Stream<Arguments> whenSchemaHoldsPatternRefusedByRe2ThenThrowsCompilerException() {
            return Stream.of(arguments("a backreference in a nested pattern", """
                    {"type": "object", "properties": {"code": {"type": "string", "pattern": "(a)\\\\1"}}}
                    """, "invalid escape sequence"), arguments("a look-behind in a patternProperties key", """
                    {"type": "object", "patternProperties": {"(?<=x)y": {"type": "number"}}}
                    """, "invalid named capture"), arguments("a look-ahead in a branch a subject need not take", """
                    {"anyOf": [{"type": "number"}, {"type": "string", "pattern": "(?!admin)"}]}
                    """, "invalid or unsupported Perl syntax"));
        }

        @Test
        @DisplayName("when schema references a schema of the schemas variable whose pattern RE2 refuses then the document is refused at compile time")
        void whenReferencedSchemaHoldsPatternRefusedByRe2ThenThrowsCompilerException() {
            val variables = (ObjectValue) json(
                    """
                            {"schemas": [{"$id": "https://example.com/password", "type": "string", "pattern": "^(?=.*[0-9]).{8,}$"}]}
                            """);
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, json("""
                    {"$ref": "https://example.com/password"}
                    """)));
            val ctx       = compilationContext(variables);
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessage(INVALID_REGULAR_EXPRESSION.formatted("invalid or unsupported Perl syntax"));
        }

        @Test
        @DisplayName("when schema expression evaluates to errors then throws SaplCompilerException")
        void whenSchemaExpressionEvaluatesToErrorThenThrowsCompilerException() {
            val errorValue = Value.error("schema lookup failed");
            val schemas    = List.of(enforcedSchema(SubscriptionElement.SUBJECT, errorValue));
            val ctx        = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Schema expression evaluation failed")
                    .hasMessageContaining("schema lookup failed");
        }
    }

    @Nested
    @DisplayName("PrecompiledSchemaValidator")
    class PrecompiledSchemaValidatorTests {

        @ParameterizedTest(name = "when element is {0} and matches schema then returns TRUE")
        @EnumSource(SubscriptionElement.class)
        void whenElementMatchesSchemaThenReturnsTrue(SubscriptionElement element) {
            val schemas   = List.of(enforcedSchema(element, STRING_SCHEMA));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            val ctx    = subscriptionContext("""
                    {
                        "subject": "alice",
                        "action": "read",
                        "resource": "document",
                        "environment": "production"
                    }
                    """);
            val result = evaluate(validator, ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @ParameterizedTest(name = "when element is {0} and does not match schema then returns FALSE")
        @EnumSource(SubscriptionElement.class)
        void whenElementDoesNotMatchSchemaThenReturnsFalse(SubscriptionElement element) {
            val schemas   = List.of(enforcedSchema(element, STRING_SCHEMA));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            val ctx    = subscriptionContext("""
                    {
                        "subject": 123,
                        "action": 456,
                        "resource": 789,
                        "environment": 999
                    }
                    """);
            val result = evaluate(validator, ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("isDependingOnSubscription returns true")
        void isDependingOnSubscriptionReturnsTrue() {
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val validator = (PureOperator) compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            assertThat(validator.isDependingOnSubscription()).isTrue();
        }

        @Test
        @DisplayName("location returns the configured location")
        void locationReturnsConfiguredLocation() {
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val validator = (PureOperator) compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            assertThat(validator.location()).isSameAs(TEST_LOCATION);
        }

        @Test
        @DisplayName("when subject matches object schema with required property then returns TRUE")
        void whenSubjectMatchesObjectSchemaWithRequiredPropertyThenReturnsTrue() {
            val schema    = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("name", obj("type", Value.of("string"))), "required", array(Value.of("name")));
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            val ctx = subscriptionContext("""
                    {
                        "subject": {"name": "alice"},
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            assertThat(evaluate(validator, ctx)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when subject missing required property then returns FALSE")
        void whenSubjectMissingRequiredPropertyThenReturnsFalse() {
            val schema    = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("name", obj("type", Value.of("string"))), "required", array(Value.of("name")));
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            val ctx = subscriptionContext("""
                    {
                        "subject": {"age": 25},
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            assertThat(evaluate(validator, ctx)).isEqualTo(Value.FALSE);
        }

    }

    @Nested
    @DisplayName("Patterns on hostile input")
    class PatternsOnHostileInputTests {

        /**
         * A schema {@code pattern} keyword is matched once per element of the
         * validated collection. Patterns are matched in linear time, so a pattern
         * that backtracking engines take exponential time for answers each element
         * quickly, and the whole validation scales linearly with the element count.
         */
        @Test
        @Timeout(30)
        @DisplayName("when many array elements are matched against a pattern that would backtrack catastrophically then the validation answers quickly")
        void whenManyElementsMatchBacktrackingPatternThenValidationAnswersQuickly() {
            val arraySchema = (ObjectValue) obj("type", Value.of("array"), "items",
                    obj("type", Value.of("string"), "pattern", Value.of("(.*a){30}$")));
            val validator   = compileValidator(List.of(enforcedSchema(SubscriptionElement.SUBJECT, arraySchema)),
                    compilationContext());
            assertThat(validator).isNotNull();
            val hostileElement = "a".repeat(24);
            val hostileArray   = ("\"" + hostileElement + "\",").repeat(1_000);
            val ctx            = subscriptionContext("""
                    {
                        "subject": [%s"%s"],
                        "action": "read",
                        "resource": "data"
                    }
                    """.formatted(hostileArray, hostileElement));

            val result = evaluate(validator, ctx);

            assertThat(result).isEqualTo(Value.FALSE);
        }
    }

    @Nested
    @DisplayName("CombinedElementValidator")
    class CombinedElementValidatorTests {

        @Test
        @DisplayName("when all validators pass then returns TRUE")
        void whenAllValidatorsPassThenReturnsTrue() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            val ctx    = subscriptionContext("""
                    {
                        "subject": "alice",
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            val result = evaluate(combined, ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when first validator fails then returns FALSE immediately")
        void whenFirstValidatorFailsThenReturnsFalseImmediately() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            val ctx    = subscriptionContext("""
                    {
                        "subject": 123,
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            val result = evaluate(combined, ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("when second validator fails then returns FALSE")
        void whenSecondValidatorFailsThenReturnsFalse() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            val ctx    = subscriptionContext("""
                    {
                        "subject": "alice",
                        "action": 456,
                        "resource": "data"
                    }
                    """);
            val result = evaluate(combined, ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("location returns first validator location")
        void locationReturnsFirstValidatorLocation() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = (PureOperator) compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            assertThat(combined.location()).isSameAs(TEST_LOCATION);
        }

        @Test
        @DisplayName("isDependingOnSubscription returns true")
        void isDependingOnSubscriptionReturnsTrue() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = (PureOperator) compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            assertThat(combined.isDependingOnSubscription()).isTrue();
        }

        @Test
        @DisplayName("when all different element types match their schemas then returns TRUE")
        void whenAllDifferentElementTypesMatchSchemasThenReturnsTrue() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, NUMBER_SCHEMA),
                    enforcedSchema(SubscriptionElement.RESOURCE, BOOLEAN_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            val ctx = subscriptionContext("""
                    {
                        "subject": "alice",
                        "action": 42,
                        "resource": true
                    }
                    """);
            assertThat(evaluate(combined, ctx)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when one element type does not match schema then returns FALSE")
        void whenOneElementTypeDoesNotMatchSchemaThenReturnsFalse() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, NUMBER_SCHEMA),
                    enforcedSchema(SubscriptionElement.RESOURCE, BOOLEAN_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            val ctx = subscriptionContext("""
                    {
                        "subject": "alice",
                        "action": "read",
                        "resource": true
                    }
                    """);
            assertThat(evaluate(combined, ctx)).isEqualTo(Value.FALSE);
        }

    }

    @Nested
    @DisplayName("$ref resolution")
    class RefResolutionTests {

        @Test
        @DisplayName("when schema has no $ref then compiles successfully")
        void whenSchemaHasNoRefThenCompilesSuccessfully() {
            val schema  = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("name", obj("type", Value.of("string")), "age", obj("type", Value.of("integer"))));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("when schema has deeply nested unresolvable $ref then throws")
        void whenSchemaHasDeeplyNestedUnresolvableRefThenThrows() {
            val deepSchema = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("level1", obj("type", Value.of("object"), "properties",
                            obj("level2", obj("$ref", Value.of("https://example.com/deep"))))));
            val schemas    = List.of(enforcedSchema(SubscriptionElement.SUBJECT, deepSchema));
            val ctx        = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA).hasMessageNotContaining("Exception");
        }

        @Test
        @DisplayName("when schema has unresolvable $ref in oneOf array then throws")
        void whenSchemaHasUnresolvableRefInOneOfArrayThenThrows() {
            val oneOfSchema = (ObjectValue) obj("oneOf",
                    array(obj("type", Value.of("string")), obj("$ref", Value.of("https://example.com/number"))));
            val schemas     = List.of(enforcedSchema(SubscriptionElement.SUBJECT, oneOfSchema));
            val ctx         = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA).hasMessageNotContaining("Exception");
        }

        @Test
        @DisplayName("when schema has $ref key with non-URI value then throws")
        void whenSchemaHasRefKeyWithNonUriValueThenThrows() {
            val schema  = (ObjectValue) obj("$ref", Value.of("not-a-uri"));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA).hasMessageNotContaining("Exception");
        }

        @Test
        @DisplayName("when schema uses local $defs then compiles successfully")
        void whenSchemaUsesLocalDefsThenCompilesSuccessfully() {
            val schema  = (ObjectValue) obj("$defs",
                    obj("address",
                            obj("type", Value.of("object"), "properties",
                                    obj("city", obj("type", Value.of("string"))))),
                    "type", Value.of("object"), "properties", obj("home", obj("$ref", Value.of("#/$defs/address"))));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(PrecompiledSchemaValidator.class);
        }

        @Test
        @DisplayName("when schema $ref resolves via SCHEMAS variable then compiles successfully")
        void whenSchemaRefResolvesViaSchemasVariableThenCompilesSuccessfully() {
            val addressSchema = (ObjectValue) obj("$id", Value.of("https://example.com/address"), "type",
                    Value.of("object"), "properties", obj("city", obj("type", Value.of("string"))));
            val variables     = (ObjectValue) obj("schemas", array(addressSchema));
            val mainSchema    = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("shipping", obj("$ref", Value.of("https://example.com/address"))));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, mainSchema));
            val result        = compileValidator(schemas, compilationContext(variables));
            assertThat(result).isInstanceOf(PrecompiledSchemaValidator.class);
        }
    }

    @Nested
    @DisplayName("Schemas a schema names")
    class SchemaReferenceTests {

        @ParameterizedTest(name = "when a schema names {1} by {0} then the document is refused")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#foreignReferences")
        void whenSchemaNamesForeignReferenceThenDocumentIsRefused(String keyword, String reference) {
            val schema  = (ObjectValue) obj(keyword, Value.of(reference), "type", Value.of("string"));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessage(NOT_A_JSON_SCHEMA + UNRESOLVABLE_REFERENCE.formatted(reference) + ".");
        }

        @ParameterizedTest(name = "when a schema names {0} then subjects are validated against it")
        @MethodSource("io.sapl.compiler.util.SchemaReferenceFixtures#builtInReferences")
        void whenSchemaNamesBuiltInMetaSchemaThenSubjectsAreValidated(String description, String schema,
                String compliant, String nonCompliant) {
            val validator = compileValidator(List.of(enforcedSchema(SubscriptionElement.SUBJECT, json(schema))),
                    compilationContext());
            assertThat(List.of(evaluate(validator, subscriptionContext(subscriptionWithSubject(compliant))),
                    evaluate(validator, subscriptionContext(subscriptionWithSubject(nonCompliant)))))
                    .containsExactly(Value.TRUE, Value.FALSE);
        }

        private static String subscriptionWithSubject(String subject) {
            return """
                    {"subject": %s, "action": "read", "resource": "data"}
                    """.formatted(subject);
        }
    }

    @Nested
    @DisplayName("Pre-compilation benefits")
    class PreCompilationTests {

        @Test
        @DisplayName("when valid schema then pre-compiles without runtime overhead")
        void whenValidSchemaThenPreCompilesWithoutRuntimeOverhead() {
            val complexSchema = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("username",
                            obj("type", Value.of("string"), "minLength", Value.of(3), "maxLength", Value.of(50)),
                            "email", obj("type", Value.of("string"), "format", Value.of("email")), "age",
                            obj("type", Value.of("integer"), "minimum", Value.of(0), "maximum", Value.of(150))),
                    "required", array(Value.of("username"), Value.of("email")));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, complexSchema));
            val validator     = compileValidator(schemas, compilationContext());
            assertThat(validator).isInstanceOf(PrecompiledSchemaValidator.class);

            val ctx = subscriptionContext("""
                    {
                        "subject": {
                            "username": "alice",
                            "email": "alice@example.com",
                            "age": 30
                        },
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            assertThat(evaluate(validator, ctx)).isEqualTo(Value.TRUE);

            val ctx2 = subscriptionContext("""
                    {
                        "subject": {
                            "username": "ab",
                            "email": "not-an-email"
                        },
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            assertThat(evaluate(validator, ctx2)).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("An unknown JSON Schema type refuses the enforced schema")
        void whenSchemaTypeIsUnknownThenCompilationFails() {
            val invalidSchema = (ObjectValue) obj("type", Value.of("not-a-valid-type"));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, invalidSchema));
            val context       = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, context)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageStartingWith(NOT_A_JSON_SCHEMA);
        }

        @Test
        @DisplayName("a validator that throws at evaluation time yields ErrorValue with the plain reason, not a thrown exception")
        void whenValidateThrowsAtEvalTimeThenReturnsErrorValue() {
            // A networknt throw on adversarial input must surface as an ErrorValue, not
            // crash the eval path.
            val throwingSchema = mock(Schema.class);
            when(throwingSchema.validate(any(JsonNode.class)))
                    .thenThrow(new SchemaException(new IllegalStateException("boom")));
            val validator = new PrecompiledSchemaValidator(SubscriptionElement.SUBJECT, STRING_SCHEMA, throwingSchema,
                    TEST_LOCATION);
            val ctx       = subscriptionContext("""
                    { "subject": "anything", "action": "read", "resource": "data" }
                    """);

            assertThat(validator.evaluate(ctx)).isInstanceOfSatisfying(ErrorValue.class,
                    error -> assertThat(error.message()).isEqualTo("Schema validation failed: boom."));
        }
    }

    @Nested
    @DisplayName("Semantic hash")
    class SemanticHashTests {

        /**
         * The canonical order of operands and the policy index rely on semantic
         * hashes that are the same in every run, so the subscription element is
         * hashed by its name and the validators are combined in the order of the
         * elements, never in the order of a hash map over enum constants.
         */
        @Test
        @DisplayName("schemas for several elements hash to the same value in every run and in any written order")
        void whenSchemasForSeveralElementsThenSemanticHashIsTheSameInEveryRunAndAnyOrder() {
            val ctx     = compilationContext();
            val written = (PureOperator) compileValidator(
                    List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                            enforcedSchema(SubscriptionElement.ACTION, NUMBER_SCHEMA),
                            enforcedSchema(SubscriptionElement.ACTION, BOOLEAN_SCHEMA)),
                    ctx);
            val swapped = (PureOperator) compileValidator(
                    List.of(enforcedSchema(SubscriptionElement.ACTION, NUMBER_SCHEMA),
                            enforcedSchema(SubscriptionElement.ACTION, BOOLEAN_SCHEMA),
                            enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA)),
                    ctx);
            assertThat(new long[] { written.semanticHash(), swapped.semanticHash() })
                    .containsExactly(4479095267155628887L, 4479095267155628887L);
        }
    }
}

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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.ast.Identifier;
import io.sapl.ast.Literal;
import io.sapl.ast.SchemaStatement;
import io.sapl.ast.SubscriptionElement;
import io.sapl.compiler.policy.SchemaValidatorCompiler.CombinedSchemaValidator;
import io.sapl.compiler.policy.SchemaValidatorCompiler.PrecompiledSchemaValidator;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.compiler.policy.SchemaValidatorCompiler.compileValidator;
import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SchemaValidatorCompiler")
class SchemaValidatorCompilerTests {

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
        return new SchemaStatement(element, new Literal(schema, TEST_LOCATION), TEST_LOCATION);
    }

    private static SchemaStatement enforcedVariableSchema(SubscriptionElement element, String variableName) {
        return new SchemaStatement(element, new Identifier(variableName, TEST_LOCATION), TEST_LOCATION);
    }

    private static SchemaStatement nonEnforcedSchema(SubscriptionElement element, Value schema) {
        return new SchemaStatement(element, new Literal(schema, TEST_LOCATION), TEST_LOCATION);
    }

    @Nested
    @DisplayName("compileValidator")
    class CompileValidatorTests {

        @Test
        @DisplayName("when schema list is empty then returns CombinedSchemaValidator with empty validators")
        void whenEmptySchemaList_thenReturnsCombinedSchemaValidatorWithEmptyValidators() {
            val result = compileValidator(List.of(), compilationContext());
            assertThat(result).isInstanceOf(CombinedSchemaValidator.class);
            assertThat(((CombinedSchemaValidator) result).validators()).isEmpty();
        }

        @Test
        @DisplayName("when only non-enforced schemas then returns CombinedSchemaValidator")
        void whenOnlyNonEnforcedSchemas_thenReturnsCombinedSchemaValidator() {
            val schemas = List.of(nonEnforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    nonEnforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(CombinedSchemaValidator.class);
        }

        @Test
        @DisplayName("when single enforced schema then returns PrecompiledSchemaValidator")
        void whenSingleEnforcedSchema_thenReturnsPrecompiledSchemaValidator() {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(PrecompiledSchemaValidator.class);
        }

        @Test
        @DisplayName("when multiple enforced schemas then returns CombinedSchemaValidator")
        void whenMultipleEnforcedSchemas_thenReturnsCombinedSchemaValidator() {
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(CombinedSchemaValidator.class);
        }

        @Test
        @DisplayName("when mixed enforced and non-enforced then all are compiled into CombinedSchemaValidator")
        void whenMixedEnforcedAndNonEnforced_thenAllCompiledIntoCombinedSchemaValidator() {
            val schemas = List.of(nonEnforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA),
                    nonEnforcedSchema(SubscriptionElement.RESOURCE, STRING_SCHEMA));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isInstanceOf(CombinedSchemaValidator.class);
        }
    }

    @Nested
    @DisplayName("Compile-time validation errors")
    class CompileTimeValidationErrorTests {

        @Test
        @DisplayName("when schema is variable reference then throws SaplCompilerException")
        void whenSchemaIsVariableReference_thenThrowsCompilerException() {
            val schemas = List.of(enforcedVariableSchema(SubscriptionElement.SUBJECT, "mySchema"));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("Schema must be a constant object literal");
        }

        @ParameterizedTest(name = "when schema is {0} then throws SaplCompilerException")
        @MethodSource("nonObjectSchemas")
        void whenSchemaNotObjectValue_thenThrowsCompilerException(String description, Value schema) {
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
        @DisplayName("when schema contains $ref then throws SaplCompilerException")
        void whenSchemaContainsRef_thenThrowsCompilerException() {
            val schemaWithRef = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("location", obj("$ref", Value.of("https://example.com/coordinates"))));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schemaWithRef));
            val ctx           = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("when schema contains nested $ref in array then throws SaplCompilerException")
        void whenSchemaContainsNestedRefInArray_thenThrowsCompilerException() {
            val schemaWithNestedRef = (ObjectValue) obj("anyOf",
                    array(obj("type", Value.of("string")), obj("$ref", Value.of("https://example.com/other"))));
            val schemas             = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schemaWithNestedRef));
            val ctx                 = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("when schema expression evaluates to error then throws SaplCompilerException")
        void whenSchemaExpressionEvaluatesToError_thenThrowsCompilerException() {
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
        void whenElementMatchesSchema_thenReturnsTrue(SubscriptionElement element) {
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
            val result = validator.evaluate(ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @ParameterizedTest(name = "when element is {0} and does not match schema then returns FALSE")
        @EnumSource(SubscriptionElement.class)
        void whenElementDoesNotMatchSchema_thenReturnsFalse(SubscriptionElement element) {
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
            val result = validator.evaluate(ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("isDependingOnSubscription returns true")
        void isDependingOnSubscription_returnsTrue() {
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            assertThat(validator.isDependingOnSubscription()).isTrue();
        }

        @Test
        @DisplayName("location returns the configured location")
        void location_returnsConfiguredLocation() {
            val schemas   = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA));
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            assertThat(validator.location()).isSameAs(TEST_LOCATION);
        }

        @Test
        @DisplayName("when subject matches object schema with required property then returns TRUE")
        void whenSubjectMatchesObjectSchemaWithRequiredProperty_thenReturnsTrue() {
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
            assertThat(validator.evaluate(ctx)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when subject missing required property then returns FALSE")
        void whenSubjectMissingRequiredProperty_thenReturnsFalse() {
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
            assertThat(validator.evaluate(ctx)).isEqualTo(Value.FALSE);
        }

    }

    @Nested
    @DisplayName("CombinedSchemaValidator")
    class CombinedSchemaValidatorTests {

        @Test
        @DisplayName("when all validators pass then returns TRUE")
        void whenAllValidatorsPass_thenReturnsTrue() {
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
            val result = combined.evaluate(ctx);
            assertThat(result).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when first validator fails then returns FALSE immediately")
        void whenFirstValidatorFails_thenReturnsFalseImmediately() {
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
            val result = combined.evaluate(ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("when second validator fails then returns FALSE")
        void whenSecondValidatorFails_thenReturnsFalse() {
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
            val result = combined.evaluate(ctx);
            assertThat(result).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("location returns first validator location")
        void location_returnsFirstValidatorLocation() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            assertThat(combined.location()).isSameAs(TEST_LOCATION);
        }

        @Test
        @DisplayName("isDependingOnSubscription returns true")
        void isDependingOnSubscription_returnsTrue() {
            val schemas  = List.of(enforcedSchema(SubscriptionElement.SUBJECT, STRING_SCHEMA),
                    enforcedSchema(SubscriptionElement.ACTION, STRING_SCHEMA));
            val combined = compileValidator(schemas, compilationContext());
            assertThat(combined).isNotNull();
            assertThat(combined.isDependingOnSubscription()).isTrue();
        }

        @Test
        @DisplayName("when all different element types match their schemas then returns TRUE")
        void whenAllDifferentElementTypesMatchSchemas_thenReturnsTrue() {
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
            assertThat(combined.evaluate(ctx)).isEqualTo(Value.TRUE);
        }

        @Test
        @DisplayName("when one element type does not match schema then returns FALSE")
        void whenOneElementTypeDoesNotMatchSchema_thenReturnsFalse() {
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
            assertThat(combined.evaluate(ctx)).isEqualTo(Value.FALSE);
        }

    }

    @Nested
    @DisplayName("$ref detection")
    class RefDetectionTests {

        @Test
        @DisplayName("when schema has no $ref then compiles successfully")
        void whenSchemaHasNoRef_thenCompilesSuccessfully() {
            val schema  = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("name", obj("type", Value.of("string")), "age", obj("type", Value.of("integer"))));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val result  = compileValidator(schemas, compilationContext());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("when schema has deeply nested $ref then throws")
        void whenSchemaHasDeeplyNestedRef_thenThrows() {
            val deepSchema = (ObjectValue) obj("type", Value.of("object"), "properties",
                    obj("level1", obj("type", Value.of("object"), "properties",
                            obj("level2", obj("$ref", Value.of("https://example.com/deep"))))));
            val schemas    = List.of(enforcedSchema(SubscriptionElement.SUBJECT, deepSchema));
            val ctx        = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("when schema has $ref in oneOf array then throws")
        void whenSchemaHasRefInOneOfArray_thenThrows() {
            val oneOfSchema = (ObjectValue) obj("oneOf",
                    array(obj("type", Value.of("string")), obj("$ref", Value.of("https://example.com/number"))));
            val schemas     = List.of(enforcedSchema(SubscriptionElement.SUBJECT, oneOfSchema));
            val ctx         = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("$ref");
        }

        @Test
        @DisplayName("when schema has $ref key with non-URI value then still throws")
        void whenSchemaHasRefKeyWithNonUriValue_thenStillThrows() {
            val schema  = (ObjectValue) obj("$ref", Value.of("not-a-uri"));
            val schemas = List.of(enforcedSchema(SubscriptionElement.SUBJECT, schema));
            val ctx     = compilationContext();
            assertThatThrownBy(() -> compileValidator(schemas, ctx)).isInstanceOf(SaplCompilerException.class)
                    .hasMessageContaining("$ref");
        }
    }

    @Nested
    @DisplayName("Pre-compilation benefits")
    class PreCompilationTests {

        @Test
        @DisplayName("when valid schema then pre-compiles without runtime overhead")
        void whenValidSchema_thenPreCompilesWithoutRuntimeOverhead() {
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
            assertThat(validator.evaluate(ctx)).isEqualTo(Value.TRUE);

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
            assertThat(validator.evaluate(ctx2)).isEqualTo(Value.FALSE);
        }

        @Test
        @DisplayName("when invalid JSON Schema then throws at compile time")
        void whenInvalidJsonSchema_thenThrowsAtCompileTime() {
            // "type" must be a valid JSON Schema type
            val invalidSchema = (ObjectValue) obj("type", Value.of("not-a-valid-type"));
            val schemas       = List.of(enforcedSchema(SubscriptionElement.SUBJECT, invalidSchema));
            // The networknt library validates schema structure at parse time
            // This should compile successfully but fail validation at runtime
            // because "not-a-valid-type" is treated as a never-matching type
            val validator = compileValidator(schemas, compilationContext());
            assertThat(validator).isNotNull();
            val ctx = subscriptionContext("""
                    {
                        "subject": "anything",
                        "action": "read",
                        "resource": "data"
                    }
                    """);
            assertThat(validator.evaluate(ctx)).isEqualTo(Value.FALSE);
        }
    }
}

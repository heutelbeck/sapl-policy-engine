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

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.ast.SchemaCondition;
import io.sapl.ast.SchemaStatement;
import io.sapl.ast.SubscriptionElement;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Compiles schema statements into pre-compiled PureOperators that validate
 * subscription elements.
 * <p>
 * Schema expressions are validated at compile time and must be:
 * <ul>
 * <li>Constant object literals (not runtime expressions)</li>
 * <li>Valid JSON Schema objects</li>
 * <li>Free of {@code $ref} references (which would require external schemas at
 * runtime)</li>
 * </ul>
 * <p>
 * Only schemas with {@code enforced = true} are compiled into the validator.
 * The validator returns:
 * <ul>
 * <li>{@code TRUE} if all enforced schemas pass validation</li>
 * <li>{@code FALSE} if any enforced schema fails validation</li>
 * </ul>
 * <p>
 * Pre-compilation provides significant performance benefits by parsing and
 * compiling the JSON schema once at compile time rather than on every
 * evaluation.
 */
@UtilityClass
public class SchemaValidatorCompiler {

    private static final String ERROR_SCHEMA_CONTAINS_REF        = "Schema contains $ref which requires external schemas at runtime. "
            + "Use inline schema definitions instead, or move $ref targets into the schema itself.";
    private static final String ERROR_SCHEMA_EVALUATION_FAILED   = "Schema expression evaluation failed: %s";
    private static final String ERROR_SCHEMA_INVALID_JSON_SCHEMA = "Invalid JSON Schema: %s";
    private static final String ERROR_SCHEMA_MUST_BE_CONSTANT    = "Schema must be a constant object literal, not a runtime expression. "
            + "Variable references and function calls are not allowed in schema definitions.";
    private static final String ERROR_SCHEMA_MUST_BE_OBJECT      = "Schema must be an object, got: %s";

    private static final String REF_FIELD = "$ref";

    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public static CompiledExpression compileValidator(@Nullable SchemaCondition match, CompilationContext ctx) {
        if (match == null || match.schemas().isEmpty()) {
            return Value.TRUE;
        }
        return compileValidator(match.schemas(), ctx);
    }

    public static CompiledExpression compileValidator(@NonNull List<SchemaStatement> schemas, CompilationContext ctx) {
        val validators = schemas.stream().map(schema -> compileSchemaValidator(schema, ctx)).toList();
        if (validators.size() == 1) {
            return validators.getFirst();
        }
        return new CombinedSchemaValidator(validators);
    }

    private static PrecompiledSchemaValidator compileSchemaValidator(SchemaStatement schema, CompilationContext ctx) {
        val compiledSchema = ExpressionCompiler.compile(schema.schema(), ctx);
        val location       = schema.location();
        return switch (compiledSchema) {
        case ErrorValue error                                            ->
            throw new SaplCompilerException(ERROR_SCHEMA_EVALUATION_FAILED.formatted(error.message()), location);
        case PureOperator ignored                                        ->
            throw new SaplCompilerException(ERROR_SCHEMA_MUST_BE_CONSTANT, location);
        case ObjectValue constantSchema when containsRef(constantSchema) ->
            throw new SaplCompilerException(ERROR_SCHEMA_CONTAINS_REF, location);
        case ObjectValue constantSchema                                  ->
            createPrecompiledValidator(schema.element(), constantSchema, location);
        default                                                          -> throw new SaplCompilerException(
                ERROR_SCHEMA_MUST_BE_OBJECT.formatted(compiledSchema.getClass().getSimpleName()), location);
        };
    }

    private static boolean containsRef(ObjectValue schema) {
        if (schema.containsKey(REF_FIELD)) {
            return true;
        }
        for (val entry : schema.entrySet()) {
            if (containsRef(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRef(ArrayValue array) {
        for (val item : array) {
            if (containsRef(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRef(Value value) {
        return switch (value) {
        case ObjectValue obj -> containsRef(obj);
        case ArrayValue arr  -> containsRef(arr);
        default              -> false;
        };
    }

    /**
     * Creates a pre-compiled validator for a constant schema.
     */
    private static PrecompiledSchemaValidator createPrecompiledValidator(SubscriptionElement element,
            ObjectValue constantSchema, SourceLocation location) {
        try {
            val schemaNode        = ValueJsonMarshaller.toJsonNode(constantSchema);
            val precompiledSchema = SCHEMA_REGISTRY.getSchema(SchemaLocation.of("mem://inline"), schemaNode);
            // Force initialization to catch schema errors at compile time
            precompiledSchema.initializeValidators();
            return new PrecompiledSchemaValidator(element, precompiledSchema, location);
        } catch (SchemaException e) {
            throw new SaplCompilerException(ERROR_SCHEMA_INVALID_JSON_SCHEMA.formatted(e.getMessage()), location);
        }
    }

    record PrecompiledSchemaValidator(SubscriptionElement element, Schema schema, SourceLocation location)
            implements PureOperator {

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val elementValue = getSubscriptionElement(ctx);
            if (elementValue instanceof ErrorValue) {
                return elementValue;
            }
            val subjectNode = ValueJsonMarshaller.toJsonNode(elementValue);
            val messages    = schema.validate(subjectNode);
            return messages.isEmpty() ? Value.TRUE : Value.FALSE;
        }

        private Value getSubscriptionElement(EvaluationContext ctx) {
            return switch (element) {
            case SUBJECT     -> ctx.subject();
            case ACTION      -> ctx.action();
            case RESOURCE    -> ctx.resource();
            case ENVIRONMENT -> ctx.environment();
            };
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }
    }

    record CombinedSchemaValidator(List<PrecompiledSchemaValidator> validators) implements PureOperator {
        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (val validator : validators) {
                val result = validator.evaluate(ctx);
                if (result instanceof ErrorValue || Value.FALSE.equals(result)) {
                    return result;
                }
            }
            return Value.TRUE;
        }

        @Override
        public SourceLocation location() {
            return validators.getFirst().location();
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }
    }
}

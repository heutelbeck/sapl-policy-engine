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
import com.networknt.schema.SchemaRegistry;
import io.sapl.api.model.*;
import io.sapl.ast.SchemaCondition;
import io.sapl.ast.SchemaStatement;
import io.sapl.ast.SubscriptionElement;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.OperandStructure;
import io.sapl.compiler.expressions.OperandStructure.StructuredOperator;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.SemanticHashing;
import io.sapl.compiler.util.JsonSchemaPreparation;
import io.sapl.compiler.util.SchemaErrors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Compiles schema statements into pre-compiled PureOperators that validate
 * subscription elements.
 * <p>
 * Schema expressions are validated at compile time and must be:
 * <ul>
 * <li>Constant object literals (not runtime expressions)</li>
 * <li>Valid JSON Schema objects (schemas using {@code $ref} are resolved
 * against the SCHEMAS pdp.json variable)</li>
 * </ul>
 * <p>
 * Only schemas with {@code enforced = true} are compiled into the validator.
 * Multiple schemas for the same subscription element use OR semantics
 * (at least one must pass). Schemas for different elements use AND semantics
 * (all elements must pass).
 * <p>
 * Pre-compilation provides significant performance benefits by parsing and
 * compiling the JSON schema once at compile time rather than on every
 * evaluation.
 */
@UtilityClass
public class SchemaValidatorCompiler {

    private static final String ERROR_SCHEMA_EVALUATION_FAILED   = "Schema expression evaluation failed: %s";
    private static final String ERROR_SCHEMA_INVALID_JSON_SCHEMA = "The schema is not a valid JSON Schema: %s.";
    private static final String ERROR_SCHEMA_MUST_BE_CONSTANT    = "Schema must be a constant object literal, not a runtime expression. "
            + "Variable references and function calls are not allowed in schema definitions.";
    private static final String ERROR_SCHEMA_MUST_BE_OBJECT      = "Schema must be an object, got: %s";
    private static final String ERROR_SCHEMA_VALIDATION_FAILED   = "Schema validation failed: %s.";

    /**
     * Compiles the enforced schema guard for a document.
     *
     * @param match document schema declarations, if present
     * @param ctx compilation context supplying constants and local schemas
     * @return the compiled guard, or true for a document without enforced schemas
     * @throws SaplCompilerException if an enforced schema cannot be prepared
     */
    public static CompiledExpression compileValidator(@Nullable SchemaCondition match, CompilationContext ctx) {
        if (match == null || match.schemas().isEmpty()) {
            return Value.TRUE;
        }
        return compileValidator(match.schemas(), ctx);
    }

    /**
     * Combines the enforced declarations for the guarded subscription elements.
     *
     * @param schemas document schema declarations
     * @param ctx compilation context supplying constants and local schemas
     * @return the compiled guard, or true if the declarations are annotations only
     * @throws SaplCompilerException if an enforced schema cannot be prepared
     */
    public static CompiledExpression compileValidator(@NonNull List<SchemaStatement> schemas, CompilationContext ctx) {
        val enforced = schemas.stream().filter(SchemaStatement::enforced).toList();
        if (enforced.isEmpty()) {
            return Value.TRUE;
        }
        val registry = JsonSchemaPreparation.registry(ctx.getData().variables().get("schemas"));
        // Grouped in the order of the subscription elements, not in the iteration order of a
        // hash map over enum constants, which differs from one run to the next and would
        // change the semantic hash of the combined validator.
        val validatorsByElement = enforced.stream().collect(Collectors.groupingBy(SchemaStatement::element,
                () -> new EnumMap<>(SubscriptionElement.class),
                Collectors.mapping(schema -> compileSchemaValidator(schema, ctx, registry), Collectors.toList())));
        val elementValidators   = new ArrayList<PureOperator>();
        for (val validators : validatorsByElement.values()) {
            if (validators.size() == 1) {
                elementValidators.add(validators.getFirst());
            } else {
                elementValidators.add(new ElementSchemaValidator(validators));
            }
        }
        if (elementValidators.size() == 1) {
            return elementValidators.getFirst();
        }
        return new CombinedElementValidator(elementValidators);
    }

    private static PrecompiledSchemaValidator compileSchemaValidator(SchemaStatement schema, CompilationContext ctx,
            SchemaRegistry registry) {
        val compiledSchema = ExpressionCompiler.compile(schema.schema(), ctx);
        val location       = schema.location();
        return switch (compiledSchema) {
        case ErrorValue error           ->
            throw new SaplCompilerException(ERROR_SCHEMA_EVALUATION_FAILED.formatted(error.message()), location);
        case PureOperator ignored       -> throw new SaplCompilerException(ERROR_SCHEMA_MUST_BE_CONSTANT, location);
        case ObjectValue constantSchema ->
            createPrecompiledValidator(schema.element(), constantSchema, location, registry);
        default                         -> throw new SaplCompilerException(
                ERROR_SCHEMA_MUST_BE_OBJECT.formatted(compiledSchema.getClass().getSimpleName()), location);
        };
    }

    private static PrecompiledSchemaValidator createPrecompiledValidator(SubscriptionElement element,
            ObjectValue constantSchema, SourceLocation location, SchemaRegistry registry) {
        try {
            val precompiledSchema = JsonSchemaPreparation.prepare(registry, constantSchema);
            return new PrecompiledSchemaValidator(element, constantSchema, precompiledSchema, location);
        } catch (SchemaException e) {
            throw new SaplCompilerException(SchemaErrors.describe(e, ERROR_SCHEMA_INVALID_JSON_SCHEMA), e, location);
        }
    }

    record PrecompiledSchemaValidator(
            SubscriptionElement element,
            ObjectValue schemaSource,
            Schema schema,
            SourceLocation location) implements StructuredOperator {
        private static final long KIND = SemanticHashing.kindHash(PrecompiledSchemaValidator.class);

        @Override
        public OperandStructure structure() {
            return OperandStructure.of("schema", element.name().toLowerCase(Locale.ROOT), schemaSource);
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            val elementValue = getSubscriptionElement(ctx);
            if (elementValue instanceof ErrorValue) {
                return elementValue;
            }
            try {
                val subjectNode = ValueJsonMarshaller.toJsonNode(elementValue);
                val messages    = schema.validate(subjectNode);
                return messages.isEmpty() ? Value.TRUE : Value.FALSE;
            } catch (Throwable e) {
                // Third-party validation on hostile input must never crash evaluation. Surface
                // an ErrorValue.
                return Value.error(SchemaErrors.describe(e, ERROR_SCHEMA_VALIDATION_FAILED), location);
            }
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

        @Override
        public long semanticHash() {
            return SemanticHashing.ordered(KIND, SemanticHashing.kindHash(element),
                    SemanticHashing.valueHash(schemaSource));
        }
    }

    /**
     * OR semantics: at least one schema for the same subscription element must
     * pass.
     */
    record ElementSchemaValidator(List<PrecompiledSchemaValidator> validators) implements StructuredOperator {
        private static final long KIND = SemanticHashing.kindHash(ElementSchemaValidator.class);

        @Override
        public OperandStructure structure() {
            return new OperandStructure("schema:any", List.copyOf(validators));
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            // Kleene OR: a TRUE from any schema wins even if another errors. Surface an
            // error only if none pass.
            ErrorValue firstError = null;
            for (val validator : validators) {
                val result = validator.evaluate(ctx);
                if (Value.TRUE.equals(result)) {
                    return Value.TRUE;
                }
                if (result instanceof ErrorValue error && firstError == null) {
                    firstError = error;
                }
            }
            return firstError != null ? firstError : Value.FALSE;
        }

        @Override
        public SourceLocation location() {
            return validators.getFirst().location();
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }

        @Override
        public long semanticHash() {
            long hash = KIND;
            for (var v : validators) {
                hash = SemanticHashing.ordered(hash, v.semanticHash());
            }
            return hash;
        }
    }

    /**
     * AND semantics: all element validators must pass.
     */
    record CombinedElementValidator(List<PureOperator> elementValidators) implements StructuredOperator {
        private static final long KIND = SemanticHashing.kindHash(CombinedElementValidator.class);

        @Override
        public OperandStructure structure() {
            return new OperandStructure("schema:all", List.copyOf(elementValidators));
        }

        @Override
        public Value evaluate(EvaluationContext ctx) {
            for (val validator : elementValidators) {
                val result = validator.evaluate(ctx);
                if (result instanceof ErrorValue || Value.FALSE.equals(result)) {
                    return result;
                }
            }
            return Value.TRUE;
        }

        @Override
        public SourceLocation location() {
            return elementValidators.getFirst().location();
        }

        @Override
        public boolean isDependingOnSubscription() {
            return true;
        }

        @Override
        public long semanticHash() {
            long hash = KIND;
            for (var v : elementValidators) {
                hash = SemanticHashing.ordered(hash, v.semanticHash());
            }
            return hash;
        }
    }
}

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
package io.sapl.compiler.document;

import io.sapl.api.model.SourceLocation;
import io.sapl.ast.*;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Expanded source depth validation before expression compilation or folding. */
@UtilityClass
public class ExpressionDepthValidator {
    /** Default expanded expression depth, with leaves at depth one. */
    public static final int DEFAULT_MAX_EXPRESSION_DEPTH = 256;
    /** Portable hard ceiling. Configuration may lower this bound. */
    public static final int MAX_EXPRESSION_DEPTH         = 256;

    private static final String ERROR_INVALID_LIMIT = "The compiler option maxExpressionDepth must be an integer from 1 to %s.";

    /**
     * @param limit the configured depth limit
     * @throws IllegalArgumentException if the limit is outside the supported range
     */
    public static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_EXPRESSION_DEPTH) {
            throw new IllegalArgumentException(ERROR_INVALID_LIMIT.formatted(MAX_EXPRESSION_DEPTH));
        }
    }

    /**
     * @param document the generated AST
     * @param limit maximum expanded expression depth
     * @return the first depth diagnostic, or an empty list
     * @throws IllegalArgumentException if the limit is outside the supported range
     */
    public static List<Diagnostic> validateDocument(SaplDocument document, int limit) {
        return validate(document, limit);
    }

    /**
     * @param expression the standalone expression AST
     * @param limit maximum expanded expression depth
     * @throws SaplCompilerException if the expression exceeds the limit
     * @throws IllegalArgumentException if the limit is outside the supported range
     */
    public static void validateExpression(Expression expression, int limit) {
        val diagnostics = validate(expression, limit);
        if (!diagnostics.isEmpty()) {
            throw new SaplCompilerException(diagnostics.getFirst().message(), expression.location());
        }
    }

    private record Pending(AstNode node, int ancestors, SourceLocation expressionLocation) {}

    // An explicit stack also handles the recursive AST of a hostile flat source chain.
    private static List<Diagnostic> validate(AstNode root, int limit) {
        validateLimit(limit);
        val pending = new ArrayDeque<Pending>();
        pending.push(new Pending(root, 0, root.location()));
        while (!pending.isEmpty()) {
            val current            = pending.pop();
            val node               = current.node();
            val depth              = current.ancestors();
            val expressionLocation = node instanceof Expression && depth == 0 ? node.location()
                    : current.expressionLocation();
            if (node instanceof Expression && depth + 1 > limit) {
                return List.of(problem(expressionLocation, limit));
            }
            enqueueChildren(node, depth, expressionLocation, pending);
        }
        return List.of();
    }

    private static Diagnostic problem(SourceLocation location, int limit) {
        val region = new Region(location.start(), location.end(), location.line(), location.column(),
                location.endLine(), location.endColumn());
        return new Diagnostic(Problem.EXPRESSION_TOO_DEEP, List.of(String.valueOf(limit)), region, List.of(), null);
    }

    private static void enqueueChildren(AstNode node, int depth, SourceLocation expressionLocation,
            Deque<Pending> pending) {
        switch (node) {
        case Expression expression                                                                     ->
            enqueueExpression(expression, depth, expressionLocation, pending);
        case Policy policy                                                                             -> {
            pending.push(new Pending(policy.body(), 0, expressionLocation));
            enqueue(policy.obligations(), 0, expressionLocation, pending);
            enqueue(policy.advice(), 0, expressionLocation, pending);
            val transformation = policy.transformation();
            if (transformation != null) {
                pending.push(new Pending(transformation, 0, expressionLocation));
            }
        }
        case PolicySet set                                                                             -> {
            val target = set.target();
            val match  = set.match();
            if (target != null) {
                pending.push(new Pending(target, 0, expressionLocation));
            }
            if (match != null) {
                pending.push(new Pending(match, 0, expressionLocation));
            }
            enqueue(set.variables(), 0, expressionLocation, pending);
            enqueue(set.policies(), 0, expressionLocation, pending);
        }
        case PolicyBody(var statements, var ignoredLocation)                                           ->
            enqueue(statements, 0, expressionLocation, pending);
        case Condition(var expression, var ignoredLocation)                                            ->
            pending.push(new Pending(expression, 0, expressionLocation));
        case VarDef(var ignoredName, var value, var schemas, var ignoredLocation)                      -> {
            pending.push(new Pending(value, 0, expressionLocation));
            enqueue(schemas, 0, expressionLocation, pending);
        }
        case SchemaCondition(var schemas, var ignoredLocation)                                         ->
            enqueue(schemas, 0, expressionLocation, pending);
        case SchemaStatement(var ignoredElement, var ignoredEnforced, var schema, var ignoredLocation) ->
            pending.push(new Pending(schema, 0, expressionLocation));
        case ExpressionPath(var expression, var ignoredLocation)                                       ->
            pending.push(new Pending(expression, depth, expressionLocation));
        case ConditionPath(var condition, var ignoredLocation)                                         ->
            pending.push(new Pending(condition, depth, expressionLocation));
        default                                                                                        -> {}
        }
    }

    private static void enqueueExpression(Expression expression, int depth, SourceLocation expressionLocation,
            Deque<Pending> pending) {
        switch (expression) {
        case BinaryOperator(var ignoredOperator, var left, var right, var ignoredLocation)                       -> {
            pending.push(new Pending(left, depth + 1, expressionLocation));
            pending.push(new Pending(right, depth + 1, expressionLocation));
        }
        case UnaryOperator(var ignoredOperator, var operand, var ignoredLocation)                                ->
            pending.push(new Pending(operand, depth + 1, expressionLocation));
        case FunctionCall(var ignoredName, var arguments, var ignoredLocation)                                   ->
            enqueue(arguments, depth + 1, expressionLocation, pending);
        case EnvironmentAttribute attribute                                                                      -> {
            enqueue(attribute.arguments(), depth + 1, expressionLocation, pending);
            val options = attribute.options();
            if (options != null) {
                pending.push(new Pending(options, depth + 1, expressionLocation));
            }
        }
        case SimpleFilter(var base, var ignoredName, var arguments, var each, var ignoredLocation)               -> {
            val next = depth + 1 + (each ? 1 : 0);
            pending.push(new Pending(base, next, expressionLocation));
            enqueue(arguments, next, expressionLocation, pending);
        }
        case ExtendedFilter(var base, var target, var ignoredName, var arguments, var each, var ignoredLocation) -> {
            val next = depth + 2 + target.elements().size() + (each ? 1 : 0);
            pending.push(new Pending(base, next, expressionLocation));
            enqueue(arguments, next, expressionLocation, pending);
            enqueue(target.elements(), next, expressionLocation, pending);
        }
        case Step step                                                                                           ->
            enqueueStep(step, depth, expressionLocation, pending);
        default                                                                                                  ->
            enqueueCollection(expression, depth, expressionLocation, pending);
        }
    }

    private static void enqueueCollection(Expression expression, int depth, SourceLocation expressionLocation,
            Deque<Pending> pending) {
        switch (expression) {
        case Conjunction(var operands, var ignoredEager, var ignoredLocation) ->
            enqueue(operands, depth + 1, expressionLocation, pending);
        case Disjunction(var operands, var ignoredEager, var ignoredLocation) ->
            enqueue(operands, depth + 1, expressionLocation, pending);
        case ExclusiveDisjunction(var operands, var ignoredLocation)          ->
            enqueue(operands, depth + 1, expressionLocation, pending);
        case Product(var operands, var ignoredLocation)                       ->
            enqueue(operands, depth + 1, expressionLocation, pending);
        case ArrayExpression(var elements, var ignoredLocation)               ->
            enqueue(elements, depth + 1, expressionLocation, pending);
        case ObjectExpression(var entries, var ignoredLocation)               -> {
            for (val entry : entries) {
                pending.push(new Pending(entry.value(), depth + 1, expressionLocation));
            }
        }
        default                                                               -> {}
        }
    }

    private static void enqueueStep(Step step, int depth, SourceLocation expressionLocation, Deque<Pending> pending) {
        pending.push(new Pending(step.base(), depth + 1, expressionLocation));
        switch (step) {
        case ExpressionStep(var ignoredBase, var expression, var ignoredLocation) ->
            pending.push(new Pending(expression, depth + 1, expressionLocation));
        case ConditionStep(var ignoredBase, var condition, var ignoredLocation)   ->
            pending.push(new Pending(condition, depth + 1, expressionLocation));
        case AttributeStep attribute                                              -> {
            enqueue(attribute.arguments(), depth + 2, expressionLocation, pending);
            val options = attribute.options();
            if (options != null) {
                pending.push(new Pending(options, depth + 2, expressionLocation));
            }
        }
        default                                                                   -> {}
        }
    }

    private static void enqueue(List<? extends AstNode> nodes, int depth, SourceLocation expressionLocation,
            Deque<Pending> pending) {
        for (val node : nodes) {
            pending.push(new Pending(node, depth, expressionLocation));
        }
    }
}

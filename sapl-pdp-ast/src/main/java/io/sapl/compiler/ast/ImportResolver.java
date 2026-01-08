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
package io.sapl.compiler.ast;

import io.sapl.api.model.SourceLocation;
import io.sapl.ast.*;
import io.sapl.compiler.SaplCompilerException;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves import references in the AST, transforming short names to fully
 * qualified names.
 * <p>
 * This is a separate pass after AST transformation that:
 * <ul>
 * <li>Builds a lookup map from imports</li>
 * <li>Walks the AST and resolves all QualifiedName references</li>
 * <li>Returns a new AST with all names fully qualified</li>
 * </ul>
 */
@UtilityClass
public class ImportResolver {

    private static final String ERROR_IMPORT_CONFLICT        = "Import conflict: '%s' already imported as '%s' from '%s'.";
    private static final String ERROR_INVALID_QUALIFIED_NAME = "Invalid qualified name '%s': too many segments (max: library.function).";
    private static final String ERROR_UNRESOLVED_REFERENCE   = "Unresolved reference '%s': not imported and not fully qualified.";

    /**
     * Resolves all import references in the document.
     *
     * @param document the document to resolve
     * @return a new document with all resolvable references fully qualified
     */
    public SaplDocument resolve(SaplDocument document) {
        var importMap = buildImportMap(document.imports());
        var schemas   = document.schemas().stream().map(s -> resolveSchema(s, importMap)).toList();
        var element   = resolveElement(document.element(), importMap);
        return new SaplDocument(document.imports(), schemas, element, document.location());
    }

    private SchemaStatement resolveSchema(SchemaStatement schema, Map<String, List<String>> importMap) {
        return new SchemaStatement(schema.element(), schema.enforced(), resolveExpr(schema.schema(), importMap),
                schema.location());
    }

    private Map<String, List<String>> buildImportMap(List<Import> imports) {
        var map        = new HashMap<String, List<String>>();
        var importedBy = new HashMap<String, Import>(); // Track which import introduced each name
        for (var imp : imports) {
            var shortName = imp.effectiveName();
            var existing  = importedBy.get(shortName);
            if (existing != null) {
                throw new SaplCompilerException(
                        ERROR_IMPORT_CONFLICT.formatted(shortName, existing.fullName(), imp.fullName()),
                        imp.location());
            }
            var fullPath = new ArrayList<>(imp.libraryPath());
            fullPath.add(imp.functionName());
            map.put(shortName, fullPath);
            importedBy.put(shortName, imp);
        }
        return map;
    }

    private QualifiedName resolve(QualifiedName name, Map<String, List<String>> importMap, SourceLocation location) {
        var parts = name.parts();
        if (parts.size() > 2) {
            throw new SaplCompilerException(ERROR_INVALID_QUALIFIED_NAME.formatted(String.join(".", parts)), location);
        }
        if (parts.size() == 2) {
            return name; // Already fully qualified
        }
        // Single-part name: attempt resolution via imports
        var resolved = importMap.get(parts.getFirst());
        if (resolved != null) {
            return new QualifiedName(resolved);
        }
        throw new SaplCompilerException(ERROR_UNRESOLVED_REFERENCE.formatted(parts.getFirst()), location);
    }

    private PolicyElement resolveElement(PolicyElement element, Map<String, List<String>> importMap) {
        return switch (element) {
        case Policy(var name, var entitlement, var target, var body, var obligations, var advice, var transformation, var location) ->
            new Policy(name, entitlement, target != null ? resolveExpr(target, importMap) : null,
                    body.stream().map(s -> resolveStatement(s, importMap)).toList(),
                    obligations.stream().map(e -> resolveExpr(e, importMap)).toList(),
                    advice.stream().map(e -> resolveExpr(e, importMap)).toList(),
                    transformation != null ? resolveExpr(transformation, importMap) : null, location);
        case PolicySet(var name, var algorithm, var target, var variables, var policies, var location)                              ->
            new PolicySet(name, algorithm, target != null ? resolveExpr(target, importMap) : null,
                    variables.stream().map(v -> resolveVarDef(v, importMap)).toList(),
                    policies.stream().map(p -> resolvePolicy(p, importMap)).toList(), location);
        };
    }

    private Policy resolvePolicy(Policy policy, Map<String, List<String>> importMap) {
        return switch (policy) {
        case Policy(var name, var entitlement, var target, var body, var obligations, var advice, var transformation, var location) ->
            new Policy(name, entitlement, target != null ? resolveExpr(target, importMap) : null,
                    body.stream().map(s -> resolveStatement(s, importMap)).toList(),
                    obligations.stream().map(e -> resolveExpr(e, importMap)).toList(),
                    advice.stream().map(e -> resolveExpr(e, importMap)).toList(),
                    transformation != null ? resolveExpr(transformation, importMap) : null, location);
        };
    }

    private Statement resolveStatement(Statement stmt, Map<String, List<String>> importMap) {
        return switch (stmt) {
        case VarDef v                                -> resolveVarDef(v, importMap);
        case Condition(var expression, var location) -> new Condition(resolveExpr(expression, importMap), location);
        };
    }

    private VarDef resolveVarDef(VarDef varDef, Map<String, List<String>> importMap) {
        return switch (varDef) {
        case VarDef(var name, var value, var schemas, var location) -> new VarDef(name, resolveExpr(value, importMap),
                schemas.stream().map(e -> resolveExpr(e, importMap)).toList(), location);
        };
    }

    private Expression resolveExpr(Expression expr, Map<String, List<String>> importMap) {
        return switch (expr) {
        case Literal l                                                                             -> l;
        case Identifier id                                                                         -> id; // No steps to
                                                                                                          // resolve in
                                                                                                          // new AST
        case RelativeReference r                                                                   -> r; // No nested
                                                                                                         // expressions
        case BinaryOperator(var op, var left, var right, var location)                             ->
            new BinaryOperator(op, resolveExpr(left, importMap), resolveExpr(right, importMap), location);
        case UnaryOperator(var op, var operand, var location)                                      ->
            new UnaryOperator(op, resolveExpr(operand, importMap), location);
        case FunctionCall(var name, var arguments, var location)                                   ->
            new FunctionCall(resolve(name, importMap, location),
                    arguments.stream().map(a -> resolveExpr(a, importMap)).toList(), location);
        case EnvironmentAttribute(var name, var arguments, var options, var head, var location)    ->
            new EnvironmentAttribute(resolve(name, importMap, location),
                    arguments.stream().map(a -> resolveExpr(a, importMap)).toList(),
                    options != null ? resolveExpr(options, importMap) : null, head, location);
        case Parenthesized(var expression, var location)                                           ->
            new Parenthesized(resolveExpr(expression, importMap), location);
        case SimpleFilter(var base, var name, var arguments, var each, var location)               ->
            new SimpleFilter(resolveExpr(base, importMap), resolve(name, importMap, location),
                    arguments.stream().map(a -> resolveExpr(a, importMap)).toList(), each, location);
        case ExtendedFilter(var base, var target, var name, var arguments, var each, var location) ->
            new ExtendedFilter(resolveExpr(base, importMap), resolveFilterPath(target, importMap),
                    resolve(name, importMap, location), arguments.stream().map(a -> resolveExpr(a, importMap)).toList(),
                    each, location);
        case ArrayExpression(var elements, var location)                                           ->
            new ArrayExpression(elements.stream().map(e -> resolveExpr(e, importMap)).toList(), location);
        case ObjectExpression(var entries, var location)                                           ->
            new ObjectExpression(entries.stream()
                    .map(e -> new ObjectEntry(e.key(), resolveExpr(e.value(), importMap), e.location())).toList(),
                    location);
        // N-ary operations
        case Conjunction(var operands, var location)          ->
            new Conjunction(operands.stream().map(o -> resolveExpr(o, importMap)).toList(), location);
        case Disjunction(var operands, var location)          ->
            new Disjunction(operands.stream().map(o -> resolveExpr(o, importMap)).toList(), location);
        case Sum(var operands, var location)                  ->
            new Sum(operands.stream().map(o -> resolveExpr(o, importMap)).toList(), location);
        case Product(var operands, var location)              ->
            new Product(operands.stream().map(o -> resolveExpr(o, importMap)).toList(), location);
        case ExclusiveDisjunction(var operands, var location) ->
            new ExclusiveDisjunction(operands.stream().map(o -> resolveExpr(o, importMap)).toList(), location);
        // Steps - each has a base expression to recurse into
        case Step step -> resolveStep(step, importMap);
        };
    }

    private Step resolveStep(Step step, Map<String, List<String>> importMap) {
        return switch (step) {
        case KeyStep(var base, var key, var location)                                              ->
            new KeyStep(resolveExpr(base, importMap), key, location);
        case IndexStep(var base, var index, var location)                                          ->
            new IndexStep(resolveExpr(base, importMap), index, location);
        case WildcardStep(var base, var location)                                                  ->
            new WildcardStep(resolveExpr(base, importMap), location);
        case SliceStep(var base, var from, var to, var stepVal, var location)                      ->
            new SliceStep(resolveExpr(base, importMap), from, to, stepVal, location);
        case ExpressionStep(var base, var expression, var location)                                ->
            new ExpressionStep(resolveExpr(base, importMap), resolveExpr(expression, importMap), location);
        case ConditionStep(var base, var condition, var location)                                  ->
            new ConditionStep(resolveExpr(base, importMap), resolveExpr(condition, importMap), location);
        case RecursiveKeyStep(var base, var key, var location)                                     ->
            new RecursiveKeyStep(resolveExpr(base, importMap), key, location);
        case RecursiveWildcardStep(var base, var location)                                         ->
            new RecursiveWildcardStep(resolveExpr(base, importMap), location);
        case RecursiveIndexStep(var base, var index, var location)                                 ->
            new RecursiveIndexStep(resolveExpr(base, importMap), index, location);
        case AttributeStep(var base, var name, var arguments, var options, var head, var location) ->
            new AttributeStep(resolveExpr(base, importMap), resolve(name, importMap, location),
                    arguments.stream().map(a -> resolveExpr(a, importMap)).toList(),
                    options != null ? resolveExpr(options, importMap) : null, head, location);
        case IndexUnionStep(var base, var indices, var location)                                   ->
            new IndexUnionStep(resolveExpr(base, importMap), indices, location);
        case AttributeUnionStep(var base, var attributes, var location)                            ->
            new AttributeUnionStep(resolveExpr(base, importMap), attributes, location);
        };
    }

    private FilterPath resolveFilterPath(FilterPath path, Map<String, List<String>> importMap) {
        return switch (path) {
        case FilterPath(var elements, var location) ->
            new FilterPath(elements.stream().map(e -> resolvePathElement(e, importMap)).toList(), location);
        };
    }

    private PathElement resolvePathElement(PathElement element, Map<String, List<String>> importMap) {
        // Most path elements don't contain expressions that need resolution
        return switch (element) {
        case ExpressionPath(var expression, var location) ->
            new ExpressionPath(resolveExpr(expression, importMap), location);
        case ConditionPath(var condition, var location)   ->
            new ConditionPath(resolveExpr(condition, importMap), location);
        default                                           -> element; // KeyPath, IndexPath, WildcardPath, etc. have no
                                                                      // expressions
        };
    }
}

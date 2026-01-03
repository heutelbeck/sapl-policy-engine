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
package io.sapl.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.SourceLocation;
import io.sapl.ast.*;
import io.sapl.compiler.SaplCompilerException;
import lombok.experimental.UtilityClass;

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

    // ═══════════════════════════════════════════════════════════════════
    // POLICY ELEMENTS
    // ═══════════════════════════════════════════════════════════════════

    private PolicyElement resolveElement(PolicyElement element, Map<String, List<String>> importMap) {
        return switch (element) {
        case Policy p     -> resolvePolicy(p, importMap);
        case PolicySet ps -> resolvePolicySet(ps, importMap);
        };
    }

    private Policy resolvePolicy(Policy p, Map<String, List<String>> importMap) {
        return new Policy(p.name(), p.entitlement(), p.target() != null ? resolveExpr(p.target(), importMap) : null,
                p.body().stream().map(s -> resolveStatement(s, importMap)).toList(),
                p.obligations().stream().map(e -> resolveExpr(e, importMap)).toList(),
                p.advice().stream().map(e -> resolveExpr(e, importMap)).toList(),
                p.transformation() != null ? resolveExpr(p.transformation(), importMap) : null, p.location());
    }

    private PolicySet resolvePolicySet(PolicySet ps, Map<String, List<String>> importMap) {
        return new PolicySet(ps.name(), ps.algorithm(),
                ps.target() != null ? resolveExpr(ps.target(), importMap) : null,
                ps.variables().stream().map(v -> resolveVarDef(v, importMap)).toList(),
                ps.policies().stream().map(p -> resolvePolicy(p, importMap)).toList(), ps.location());
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATEMENTS
    // ═══════════════════════════════════════════════════════════════════

    private Statement resolveStatement(Statement stmt, Map<String, List<String>> importMap) {
        return switch (stmt) {
        case VarDef v    -> resolveVarDef(v, importMap);
        case Condition c -> new Condition(resolveExpr(c.expression(), importMap), c.location());
        };
    }

    private VarDef resolveVarDef(VarDef v, Map<String, List<String>> importMap) {
        return new VarDef(v.name(), resolveExpr(v.value(), importMap),
                v.schemas().stream().map(e -> resolveExpr(e, importMap)).toList(), v.location());
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXPRESSIONS
    // ═══════════════════════════════════════════════════════════════════

    private Expression resolveExpr(Expression expr, Map<String, List<String>> importMap) {
        return switch (expr) {
        case Literal l               -> l;
        case Identifier id           -> id; // No steps to resolve in new AST
        case BinaryOperation b       -> new BinaryOperation(b.op(), resolveExpr(b.left(), importMap),
                resolveExpr(b.right(), importMap), b.nature(), b.location());
        case UnaryOperation u        ->
            new UnaryOperation(u.op(), resolveExpr(u.operand(), importMap), u.nature(), u.location());
        case FunctionCall fc         -> new FunctionCall(resolve(fc.name(), importMap, fc.location()),
                fc.arguments().stream().map(a -> resolveExpr(a, importMap)).toList(), fc.nature(), fc.location());
        case EnvironmentAttribute aa -> new EnvironmentAttribute(resolve(aa.name(), importMap, aa.location()),
                aa.arguments().stream().map(a -> resolveExpr(a, importMap)).toList(),
                aa.options() != null ? resolveExpr(aa.options(), importMap) : null, aa.head(), aa.location());
        case RelativeReference r     -> r; // No nested expressions
        case Parenthesized g         -> new Parenthesized(resolveExpr(g.expression(), importMap), g.location());
        case FilterOperation fo      -> resolveFilterOperation(fo, importMap);
        case ArrayExpression ae      -> new ArrayExpression(
                ae.elements().stream().map(e -> resolveExpr(e, importMap)).toList(), ae.nature(), ae.location());
        case ObjectExpression oe     -> new ObjectExpression(
                oe.entries().stream()
                        .map(e -> new ObjectEntry(e.key(), resolveExpr(e.value(), importMap), e.location())).toList(),
                oe.nature(), oe.location());
        // N-ary operations
        case Conjunction c           -> new Conjunction(
                c.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), c.nature(), c.location());
        case Disjunction d           -> new Disjunction(
                d.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), d.nature(), d.location());
        case Sum s                   ->
            new Sum(s.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), s.nature(), s.location());
        case Product p               ->
            new Product(p.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), p.nature(), p.location());
        case EagerConjunction ec     -> new EagerConjunction(
                ec.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), ec.nature(), ec.location());
        case EagerDisjunction ed     -> new EagerDisjunction(
                ed.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), ed.nature(), ed.location());
        case ExclusiveDisjunction xd -> new ExclusiveDisjunction(
                xd.operands().stream().map(o -> resolveExpr(o, importMap)).toList(), xd.nature(), xd.location());
        // Steps - each has a base expression to recurse into
        case Step step -> resolveStep(step, importMap);
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // STEPS
    // ═══════════════════════════════════════════════════════════════════

    private Step resolveStep(Step step, Map<String, List<String>> importMap) {
        return switch (step) {
        case KeyStep ks                -> new KeyStep(resolveExpr(ks.base(), importMap), ks.key(), ks.location());
        case IndexStep is              -> new IndexStep(resolveExpr(is.base(), importMap), is.index(), is.location());
        case WildcardStep ws           -> new WildcardStep(resolveExpr(ws.base(), importMap), ws.location());
        case SliceStep ss              ->
            new SliceStep(resolveExpr(ss.base(), importMap), ss.from(), ss.to(), ss.step(), ss.location());
        case ExpressionStep es         -> new ExpressionStep(resolveExpr(es.base(), importMap),
                resolveExpr(es.expression(), importMap), es.location());
        case ConditionStep cs          ->
            new ConditionStep(resolveExpr(cs.base(), importMap), resolveExpr(cs.condition(), importMap), cs.location());
        case RecursiveKeyStep rks      ->
            new RecursiveKeyStep(resolveExpr(rks.base(), importMap), rks.key(), rks.location());
        case RecursiveWildcardStep rws -> new RecursiveWildcardStep(resolveExpr(rws.base(), importMap), rws.location());
        case RecursiveIndexStep ris    ->
            new RecursiveIndexStep(resolveExpr(ris.base(), importMap), ris.index(), ris.location());
        case AttributeStep afs         ->
            new AttributeStep(resolveExpr(afs.base(), importMap), resolve(afs.name(), importMap, afs.location()),
                    afs.arguments().stream().map(a -> resolveExpr(a, importMap)).toList(),
                    afs.options() != null ? resolveExpr(afs.options(), importMap) : null, afs.head(), afs.location());
        case IndexUnionStep ius        ->
            new IndexUnionStep(resolveExpr(ius.base(), importMap), ius.indices(), ius.location());
        case AttributeUnionStep aus    ->
            new AttributeUnionStep(resolveExpr(aus.base(), importMap), aus.attributes(), aus.location());
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // FILTER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════

    private FilterOperation resolveFilterOperation(FilterOperation fo, Map<String, List<String>> importMap) {
        return new FilterOperation(resolveExpr(fo.base(), importMap),
                fo.target() != null ? resolveFilterPath(fo.target(), importMap) : null,
                resolve(fo.function(), importMap, fo.location()),
                fo.arguments().stream().map(a -> resolveExpr(a, importMap)).toList(), fo.each(), fo.nature(),
                fo.location());
    }

    private FilterPath resolveFilterPath(FilterPath path, Map<String, List<String>> importMap) {
        return new FilterPath(path.elements().stream().map(e -> resolvePathElement(e, importMap)).toList(),
                path.location());
    }

    private PathElement resolvePathElement(PathElement element, Map<String, List<String>> importMap) {
        // Most path elements don't contain expressions that need resolution
        return switch (element) {
        case ExpressionPath ep -> new ExpressionPath(resolveExpr(ep.expression(), importMap), ep.location());
        case ConditionPath cp  -> new ConditionPath(resolveExpr(cp.condition(), importMap), cp.location());
        default                -> element; // KeyPath, IndexPath, WildcardPath, etc. have no expressions
        };
    }
}

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

import io.sapl.api.model.DocumentLines;
import io.sapl.api.model.NumberValueLimits;
import io.sapl.api.model.ReservedIdentifiers;
import io.sapl.ast.Import;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.FurtherRegion;
import io.sapl.compiler.diagnostics.FurtherRegion.Label;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.util.SourceLocationUtil;
import io.sapl.grammar.antlr.SAPLParser.AttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.FilterStatementContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.ImportStatementContext;
import io.sapl.grammar.antlr.SAPLParser.NumberLiteralContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.SchemaStatementContext;
import io.sapl.grammar.antlr.SAPLParser.SignedNumberContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import lombok.val;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The rules of the language that the grammar cannot express, checked on the
 * parse tree. Every occurrence is reported. A construct that the parser could
 * not read completely is not checked: its mistake is reported already.
 * <p>
 * The rules are the predicates of {@link AstTransformer}, which refuses the
 * first occurrence of each for a document that is transformed without this
 * check.
 */
final class RuleChecks {

    private final DocumentLines           lines;
    private final Map<String, ImportedAs> imports  = new HashMap<>();
    private final List<Diagnostic>        problems = new ArrayList<>();

    /**
     * An import by its short name.
     *
     * @param imported the import
     * @param region the region of the imported name
     */
    private record ImportedAs(Import imported, Region region) {}

    private RuleChecks(DocumentLines lines) {
        this.lines = lines;
    }

    /**
     * @param document the parse tree of a document
     * @param lines the lines of the document
     * @return the problems of the rules, in the order of the document
     */
    static List<Diagnostic> check(SaplContext document, DocumentLines lines) {
        val checks = new RuleChecks(lines);
        // Imports precede everything that can use them.
        document.importStatement().forEach(checks::importStatement);
        walk(document, tree -> {
            checks.checkConstruct(tree);
            return true;
        });
        return checks.problems;
    }

    private void checkConstruct(ParseTree tree) {
        if (!(tree instanceof ParserRuleContext construct) || !isComplete(construct)) {
            return;
        }
        switch (construct) {
        case FunctionIdentifierContext name -> resolve(name);
        case NumberLiteralContext number    -> number(number);
        case SignedNumberContext index      -> index(index);
        case FilterStatementContext filter  -> filterTarget(filter);
        case SchemaStatementContext schema  -> schema(schema.schemaExpression);
        case ValueDefinitionContext value   -> {
            variableName(value);
            value.schemaVarExpression.forEach(this::schema);
        }
        default                             -> {
            // Every other construct is free of rules beyond the grammar.
        }
        }
    }

    /**
     * The elements of the authorization subscription can be read like variables,
     * but a policy must not define a variable that takes one of their names.
     */
    private void variableName(ValueDefinitionContext value) {
        if (value.name == null) {
            return;
        }
        val name = AstTransformer.stripBackticks(value.name.getText());
        if (ReservedIdentifiers.SUBSCRIPTION_IDENTIFIERS.contains(name)) {
            problems.add(problem(Problem.RESERVED_VARIABLE_NAME, value.name, name));
        }
    }

    /**
     * Two imports must not share a short name.
     */
    private void importStatement(ImportStatementContext statement) {
        if (!isComplete(statement) || statement.functionName == null) {
            return;
        }
        // The checks need the names an import introduces, not the name of its document.
        val imported  = AstTransformer.importOf(statement, SourceLocationUtil.fromContext(statement, null));
        val shortName = imported.effectiveName();
        val region    = Region.of(lines, Tokens.startOf(statement.libSteps.getFirst().getStart(), lines),
                Tokens.endOf(statement.functionName.getStop(), lines));
        val existing  = imports.get(shortName);
        if (existing == null) {
            imports.put(shortName, new ImportedAs(imported, region));
            return;
        }
        val firstImported = List.of(new FurtherRegion(existing.region(), Label.FIRST_IMPORTED_HERE));
        val name          = imported.fullName();
        val alias         = aliasFor(imported);
        val origin        = Tokens.quotable(existing.imported().fullName());
        if (origin.isEmpty()) {
            problems.add(new Diagnostic(Problem.IMPORT_CONFLICT_WITHOUT_ORIGIN, List.of(shortName, name, alias), region,
                    firstImported, null));
        } else {
            problems.add(new Diagnostic(Problem.IMPORT_CONFLICT, List.of(shortName, origin, name, alias), region,
                    firstImported, null));
        }
    }

    /**
     * The name of a function or attribute finder is fully qualified with two
     * segments, or is the short name of an import.
     */
    private void resolve(FunctionIdentifierContext name) {
        val segments = AstTransformer.segmentsOf(name);
        val written  = String.join(".", segments);
        if (AstTransformer.isTooLongName(segments)) {
            problems.add(problem(Problem.NAME_TOO_LONG, name, written, Problem.countInWords(segments.size())));
        } else if (!AstTransformer.isFullyQualified(segments) && !imports.containsKey(written)) {
            problems.add(problem(Problem.NAME_NOT_IMPORTED, name, written));
        }
    }

    private void number(NumberLiteralContext number) {
        val literal = number.getText();
        if (AstTransformer.isTooLongNumber(literal)) {
            problems.add(problem(Problem.NUMBER_TOO_LONG, number, String.valueOf(literal.length()),
                    String.valueOf(AstTransformer.MAX_NUMBER_LITERAL_LENGTH)));
        } else if (AstTransformer.numberValueOf(literal) == null) {
            problems.add(
                    problem(Problem.NUMBER_OUT_OF_RANGE, number, literal, String.valueOf(NumberValueLimits.MAX_SCALE)));
        }
    }

    private void index(SignedNumberContext index) {
        val literal = index.getText();
        if (AstTransformer.indexOf(literal).isPresent()) {
            return;
        }
        val digits = index.NUMBER().getText();
        if (digits.chars().allMatch(digit -> digit >= '0' && digit <= '9')) {
            problems.add(problem(Problem.INDEX_TOO_LARGE, index, literal, String.valueOf(Integer.MIN_VALUE),
                    String.valueOf(Integer.MAX_VALUE)));
        } else {
            problems.add(problem(Problem.INDEX_NOT_WHOLE, index, literal));
        }
    }

    /**
     * The first attribute finder that is a step of the target of a filter is
     * reported. An attribute finder inside an expression or a condition of a
     * step is no step of the target.
     */
    private void filterTarget(FilterStatementContext filter) {
        if (filter.target == null) {
            return;
        }
        filter.target.step().stream().map(AstTransformer::attributeFinderOf).filter(Objects::nonNull).findFirst()
                .ifPresent(finder -> problems.add(problem(Problem.ATTRIBUTE_FINDER_IN_FILTER_TARGET, finder)));
    }

    /**
     * Schemas are evaluated without access to policy information points, so an
     * attribute finder in a schema could never be resolved. The first one is
     * reported.
     */
    private void schema(@Nullable ExpressionContext schema) {
        if (schema == null) {
            return;
        }
        walk(schema, tree -> {
            val found = regionOfAttributeFinder(tree);
            if (found != null) {
                problems.add(new Diagnostic(Problem.ATTRIBUTE_FINDER_IN_SCHEMA, List.of(), found, List.of(), null));
            }
            return found == null;
        });
    }

    /**
     * @return the region of an attribute finder that a construct is, without the
     * steps applied to it, or null if it is none
     */
    private @Nullable Region regionOfAttributeFinder(ParseTree tree) {
        return switch (tree) {
        case BasicEnvironmentAttributeContext finder when finder.GT() != null     ->
            Tokens.regionOf(finder.LT().getSymbol(), finder.GT().getSymbol(), lines);
        case BasicEnvironmentHeadAttributeContext finder when finder.GT() != null ->
            Tokens.regionOf(finder.PIPE_LT().getSymbol(), finder.GT().getSymbol(), lines);
        case AttributeFinderStepContext finder when finder.getStop() != null      -> regionOf(finder);
        case HeadAttributeFinderStepContext finder when finder.getStop() != null  -> regionOf(finder);
        default                                                                   -> null;
        };
    }

    private Region regionOf(ParserRuleContext construct) {
        return Tokens.regionOf(construct.getStart(), construct.getStop(), lines);
    }

    private Diagnostic problem(Problem problem, ParserRuleContext construct, String... values) {
        return new Diagnostic(problem, List.of(values), regionOf(construct), List.of(), null);
    }

    /**
     * Visits a parse tree and the trees below it in the order of the document,
     * until a visit asks to stop.
     *
     * @param root the parse tree
     * @param visit called for each tree, returns whether to go on
     */
    private static void walk(ParseTree root, Predicate<ParseTree> visit) {
        val pending = new ArrayDeque<ParseTree>();
        pending.push(root);
        while (!pending.isEmpty()) {
            val tree = pending.pop();
            if (!visit.test(tree)) {
                return;
            }
            for (var child = tree.getChildCount() - 1; child >= 0; child--) {
                pending.push(tree.getChild(child));
            }
        }
    }

    /**
     * Whether the parser read a construct completely: it raised no error in it
     * and inserted no token that is missing.
     *
     * @param construct a construct of the parse tree
     * @return whether its start and its end are tokens of the document, in this
     * order, and none of its parts is an error
     */
    static boolean isComplete(ParserRuleContext construct) {
        if (construct.exception != null || construct.getStart() == null || construct.getStop() == null
                || construct.getStop().getTokenIndex() < construct.getStart().getTokenIndex()) {
            return false;
        }
        for (var child = 0; child < construct.getChildCount(); child++) {
            if (construct.getChild(child) instanceof ErrorNode) {
                return false;
            }
        }
        return true;
    }

    /**
     * An alias that tells two imports of the same short name apart: the name
     * prefixed by its library, such as {@code clockNow} for {@code clock.now}.
     */
    private static String aliasFor(Import imported) {
        val name    = imported.functionName();
        val library = imported.libraryPath().isEmpty() ? "" : imported.libraryPath().getLast();
        val first   = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        return library + first + name.substring(first.isEmpty() ? 0 : name.offsetByCodePoints(0, 1));
    }

}

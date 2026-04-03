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
package io.sapl.lsp.sapl;

import static io.sapl.lsp.core.FormattingConstants.INDENT;
import static io.sapl.lsp.core.FormattingConstants.LINE_WIDTH;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser.AdditionContext;
import io.sapl.grammar.antlr.SAPLParser.ArgumentsContext;
import io.sapl.grammar.antlr.SAPLParser.ArrayContext;
import io.sapl.grammar.antlr.SAPLParser.ArraySlicingStepContext;
import io.sapl.grammar.antlr.SAPLParser.AttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicEnvironmentHeadAttributeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.BasicFunctionContext;
import io.sapl.grammar.antlr.SAPLParser.BasicGroupContext;
import io.sapl.grammar.antlr.SAPLParser.BasicIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.BasicRelativeContext;
import io.sapl.grammar.antlr.SAPLParser.BasicRelativeLocationContext;
import io.sapl.grammar.antlr.SAPLParser.BasicValueContext;
import io.sapl.grammar.antlr.SAPLParser.CombiningAlgorithmContext;
import io.sapl.grammar.antlr.SAPLParser.ComparisonContext;
import io.sapl.grammar.antlr.SAPLParser.ConditionStepContext;
import io.sapl.grammar.antlr.SAPLParser.EagerAndContext;
import io.sapl.grammar.antlr.SAPLParser.EagerOrContext;
import io.sapl.grammar.antlr.SAPLParser.EqualityContext;
import io.sapl.grammar.antlr.SAPLParser.HasExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ExclusiveOrContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionStepContext;
import io.sapl.grammar.antlr.SAPLParser.FilterExtendedContext;
import io.sapl.grammar.antlr.SAPLParser.FilterSimpleContext;
import io.sapl.grammar.antlr.SAPLParser.FilterStatementContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderStepContext;
import io.sapl.grammar.antlr.SAPLParser.ImportStatementContext;
import io.sapl.grammar.antlr.SAPLParser.IndexUnionStepContext;
import io.sapl.grammar.antlr.SAPLParser.LazyAndContext;
import io.sapl.grammar.antlr.SAPLParser.LazyOrContext;
import io.sapl.grammar.antlr.SAPLParser.MultiplicationContext;
import io.sapl.grammar.antlr.SAPLParser.NotExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ObjectContext;
import io.sapl.grammar.antlr.SAPLParser.PairContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicySetContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.SchemaStatementContext;
import io.sapl.grammar.antlr.SAPLParser.SignedNumberContext;
import io.sapl.grammar.antlr.SAPLParser.StepContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryMinusExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryPlusExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParserBaseVisitor;
import lombok.val;

/**
 * ANTLR visitor that walks the SAPL parse tree and produces formatted output.
 * Returns formatted strings from each visit method.
 */
class SAPLFormattingVisitor extends SAPLParserBaseVisitor<String> {

    private final CommonTokenStream tokenStream;
    private final Set<Token>        emittedComments = new HashSet<>();

    SAPLFormattingVisitor(CommonTokenStream tokenStream) {
        this.tokenStream = tokenStream;
    }

    @Override
    public String visitSapl(SaplContext ctx) {
        val sb = new StringBuilder();

        val imports = ctx.importStatement();
        if (!imports.isEmpty()) {
            appendComments(sb, imports.getFirst().getStart(), "");
            val sortedImports = imports.stream().map(this::visitImportStatement).sorted().collect(Collectors.toList());
            for (val imp : sortedImports) {
                sb.append(imp).append('\n');
            }
            sb.append('\n');
        }

        val schemas = ctx.schemaStatement();
        if (!schemas.isEmpty()) {
            for (val schema : schemas) {
                appendComments(sb, schema.getStart(), "");
                sb.append(visitSchemaStatement(schema)).append('\n');
            }
            sb.append('\n');
        }

        appendComments(sb, ctx.policyElement().getStart(), "");
        sb.append(visit(ctx.policyElement()));

        val result = sb.toString();
        if (!result.endsWith("\n")) {
            return result + "\n";
        }
        return result;
    }

    @Override
    public String visitImportStatement(ImportStatementContext ctx) {
        val sb       = new StringBuilder("import ");
        val libSteps = ctx.libSteps;
        for (var i = 0; i < libSteps.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(libSteps.get(i).getText());
        }
        sb.append('.').append(ctx.functionName.getText());
        if (ctx.functionAlias != null) {
            sb.append(" as ").append(ctx.functionAlias.getText());
        }
        return sb.toString();
    }

    @Override
    public String visitSchemaStatement(SchemaStatementContext ctx) {
        val sb = new StringBuilder();
        sb.append(ctx.subscriptionElement.getText());
        if (ctx.enforced != null) {
            sb.append(" enforced");
        }
        sb.append(" schema ");
        sb.append(visit(ctx.schemaExpression));
        return sb.toString();
    }

    @Override
    public String visitPolicySet(PolicySetContext ctx) {
        val sb = new StringBuilder();
        sb.append("set ").append(ctx.saplName.getText()).append('\n');
        sb.append(visitCombiningAlgorithm(ctx.combiningAlgorithm()));

        if (ctx.target != null) {
            sb.append('\n').append("for ").append(visit(ctx.target));
        }

        val varDefs = ctx.valueDefinition();
        for (val varDef : varDefs) {
            sb.append('\n');
            appendComments(sb, varDef.getStart(), INDENT);
            appendIndented(sb, visitValueDefinition(varDef), INDENT);
            sb.append(';');
        }

        val policies = ctx.policy();
        for (val policy : policies) {
            sb.append("\n\n");
            sb.append(formatPolicy(policy));
        }
        sb.append('\n');
        return sb.toString();
    }

    @Override
    public String visitCombiningAlgorithm(CombiningAlgorithmContext ctx) {
        val sb         = new StringBuilder();
        val votingText = ctx.votingMode().getText();
        if ("unanimousstrict".equals(votingText)) {
            sb.append("unanimous strict");
        } else {
            sb.append(votingText);
            if (votingText.startsWith("priority")) {
                sb.replace(0, sb.length(), "priority " + votingText.substring(8));
            }
        }
        sb.append(" or ");
        sb.append(ctx.defaultDecision().getText());
        if (ctx.errorHandling() != null) {
            sb.append(" errors ").append(ctx.errorHandling().getText());
        }
        return sb.toString();
    }

    @Override
    public String visitPolicy(PolicyContext ctx) {
        return formatPolicy(ctx);
    }

    @Override
    public String visitValueDefinition(ValueDefinitionContext ctx) {
        val sb = new StringBuilder();
        sb.append("var ").append(ctx.name.getText()).append(" = ");
        sb.append(visit(ctx.eval));
        if (ctx.schemaVarExpression != null && !ctx.schemaVarExpression.isEmpty()) {
            sb.append(" schema ");
            for (var i = 0; i < ctx.schemaVarExpression.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.schemaVarExpression.get(i)));
            }
        }
        return sb.toString();
    }

    @Override
    public String visitExpression(ExpressionContext ctx) {
        return visit(ctx.lazyOr());
    }

    @Override
    public String visitLazyOr(LazyOrContext ctx) {
        return formatBinaryChain(ctx.lazyAnd(), "||");
    }

    @Override
    public String visitLazyAnd(LazyAndContext ctx) {
        return formatBinaryChain(ctx.eagerOr(), "&&");
    }

    @Override
    public String visitEagerOr(EagerOrContext ctx) {
        return formatBinaryChain(ctx.exclusiveOr(), "|");
    }

    @Override
    public String visitExclusiveOr(ExclusiveOrContext ctx) {
        return formatBinaryChain(ctx.eagerAnd(), "^");
    }

    @Override
    public String visitEagerAnd(EagerAndContext ctx) {
        return formatBinaryChain(ctx.equality(), "&");
    }

    @Override
    public String visitEquality(EqualityContext ctx) {
        if (ctx.hasExpression().size() == 1) {
            return visit(ctx.hasExpression(0));
        }
        val left  = visit(ctx.hasExpression(0));
        val right = visit(ctx.hasExpression(1));
        val op    = findOperatorToken(ctx);
        return left + " " + op + " " + right;
    }

    @Override
    public String visitHasExpression(HasExpressionContext ctx) {
        if (ctx.HAS() == null) {
            return visit(ctx.comparison(0));
        }
        val    left  = visit(ctx.comparison(0));
        val    right = visit(ctx.comparison(1));
        String modifier;
        if (ctx.ANY() != null) {
            modifier = " has any ";
        } else if (ctx.ALL() != null) {
            modifier = " has all ";
        } else {
            modifier = " has ";
        }
        return left + modifier + right;
    }

    @Override
    public String visitComparison(ComparisonContext ctx) {
        if (ctx.addition().size() == 1) {
            return visit(ctx.addition(0));
        }
        val left  = visit(ctx.addition(0));
        val right = visit(ctx.addition(1));
        if (ctx.ANY() != null) {
            return left + " any in " + right;
        }
        if (ctx.ALL() != null) {
            return left + " all in " + right;
        }
        val op = findOperatorToken(ctx);
        return left + " " + op + " " + right;
    }

    @Override
    public String visitAddition(AdditionContext ctx) {
        return formatBinaryChainWithMixedOperators(ctx.multiplication(), ctx);
    }

    @Override
    public String visitMultiplication(MultiplicationContext ctx) {
        return formatBinaryChainWithMixedOperators(ctx.unaryExpression(), ctx);
    }

    @Override
    public String visitNotExpression(NotExpressionContext ctx) {
        return "!" + visit(ctx.unaryExpression());
    }

    @Override
    public String visitUnaryMinusExpression(UnaryMinusExpressionContext ctx) {
        return "-" + visit(ctx.unaryExpression());
    }

    @Override
    public String visitUnaryPlusExpression(UnaryPlusExpressionContext ctx) {
        return "+" + visit(ctx.unaryExpression());
    }

    @Override
    public String visitBasicExpression(BasicExpressionContext ctx) {
        val sb = new StringBuilder();
        sb.append(visit(ctx.basic()));
        if (ctx.filterComponent() != null) {
            sb.append(" |- ");
            sb.append(visit(ctx.filterComponent()));
        } else if (ctx.basicExpression() != null) {
            sb.append(" :: ");
            sb.append(visit(ctx.basicExpression()));
        }
        return sb.toString();
    }

    @Override
    public String visitFilterSimple(FilterSimpleContext ctx) {
        val sb = new StringBuilder();
        if (ctx.each != null) {
            sb.append("each ");
        }
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        return sb.toString();
    }

    @Override
    public String visitFilterExtended(FilterExtendedContext ctx) {
        val statements          = ctx.filterStatement();
        val formattedStatements = new ArrayList<String>();
        for (val statement : statements) {
            formattedStatements.add(visitFilterStatement(statement));
        }
        return "{ " + String.join(", ", formattedStatements) + " }";
    }

    @Override
    public String visitBasicGroup(BasicGroupContext ctx) {
        val sb = new StringBuilder();
        sb.append('(').append(visit(ctx.expression())).append(')');
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicValue(BasicValueContext ctx) {
        val sb = new StringBuilder();
        sb.append(visit(ctx.value()));
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicFunction(BasicFunctionContext ctx) {
        val sb = new StringBuilder();
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        sb.append(visitArguments(ctx.arguments()));
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicEnvironmentAttribute(BasicEnvironmentAttributeContext ctx) {
        val sb = new StringBuilder();
        sb.append('<');
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        if (ctx.attributeFinderOptions != null) {
            sb.append('[').append(visit(ctx.attributeFinderOptions)).append(']');
        }
        sb.append('>');
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicEnvironmentHeadAttribute(BasicEnvironmentHeadAttributeContext ctx) {
        val sb = new StringBuilder();
        sb.append("|<");
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        if (ctx.attributeFinderOptions != null) {
            sb.append('[').append(visit(ctx.attributeFinderOptions)).append(']');
        }
        sb.append('>');
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicIdentifier(BasicIdentifierContext ctx) {
        val sb = new StringBuilder();
        sb.append(ctx.saplId().getText());
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicRelative(BasicRelativeContext ctx) {
        val sb = new StringBuilder("@");
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitBasicRelativeLocation(BasicRelativeLocationContext ctx) {
        val sb = new StringBuilder("#");
        appendSteps(sb, ctx.step());
        return sb.toString();
    }

    @Override
    public String visitFunctionIdentifier(FunctionIdentifierContext ctx) {
        return ctx.idFragment.stream().map(ParseTree::getText).collect(Collectors.joining("."));
    }

    @Override
    public String visitArguments(ArgumentsContext ctx) {
        val sb = new StringBuilder("(");
        if (ctx.args != null) {
            for (var i = 0; i < ctx.args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.args.get(i)));
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String visitObject(ObjectContext ctx) {
        return formatObject(ctx, 0);
    }

    @Override
    public String visitPair(PairContext ctx) {
        return ctx.pairKey().getText() + ": " + visit(ctx.pairValue);
    }

    @Override
    public String visitArray(ArrayContext ctx) {
        return formatArray(ctx, 0);
    }

    @Override
    public String visitFilterStatement(FilterStatementContext ctx) {
        val sb = new StringBuilder();
        if (ctx.each != null) {
            sb.append("each ");
        }
        sb.append(visit(ctx.target));
        sb.append(" : ");
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        return sb.toString();
    }

    @Override
    public String visitSignedNumber(SignedNumberContext ctx) {
        return ctx.getText();
    }

    @Override
    public String visitTerminal(TerminalNode node) {
        return node.getText();
    }

    @Override
    protected String defaultResult() {
        return "";
    }

    @Override
    protected String aggregateResult(String aggregate, String nextResult) {
        if (aggregate == null || aggregate.isEmpty()) {
            return nextResult;
        }
        if (nextResult == null || nextResult.isEmpty()) {
            return aggregate;
        }
        return aggregate + nextResult;
    }

    private String formatPolicy(PolicyContext ctx) {
        val sb = new StringBuilder();

        appendComments(sb, ctx.getStart(), "");

        sb.append("policy ").append(ctx.saplName.getText()).append('\n');
        sb.append(ctx.entitlement().getText());

        if (ctx.policyBody() != null) {
            sb.append('\n');
            sb.append(formatPolicyBody(ctx.policyBody()));
        }

        for (val obligation : ctx.obligations) {
            sb.append('\n');
            appendComments(sb, obligation.getStart(), INDENT);
            sb.append("obligation").append('\n');
            appendIndented(sb, visit(obligation), INDENT);
        }

        for (val advice : ctx.adviceExpressions) {
            sb.append('\n');
            appendComments(sb, advice.getStart(), INDENT);
            sb.append("advice").append('\n');
            appendIndented(sb, visit(advice), INDENT);
        }

        if (ctx.transformation != null) {
            sb.append('\n');
            appendComments(sb, ctx.transformation.getStart(), INDENT);
            sb.append("transform").append('\n');
            appendIndented(sb, visit(ctx.transformation), INDENT);
        }

        return sb.toString();
    }

    private String formatPolicyBody(PolicyBodyContext ctx) {
        val sb         = new StringBuilder();
        val statements = ctx.statements;
        for (var i = 0; i < statements.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            appendComments(sb, statements.get(i).getStart(), INDENT);
            appendIndented(sb, visit(statements.get(i)), INDENT);
            sb.append(';');
        }
        return sb.toString();
    }

    private <T extends ParserRuleContext> String formatBinaryChain(List<T> operands, String operator) {
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }

        val parts = new ArrayList<String>();
        for (val operand : operands) {
            parts.add(visit(operand));
        }

        val inline = String.join(" " + operator + " ", parts);
        if (inline.length() <= LINE_WIDTH) {
            return inline;
        }

        val sb = new StringBuilder(parts.getFirst());
        for (var i = 1; i < parts.size(); i++) {
            sb.append('\n').append(INDENT).append(INDENT).append(operator).append(' ').append(parts.get(i));
        }
        return sb.toString();
    }

    private <T extends ParserRuleContext> String formatBinaryChainWithMixedOperators(List<T> operands,
            ParserRuleContext parentCtx) {
        if (operands.size() == 1) {
            return visit(operands.getFirst());
        }

        val parts     = new ArrayList<String>();
        val operators = new ArrayList<String>();
        parts.add(visit(operands.getFirst()));

        var operandIdx = 0;
        for (var i = 0; i < parentCtx.getChildCount(); i++) {
            val child = parentCtx.getChild(i);
            if (child instanceof TerminalNode terminal) {
                operators.add(terminal.getText());
            } else if (child instanceof ParserRuleContext) {
                operandIdx++;
                if (operandIdx > 0 && operandIdx < operands.size()) {
                    parts.add(visit(operands.get(operandIdx)));
                }
            }
        }

        val sb = new StringBuilder(parts.getFirst());
        for (var i = 0; i < operators.size() && i + 1 < parts.size(); i++) {
            sb.append(' ').append(operators.get(i)).append(' ').append(parts.get(i + 1));
        }
        return sb.toString();
    }

    private String findOperatorToken(ParserRuleContext ctx) {
        for (var i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof TerminalNode terminal) {
                val type = terminal.getSymbol().getType();
                if (type != Token.EOF) {
                    return terminal.getText();
                }
            }
        }
        return "";
    }

    private void appendSteps(StringBuilder sb, List<StepContext> steps) {
        if (steps == null) {
            return;
        }
        for (val step : steps) {
            sb.append(formatStep(step));
        }
    }

    private String formatStep(StepContext step) {
        val firstChild = step.getChild(0);
        if (firstChild instanceof TerminalNode terminal) {
            val tokenType = terminal.getSymbol().getType();
            if (tokenType == SAPLLexer.DOT) {
                return "." + formatStepContent(step, 1);
            }
            if (tokenType == SAPLLexer.DOTDOT) {
                return ".." + formatStepContent(step, 1);
            }
            if (tokenType == SAPLLexer.LBRACKET) {
                return "[" + formatStepContent(step, 1) + "]";
            }
        }
        return visit(step);
    }

    private String formatStepContent(StepContext step, int childIndex) {
        if (childIndex < step.getChildCount()) {
            val child = step.getChild(childIndex);
            if (child instanceof ParserRuleContext ruleCtx) {
                return visit(ruleCtx);
            }
            return child.getText();
        }
        return "";
    }

    @Override
    public String visitAttributeFinderStep(AttributeFinderStepContext ctx) {
        val sb = new StringBuilder("<");
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        if (ctx.attributeFinderOptions != null) {
            sb.append('[').append(visit(ctx.attributeFinderOptions)).append(']');
        }
        sb.append('>');
        return sb.toString();
    }

    @Override
    public String visitHeadAttributeFinderStep(HeadAttributeFinderStepContext ctx) {
        val sb = new StringBuilder("|<");
        sb.append(visitFunctionIdentifier(ctx.functionIdentifier()));
        if (ctx.arguments() != null) {
            sb.append(visitArguments(ctx.arguments()));
        }
        if (ctx.attributeFinderOptions != null) {
            sb.append('[').append(visit(ctx.attributeFinderOptions)).append(']');
        }
        sb.append('>');
        return sb.toString();
    }

    @Override
    public String visitExpressionStep(ExpressionStepContext ctx) {
        return "(" + visit(ctx.expression()) + ")";
    }

    @Override
    public String visitConditionStep(ConditionStepContext ctx) {
        return "?(" + visit(ctx.expression()) + ")";
    }

    @Override
    public String visitIndexUnionStep(IndexUnionStepContext ctx) {
        return ctx.indices.stream().map(this::visitSignedNumber).collect(Collectors.joining(", "));
    }

    @Override
    public String visitArraySlicingStep(ArraySlicingStepContext ctx) {
        val sb = new StringBuilder();
        if (ctx.index != null) {
            sb.append(visitSignedNumber(ctx.index));
        }
        if (ctx.SUBTEMPLATE() != null) {
            sb.append("::");
        } else {
            sb.append(':');
        }
        if (ctx.to != null) {
            sb.append(visitSignedNumber(ctx.to));
        }
        if (ctx.stepValue != null) {
            if (ctx.SUBTEMPLATE() == null) {
                sb.append(':');
            }
            sb.append(visitSignedNumber(ctx.stepValue));
        }
        return sb.toString();
    }

    String formatObject(ObjectContext ctx, int indentLevel) {
        val pairs = ctx.pair();
        if (pairs.isEmpty()) {
            return "{}";
        }

        val indent      = INDENT.repeat(indentLevel);
        val innerIndent = INDENT.repeat(indentLevel + 1);

        val formattedPairs = new ArrayList<String>();
        for (val pair : pairs) {
            formattedPairs.add(pair.pairKey().getText() + ": " + visit(pair.pairValue));
        }

        if (formattedPairs.size() == 1) {
            return "{ " + formattedPairs.getFirst() + " }";
        }

        val sb = new StringBuilder("{\n");
        for (var i = 0; i < formattedPairs.size(); i++) {
            sb.append(innerIndent).append(formattedPairs.get(i));
            if (i < formattedPairs.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append(indent).append('}');
        return sb.toString();
    }

    String formatArray(ArrayContext ctx, int indentLevel) {
        if (ctx.items == null || ctx.items.isEmpty()) {
            return "[]";
        }

        val indent      = INDENT.repeat(indentLevel);
        val innerIndent = INDENT.repeat(indentLevel + 1);

        val formattedItems = new ArrayList<String>();
        for (val item : ctx.items) {
            formattedItems.add(visit(item));
        }

        val inline = "[" + String.join(", ", formattedItems) + "]";
        if (inline.length() + indent.length() <= LINE_WIDTH) {
            return inline;
        }

        val sb = new StringBuilder("[\n");
        for (var i = 0; i < formattedItems.size(); i++) {
            sb.append(innerIndent).append(formattedItems.get(i));
            if (i < formattedItems.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append(indent).append(']');
        return sb.toString();
    }

    private void appendComments(StringBuilder sb, Token token, String indent) {
        val comments = getCommentsBeforeToken(token);
        if (comments.isEmpty()) {
            return;
        }
        for (val line : comments.split("\n", -1)) {
            if (!line.isEmpty()) {
                sb.append(indent).append(line).append('\n');
            }
        }
    }

    private void appendIndented(StringBuilder sb, String text, String indent) {
        val lines = text.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            if (!lines[i].isEmpty()) {
                sb.append(indent).append(lines[i]);
            }
        }
    }

    private String getCommentsBeforeToken(Token token) {
        val hidden = tokenStream.getHiddenTokensToLeft(token.getTokenIndex());
        if (hidden == null) {
            return "";
        }
        val sb = new StringBuilder();
        for (val hiddenToken : hidden) {
            if (emittedComments.contains(hiddenToken)) {
                continue;
            }
            val type = hiddenToken.getType();
            if (type == SAPLLexer.ML_COMMENT || type == SAPLLexer.SL_COMMENT) {
                emittedComments.add(hiddenToken);
                if (type == SAPLLexer.ML_COMMENT) {
                    sb.append(formatMultiLineComment(hiddenToken.getText()));
                } else {
                    sb.append(hiddenToken.getText().strip());
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String formatMultiLineComment(String text) {
        val lines = text.split("\n", -1);
        if (lines.length == 1) {
            return text.strip();
        }
        val sb = new StringBuilder(lines[0].stripLeading());
        for (var i = 1; i < lines.length; i++) {
            val stripped = lines[i].stripLeading();
            sb.append('\n');
            if (stripped.startsWith("*")) {
                sb.append(' ').append(stripped);
            } else {
                sb.append(stripped);
            }
        }
        return sb.toString();
    }

}

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
package io.sapl.lsp.sapltest;

import static io.sapl.lsp.core.FormattingConstants.INDENT;
import static io.sapl.lsp.core.FormattingConstants.LINE_WIDTH;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser.AlgorithmGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ArrayValueContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AttributeEmitStepContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AttributeMockContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AttributeNameContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AttributeParametersContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AttributeVerificationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AuthorizationDecisionContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.AuthorizationSubscriptionContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.CombiningAlgorithmContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ConfigurationGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.DocumentGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.EntityAttributeReferenceContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.EnvironmentAttributeReferenceContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ErrorValueContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ExpectOrThenExpectContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.FunctionMockContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.FunctionNameContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.FunctionParametersContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.FunctionVerificationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.GivenContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.GivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.MatcherExpectationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.MockGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.MultipleDocumentsContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ObjectValueContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.PairContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.PdpConfigurationGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SecretsGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SingleDocumentContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SingleExpectationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.StreamExpectationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ThenBlockContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ThenExpectContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.VariablesGivenItemContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.VerifyBlockContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.WhenStepContext;
import io.sapl.test.grammar.antlr.SAPLTestParserBaseVisitor;
import lombok.val;

/**
 * ANTLR visitor that walks the SAPLTest parse tree and produces formatted
 * output.
 */
class SAPLTestFormattingVisitor extends SAPLTestParserBaseVisitor<String> {

    private static final String ATTRIBUTE = "attribute ";
    private static final String INDENT2   = INDENT.repeat(2);
    private static final String INDENT3   = INDENT.repeat(3);
    private static final String WHEN      = "when ";

    private final CommonTokenStream tokenStream;
    private final Set<Token>        emittedComments = new HashSet<>();

    SAPLTestFormattingVisitor(CommonTokenStream tokenStream) {
        this.tokenStream = tokenStream;
    }

    @Override
    public String visitSaplTest(SaplTestContext ctx) {
        val sb           = new StringBuilder();
        val requirements = ctx.requirement();
        for (var i = 0; i < requirements.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(visitRequirement(requirements.get(i)));
        }
        val result = sb.toString();
        if (!result.endsWith("\n")) {
            return result + "\n";
        }
        return result;
    }

    @Override
    public String visitRequirement(RequirementContext ctx) {
        val sb = new StringBuilder();
        sb.append("requirement ").append(ctx.name.getText()).append(" {\n");
        sb.append('\n');

        if (ctx.given() != null) {
            sb.append(formatGiven(ctx.given(), INDENT));
            sb.append('\n');
        }

        val scenarios = ctx.scenario();
        for (var i = 0; i < scenarios.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(formatScenario(scenarios.get(i)));
        }

        sb.append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String visitCombiningAlgorithm(CombiningAlgorithmContext ctx) {
        val sb         = new StringBuilder();
        val votingText = ctx.votingMode().getText();
        if ("unanimousstrict".equals(votingText)) {
            sb.append("unanimous strict");
        } else if (votingText.startsWith("priority")) {
            sb.append("priority ").append(votingText.substring(8));
        } else {
            sb.append(votingText);
        }
        sb.append(" or ");
        sb.append(ctx.defaultDecision().getText());
        if (ctx.errorHandling() != null) {
            sb.append(" errors ").append(ctx.errorHandling().getText());
        }
        return sb.toString();
    }

    @Override
    public String visitFunctionName(FunctionNameContext ctx) {
        return ctx.parts.stream().map(ParseTree::getText).collect(Collectors.joining("."));
    }

    @Override
    public String visitAttributeName(AttributeNameContext ctx) {
        return ctx.parts.stream().map(ParseTree::getText).collect(Collectors.joining("."));
    }

    @Override
    public String visitFunctionParameters(FunctionParametersContext ctx) {
        val sb = new StringBuilder("(");
        if (ctx.matchers != null) {
            for (var i = 0; i < ctx.matchers.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.matchers.get(i)));
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String visitAttributeParameters(AttributeParametersContext ctx) {
        val sb = new StringBuilder("(");
        if (ctx.matchers != null) {
            for (var i = 0; i < ctx.matchers.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.matchers.get(i)));
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String visitEnvironmentAttributeReference(EnvironmentAttributeReferenceContext ctx) {
        val sb = new StringBuilder("<");
        sb.append(visitAttributeName(ctx.attributeFullName));
        if (ctx.attributeParameters() != null) {
            sb.append(visitAttributeParameters(ctx.attributeParameters()));
        }
        sb.append('>');
        return sb.toString();
    }

    @Override
    public String visitEntityAttributeReference(EntityAttributeReferenceContext ctx) {
        val sb = new StringBuilder();
        sb.append(visit(ctx.entityMatcher));
        sb.append(".<");
        sb.append(visitAttributeName(ctx.attributeFullName));
        if (ctx.attributeParameters() != null) {
            sb.append(visitAttributeParameters(ctx.attributeParameters()));
        }
        sb.append('>');
        return sb.toString();
    }

    @Override
    public String visitAuthorizationDecision(AuthorizationDecisionContext ctx) {
        val sb = new StringBuilder();
        sb.append(visit(ctx.decision));
        if (ctx.obligations != null && !ctx.obligations.isEmpty()) {
            sb.append(" with obligations ");
            for (var i = 0; i < ctx.obligations.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.obligations.get(i)));
            }
        }
        if (ctx.resource != null) {
            sb.append(" with resource ").append(visit(ctx.resource));
        }
        if (ctx.advice != null && !ctx.advice.isEmpty()) {
            sb.append(" with advice ");
            for (var i = 0; i < ctx.advice.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(visit(ctx.advice.get(i)));
            }
        }
        return sb.toString();
    }

    @Override
    public String visitObjectValue(ObjectValueContext ctx) {
        return formatObjectValue(ctx, 0);
    }

    @Override
    public String visitPair(PairContext ctx) {
        return ctx.key.getText() + ": " + visit(ctx.pairValue);
    }

    @Override
    public String visitArrayValue(ArrayValueContext ctx) {
        return formatArrayValue(ctx, 0);
    }

    @Override
    public String visitErrorValue(ErrorValueContext ctx) {
        if (ctx.message != null) {
            return "error(" + ctx.message.getText() + ")";
        }
        return "error";
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

    private String formatScenario(ScenarioContext ctx) {
        val sb = new StringBuilder();

        val comments = getCommentsBeforeToken(ctx.getStart());
        if (!comments.isEmpty()) {
            sb.append(INDENT).append(comments).append('\n');
        }

        sb.append(INDENT).append("scenario ").append(ctx.name.getText()).append('\n');

        if (ctx.given() != null) {
            sb.append(formatGiven(ctx.given(), INDENT2));
        }

        sb.append(formatWhenStep(ctx.whenStep()));
        sb.append(formatExpectOrThenExpect(ctx.expectOrThenExpect(), ctx.verifyBlock()));

        return sb.toString();
    }

    private String formatGiven(GivenContext ctx, String indent) {
        val sb = new StringBuilder();
        sb.append(indent).append("given\n");
        val innerIndent = indent + INDENT;
        for (val item : ctx.givenItem()) {
            sb.append(innerIndent).append("- ").append(formatGivenItem(item)).append('\n');
        }
        return sb.toString();
    }

    private String formatGivenItem(GivenItemContext ctx) {
        if (ctx instanceof DocumentGivenItemContext docCtx) {
            return visit(docCtx.documentSpecification());
        }
        if (ctx instanceof AlgorithmGivenItemContext algCtx) {
            return visitCombiningAlgorithm(algCtx.combiningAlgorithm());
        }
        if (ctx instanceof VariablesGivenItemContext varCtx) {
            return "variables " + formatObjectValue(varCtx.variablesDefinition().variables, 3);
        }
        if (ctx instanceof SecretsGivenItemContext secCtx) {
            return "secrets " + formatObjectValue(secCtx.secretsDefinition().secrets, 3);
        }
        if (ctx instanceof MockGivenItemContext mockCtx) {
            return visit(mockCtx.mockDefinition());
        }
        if (ctx instanceof ConfigurationGivenItemContext confCtx) {
            return "configuration " + confCtx.configurationSpecification().path.getText();
        }
        if (ctx instanceof PdpConfigurationGivenItemContext pdpCtx) {
            return "pdp-configuration " + pdpCtx.pdpConfigurationSpecification().path.getText();
        }
        return ctx.getText();
    }

    @Override
    public String visitSingleDocument(SingleDocumentContext ctx) {
        return "document " + ctx.identifier.getText();
    }

    @Override
    public String visitMultipleDocuments(MultipleDocumentsContext ctx) {
        val docs = ctx.identifiers.stream().map(Token::getText).collect(Collectors.joining(", "));
        return "documents " + docs;
    }

    @Override
    public String visitFunctionMock(FunctionMockContext ctx) {
        val sb = new StringBuilder("function ");
        sb.append(visitFunctionName(ctx.functionFullName));
        if (ctx.functionParameters() != null) {
            sb.append(visitFunctionParameters(ctx.functionParameters()));
        }
        sb.append(" maps to ").append(visit(ctx.returnValue));
        return sb.toString();
    }

    @Override
    public String visitAttributeMock(AttributeMockContext ctx) {
        val sb = new StringBuilder(ATTRIBUTE);
        sb.append(ctx.mockId.getText()).append(' ');
        sb.append(visit(ctx.attributeReference()));
        if (ctx.initialValue != null) {
            sb.append(" emits ").append(visit(ctx.initialValue));
        }
        return sb.toString();
    }

    private String formatWhenStep(WhenStepContext ctx) {
        val sub = ctx.authorizationSubscription();
        val sb  = new StringBuilder();
        sb.append(INDENT2).append(WHEN);
        sb.append(formatAuthorizationSubscription(sub));
        sb.append('\n');
        return sb.toString();
    }

    private String formatAuthorizationSubscription(AuthorizationSubscriptionContext ctx) {
        val sb = new StringBuilder();
        if (ctx.SUBJECT() != null) {
            sb.append("subject ");
        }
        sb.append(visit(ctx.subject));
        sb.append(" attempts ");
        if (ctx.ACTION() != null) {
            sb.append("action ");
        }
        sb.append(visit(ctx.action));
        sb.append(" on ");
        if (ctx.RESOURCE() != null) {
            sb.append("resource ");
        }
        sb.append(visit(ctx.resource));

        if (ctx.env != null) {
            appendEnvironmentClause(sb, ctx);
        }

        if (ctx.subscriptionSecrets != null) {
            appendSecretsClause(sb, ctx);
        }

        return sb.toString();
    }

    private void appendEnvironmentClause(StringBuilder sb, AuthorizationSubscriptionContext ctx) {
        val envStr = " in " + (ctx.ENVIRONMENT() != null ? "environment " : "") + formatObjectValue(ctx.env, 0);
        if (sb.length() + envStr.length() + INDENT2.length() + WHEN.length() <= LINE_WIDTH) {
            sb.append(envStr);
        } else {
            sb.append('\n').append(INDENT3).append("in ");
            if (ctx.ENVIRONMENT() != null) {
                sb.append("environment ");
            }
            sb.append(formatObjectValue(ctx.env, 0));
        }
    }

    private void appendSecretsClause(StringBuilder sb, AuthorizationSubscriptionContext ctx) {
        val secStr = " with secrets " + formatObjectValue(ctx.subscriptionSecrets, 0);
        if (sb.length() + secStr.length() + INDENT2.length() + WHEN.length() <= LINE_WIDTH) {
            sb.append(secStr);
        } else {
            sb.append('\n').append(INDENT3).append("with secrets ")
                    .append(formatObjectValue(ctx.subscriptionSecrets, 0));
        }
    }

    private String formatExpectOrThenExpect(ExpectOrThenExpectContext ctx, VerifyBlockContext verifyCtx) {
        val sb     = new StringBuilder();
        val isLast = ctx.thenExpect().isEmpty() && verifyCtx == null;
        sb.append(formatExpectation(ctx.expectation(), isLast && verifyCtx == null && ctx.thenExpect().isEmpty()));

        for (var i = 0; i < ctx.thenExpect().size(); i++) {
            val thenExpect = ctx.thenExpect().get(i);
            sb.append(formatThenBlock(thenExpect.thenBlock()));
            val isLastExpect = i == ctx.thenExpect().size() - 1 && verifyCtx == null;
            sb.append(formatExpectation(thenExpect.expectation(), isLastExpect));
        }

        if (verifyCtx != null) {
            sb.append(formatVerifyBlock(verifyCtx));
        }

        return sb.toString();
    }

    private String formatExpectation(io.sapl.test.grammar.antlr.SAPLTestParser.ExpectationContext ctx,
            boolean withSemicolon) {
        if (ctx instanceof SingleExpectationContext singleCtx) {
            return formatSingleExpectation(singleCtx, withSemicolon);
        }
        if (ctx instanceof MatcherExpectationContext matcherCtx) {
            return formatMatcherExpectation(matcherCtx, withSemicolon);
        }
        if (ctx instanceof StreamExpectationContext streamCtx) {
            return formatStreamExpectation(streamCtx, withSemicolon);
        }
        return "";
    }

    private String formatSingleExpectation(SingleExpectationContext ctx, boolean withSemicolon) {
        val sb = new StringBuilder();
        sb.append(INDENT2).append("expect ");
        sb.append(visitAuthorizationDecision(ctx.authorizationDecision()));
        if (withSemicolon) {
            sb.append(';');
        }
        sb.append('\n');
        return sb.toString();
    }

    private String formatMatcherExpectation(MatcherExpectationContext ctx, boolean withSemicolon) {
        val sb       = new StringBuilder();
        val matchers = ctx.matchers.stream().map(this::visit).collect(Collectors.toList());
        val inline   = String.join(", ", matchers);
        val fullLine = INDENT2 + "expect decision " + inline;
        sb.append(INDENT2).append("expect decision ");
        if (fullLine.length() + (withSemicolon ? 1 : 0) <= LINE_WIDTH) {
            sb.append(inline);
        } else {
            sb.append(matchers.getFirst());
            for (var i = 1; i < matchers.size(); i++) {
                sb.append(",\n").append(INDENT3).append(matchers.get(i));
            }
        }
        if (withSemicolon) {
            sb.append(';');
        }
        sb.append('\n');
        return sb.toString();
    }

    private String formatStreamExpectation(StreamExpectationContext ctx, boolean withSemicolon) {
        val sb    = new StringBuilder();
        val steps = ctx.expectStep();
        sb.append(INDENT2).append("expect\n");
        for (var i = 0; i < steps.size(); i++) {
            sb.append(INDENT3).append("- ").append(visit(steps.get(i)));
            if (withSemicolon && i == steps.size() - 1) {
                sb.append(';');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String formatThenBlock(ThenBlockContext ctx) {
        val sb = new StringBuilder();
        sb.append(INDENT2).append("then\n");
        for (val step : ctx.thenStep()) {
            sb.append(INDENT3).append("- ").append(visit(step)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String visitAttributeEmitStep(AttributeEmitStepContext ctx) {
        return ATTRIBUTE + ctx.mockId.getText() + " emits " + visit(ctx.emittedValue);
    }

    private String formatVerifyBlock(VerifyBlockContext ctx) {
        val sb = new StringBuilder();
        sb.append(INDENT2).append("verify\n");
        val steps = ctx.verifyStep();
        for (var i = 0; i < steps.size(); i++) {
            sb.append(INDENT3).append("- ").append(visit(steps.get(i)));
            if (i == steps.size() - 1) {
                sb.append(';');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String visitFunctionVerification(FunctionVerificationContext ctx) {
        val sb = new StringBuilder("function ");
        sb.append(visitFunctionName(ctx.functionFullName));
        if (ctx.functionParameters() != null) {
            sb.append(visitFunctionParameters(ctx.functionParameters()));
        }
        sb.append(" is called ").append(visit(ctx.timesCalled));
        return sb.toString();
    }

    @Override
    public String visitAttributeVerification(AttributeVerificationContext ctx) {
        val sb = new StringBuilder(ATTRIBUTE);
        sb.append(visit(ctx.attributeReference()));
        sb.append(" is called ").append(visit(ctx.timesCalled));
        return sb.toString();
    }

    String formatObjectValue(ObjectValueContext ctx, int indentLevel) {
        val pairs = ctx.pair();
        if (pairs.isEmpty()) {
            return "{}";
        }

        val indent      = INDENT.repeat(indentLevel);
        val innerIndent = INDENT.repeat(indentLevel + 1);

        val formattedPairs = new ArrayList<String>();
        for (val pair : pairs) {
            formattedPairs.add(pair.key.getText() + ": " + visit(pair.pairValue));
        }

        val inline = "{ " + String.join(", ", formattedPairs) + " }";
        if (inline.length() + indent.length() <= LINE_WIDTH) {
            return inline;
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

    String formatArrayValue(ArrayValueContext ctx, int indentLevel) {
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
            if (type == SAPLTestLexer.ML_COMMENT || type == SAPLTestLexer.SL_COMMENT) {
                emittedComments.add(hiddenToken);
                sb.append(hiddenToken.getText().strip());
                if (type == SAPLTestLexer.SL_COMMENT) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

}

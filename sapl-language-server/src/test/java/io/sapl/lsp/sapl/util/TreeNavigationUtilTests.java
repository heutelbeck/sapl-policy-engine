/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.lsp.sapl.TestParsing;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;

/**
 * Tests for TreeNavigationUtil ANTLR parse tree navigation utilities.
 */
class TreeNavigationUtilTests {

    private static final String SIMPLE_POLICY = """
            policy "test"
            permit
            where
              var foo = 5;
            """;

    @Test
    void whenGoToFirstParent_nodeIsNull_thenThrowsNullPointerException() {
        assertThatThrownBy(() -> TreeNavigationUtil.goToFirstParent(null, SaplContext.class))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenGoToFirstParent_classTypeIsNull_thenThrowsNullPointerException() {
        var sapl = parse(SIMPLE_POLICY);
        assertThatThrownBy(() -> TreeNavigationUtil.goToFirstParent(sapl, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenGoToFirstParent_nodeIsRequestedType_thenReturnsSameNode() {
        var sapl = parse(SIMPLE_POLICY);

        var result = TreeNavigationUtil.goToFirstParent(sapl, SaplContext.class);

        assertThat(result).isSameAs(sapl);
    }

    @Test
    void whenGoToFirstParent_parentIsRequestedType_thenReturnsParent() {
        var sapl = parse(SIMPLE_POLICY);
        var body = getPolicyBody(sapl);
        if (body == null) {
            return;
        }
        var statement = body.statement(0);

        var result = TreeNavigationUtil.goToFirstParent(statement, PolicyBodyContext.class);

        assertThat(result).isSameAs(body);
    }

    @Test
    void whenGoToFirstParent_noMatchingParent_thenReturnsNull() {
        var sapl = parse(SIMPLE_POLICY);

        var result = TreeNavigationUtil.goToFirstParent(sapl, ValueDefinitionContext.class);

        assertThat(result).isNull();
    }

    @Test
    void whenGoToFirstParent_multipleMatchingParents_thenReturnsClosest() {
        var document = """
                policy "test"
                permit
                where
                  (1 + 2);
                """;
        var sapl     = parse(document);
        var body     = getPolicyBody(sapl);
        if (body == null) {
            return;
        }
        var statement = body.statement(0);
        if (!(statement instanceof io.sapl.grammar.antlr.SAPLParser.ConditionStatementContext conditionStmt)) {
            return;
        }
        var outerExpression = conditionStmt.expression();

        var result = TreeNavigationUtil.goToFirstParent(outerExpression, ExpressionContext.class);

        assertThat(result).isSameAs(outerExpression);
    }

    @Test
    void whenGoToLastParent_nodeIsNull_thenThrowsNullPointerException() {
        assertThatThrownBy(() -> TreeNavigationUtil.goToLastParent(null, SaplContext.class))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenGoToLastParent_classTypeIsNull_thenThrowsNullPointerException() {
        var sapl = parse(SIMPLE_POLICY);
        assertThatThrownBy(() -> TreeNavigationUtil.goToLastParent(sapl, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenGoToLastParent_nodeIsRequestedType_thenReturnsSameNode() {
        var sapl = parse(SIMPLE_POLICY);

        var result = TreeNavigationUtil.goToLastParent(sapl, SaplContext.class);

        assertThat(result).isSameAs(sapl);
    }

    @Test
    void whenGoToLastParent_noMatchingParent_thenReturnsNull() {
        var sapl = parse(SIMPLE_POLICY);

        var result = TreeNavigationUtil.goToLastParent(sapl, ValueDefinitionContext.class);

        assertThat(result).isNull();
    }

    @Test
    void whenGoToLastParent_fromDeepNode_thenReturnsRootMatch() {
        var sapl = parse(SIMPLE_POLICY);
        var body = getPolicyBody(sapl);
        if (body == null) {
            return;
        }
        var statement = body.statement(0);
        if (!(statement instanceof ValueDefinitionStatementContext valueDefStmt)) {
            return;
        }
        var valueDef = valueDefStmt.valueDefinition();

        var result = TreeNavigationUtil.goToLastParent(valueDef, SaplContext.class);

        assertThat(result).isSameAs(sapl);
    }

    @Test
    void whenOffsetOf_validNode_thenReturnsStartIndex() {
        var sapl = parse(SIMPLE_POLICY);

        var offset = TreeNavigationUtil.offsetOf(sapl);

        assertThat(offset).isZero();
    }

    @Test
    void whenOffsetOf_policyBody_thenReturnsWhereOffset() {
        var sapl = parse(SIMPLE_POLICY);
        var body = getPolicyBody(sapl);
        if (body == null) {
            return;
        }

        var offset = TreeNavigationUtil.offsetOf(body);

        assertThat(offset).isEqualTo(SIMPLE_POLICY.indexOf("where"));
    }

    @Test
    void whenOffsetOf_valueDefinition_thenReturnsVarOffset() {
        var sapl = parse(SIMPLE_POLICY);
        var body = getPolicyBody(sapl);
        if (body == null) {
            return;
        }
        var statement = body.statement(0);
        if (!(statement instanceof ValueDefinitionStatementContext valueDefStmt)) {
            return;
        }
        var valueDef = valueDefStmt.valueDefinition();

        var offset = TreeNavigationUtil.offsetOf(valueDef);

        assertThat(offset).isEqualTo(SIMPLE_POLICY.indexOf("var foo"));
    }

    @Test
    void whenOffsetOf_nullNode_thenReturnsMinusOne() {
        var offset = TreeNavigationUtil.offsetOf(null);

        assertThat(offset).isEqualTo(-1);
    }

    @Test
    void whenGoToFirstParent_fromPolicy_thenFindsPolicyContext() {
        var sapl   = parse(SIMPLE_POLICY);
        var policy = getPolicy(sapl);
        if (policy == null) {
            return;
        }

        var result = TreeNavigationUtil.goToFirstParent(policy, PolicyContext.class);

        assertThat(result).isSameAs(policy);
    }

    private static PolicyContext getPolicy(SaplContext sapl) {
        var element = sapl.policyElement();
        if (element instanceof PolicyOnlyElementContext poe) {
            return poe.policy();
        }
        return null;
    }

    private static PolicyBodyContext getPolicyBody(SaplContext sapl) {
        var policy = getPolicy(sapl);
        return policy != null ? policy.policyBody() : null;
    }

    private static SaplContext parse(String content) {
        return TestParsing.parseSilently(content);
    }

}

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
package io.sapl.vaadin.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayName("Vaadin authorization subscription builder")
class VaadinAuthorizationSubscriptionBuilderTests {

    MethodSecurityExpressionHandler               methodSecurityExpressionHandlerMock;
    private final JsonMapper                      mapper = JsonMapper.builder().build();
    VaadinAuthorizationSubscriptionBuilderService sut;
    private static final JsonNodeFactory          JSON   = JsonNodeFactory.instance;

    @BeforeEach
    void setup() {
        methodSecurityExpressionHandlerMock = mock(MethodSecurityExpressionHandler.class);
        sut                                 = new VaadinAuthorizationSubscriptionBuilderService(
                methodSecurityExpressionHandlerMock, mapper);
    }

    @Test
    void when_evaluateExpressionStringToJson_then_valueToTreeWithInputIsReturned() {
        // GIVEN
        var expressionParser = mock(ExpressionParser.class);
        when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
        var expressionMock = mock(Expression.class);
        when(expressionMock.getValue(any(EvaluationContext.class))).thenReturn("1");
        when(expressionParser.parseExpression(any())).thenReturn(expressionMock);

        // WHEN
        JsonNode ret = sut.evaluateExpressionStringToJson("{roles: getAuthorities().![getAuthority()]}", null);

        // THEN
        assertThat(ret).isEqualTo(mapper.valueToTree("1"));
    }

    @Test
    void when_evaluateExpressionStringToJsonWithThrownEvaluationException_then_IllegalArgumentExceptionIsGiven() {
        // GIVEN
        var expressionParser = mock(ExpressionParser.class);
        when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
        var expressionMock = mock(Expression.class);
        when(expressionMock.getValue(any(EvaluationContext.class))).thenThrow(EvaluationException.class);
        when(expressionParser.parseExpression(any())).thenReturn(expressionMock);
        when(expressionMock.getExpressionString()).thenReturn("roles: getAuthorities().![getAuthority()]}");

        // WHEN + THEN
        assertThatThrownBy(
                () -> sut.evaluateExpressionStringToJson("{roles: getAuthorities().![getAuthority()]}", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to evaluate expression 'roles: getAuthorities().![getAuthority()]}'");
    }

    @Test
    void when_retrieveSubjectCredentials_then_SubjectIsReturnedWithoutCredentials() {
        // GIVEN
        var authReq = new UsernamePasswordAuthenticationToken("user", "pass");

        // WHEN
        var ret = sut.retrieveSubject(authReq);

        // THEN
        assertThat(ret.get("principal")).isEqualTo(mapper.valueToTree("user"));
        assertThat(ret.get("credentials")).isNull();
    }

    @Test
    void when_retrieveSubjectWithPasswordAndCredentials_then_SubjectIsReturnedWithoutBoth() {
        // GIVEN
        var subject = mapper.createObjectNode();
        subject.set("password", mapper.convertValue("password", JsonNode.class));
        subject.set("user", mapper.convertValue("user", JsonNode.class));
        var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

        // WHEN
        var ret = sut.retrieveSubject(authReq);

        // THEN
        assertThat(ret.get("principal").get("password")).isNull();
        assertThat(ret.get("credentials")).isNull();
        var expected = mapper.createObjectNode();
        expected.set("user", mapper.convertValue("user", JsonNode.class));
        assertThat(ret.get("principal")).isEqualTo(expected);
    }

    @Test
    void when_retrieveSubjectWithPasswordAndCredentialsWithoutExpression_then_SubjectIsReturnedWithoutBoth() {
        // GIVEN
        var subject = mapper.createObjectNode();
        subject.set("password", mapper.convertValue("password", JsonNode.class));
        subject.set("user", mapper.convertValue("user", JsonNode.class));
        var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

        // WHEN
        var ret = sut.retrieveSubject(authReq, null);

        // THEN
        assertThat(ret.get("principal").get("password")).isNull();
        assertThat(ret.get("credentials")).isNull();
        var expected = mapper.createObjectNode();
        expected.set("user", mapper.convertValue("user", JsonNode.class));
        assertThat(ret.get("principal")).isEqualTo(expected);
    }

    @Test
    void when_retrieveSubjectWithPasswordAndCredentialsWithEmptyExpression_then_SubjectIsReturnedWithoutBoth() {
        // GIVEN
        var subject = mapper.createObjectNode();
        subject.set("password", mapper.convertValue("password", JsonNode.class));
        subject.set("user", mapper.convertValue("user", JsonNode.class));
        var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

        // WHEN
        var ret = sut.retrieveSubject(authReq, "");

        // THEN
        assertThat(ret.get("principal").get("password")).isNull();
        assertThat(ret.get("credentials")).isNull();
        var expected = mapper.createObjectNode();
        expected.set("user", mapper.convertValue("user", JsonNode.class));
        assertThat(ret.get("principal")).isEqualTo(expected);
    }

    @Test
    void when_retrieveSubjectWithExpression_then_valueToTreeWithInputIsReturned() {
        // GIVEN
        var subject = mapper.createObjectNode();
        subject.set("password", mapper.convertValue("password", JsonNode.class));
        subject.set("user", mapper.convertValue("user", JsonNode.class));
        var authReq = new UsernamePasswordAuthenticationToken(subject, "pass");

        var expressionParser = mock(ExpressionParser.class);
        when(methodSecurityExpressionHandlerMock.getExpressionParser()).thenReturn(expressionParser);
        var expressionMock = mock(Expression.class);
        when(expressionMock.getValue(any(EvaluationContext.class))).thenReturn("1");
        when(expressionParser.parseExpression(any())).thenReturn(expressionMock);

        // WHEN
        var ret = sut.retrieveSubject(authReq, "{roles: getAuthorities().![getAuthority()]}");

        // THEN
        assertThat(ret).isEqualTo(mapper.valueToTree("1"));
    }

    @Test
    void when_serializeTargetClassDescription() {
        // GIVEN
        // WHEN
        var ret = VaadinAuthorizationSubscriptionBuilderService.serializeTargetClassDescription(getClass());

        // THEN
        var subject = mapper.createObjectNode();
        subject.set("name",
                JsonNodeFactory.instance.stringNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
        subject.set("canonicalName",
                JsonNodeFactory.instance.stringNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
        subject.set("typeName",
                JsonNodeFactory.instance.stringNode("io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderTests"));
        subject.set("simpleName", JsonNodeFactory.instance.stringNode("VaadinAuthorizationSubscriptionBuilderTests"));
        assertThat(ret).isEqualTo(subject);
    }

}

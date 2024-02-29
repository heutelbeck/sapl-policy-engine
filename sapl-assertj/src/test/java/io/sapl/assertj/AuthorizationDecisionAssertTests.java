/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.assertj;

import static io.sapl.assertj.SaplAssertions.assertThatAuthorizationDecision;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;

class AuthorizationDecisionAssertTests {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void isPermitPositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertDoesNotThrow(() -> sut.isPermit());
    }

    @Test
    void isPermitNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.DENY);
        assertThatThrownBy(() -> sut.isPermit()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have decision <PERMIT> but was <DENY>");
    }

    @Test
    void isDenyPositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.DENY);
        assertDoesNotThrow(() -> sut.isDeny());
    }

    @Test
    void isDenyNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertThatThrownBy(() -> sut.isDeny()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have decision <DENY> but was <PERMIT>");
    }

    @Test
    void isNotApplicablePositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.NOT_APPLICABLE);
        assertDoesNotThrow(() -> sut.isNotApplicable());
    }

    @Test
    void isNotApplicableNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.DENY);
        assertThatThrownBy(() -> sut.isNotApplicable()).isInstanceOf(AssertionError.class).hasMessageContaining(
                "Expected AuthorizationDecision to have decision <NOT_APPLICABLE> but was <DENY>");
    }

    @Test
    void isIndeterminatePositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.INDETERMINATE);
        assertDoesNotThrow(() -> sut.isIndeterminate());
    }

    @Test
    void isIndeterminateNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.DENY);
        assertThatThrownBy(() -> sut.isIndeterminate()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have decision <INDETERMINATE> but was <DENY>");
    }

    @Test
    void hasObligationsPositive() {
        ArrayNode obligations = mapper.createArrayNode();
        obligations.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        var sut      = assertThatAuthorizationDecision(decision);
        assertDoesNotThrow(() -> sut.hasObligations());
    }

    @Test
    void hasObligationsNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertThatThrownBy(() -> sut.hasObligations()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have obligations but it had none.");
    }

    @Test
    void hasNoObligationsPositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertDoesNotThrow(() -> sut.hasNoObligations());
    }

    @Test
    void hasNoObligationsNegative() {
        ArrayNode obligations = mapper.createArrayNode();
        obligations.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        var sut      = assertThatAuthorizationDecision(decision);
        assertThatThrownBy(() -> sut.hasNoObligations()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have no obligations but they were");
    }

    @Test
    void hasAdvicePositive() {
        ArrayNode advice = mapper.createArrayNode();
        advice.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        var sut      = assertThatAuthorizationDecision(decision);
        assertDoesNotThrow(() -> sut.hasAdvice());
    }

    @Test
    void hasAdviceNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertThatThrownBy(() -> sut.hasAdvice()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have advice but it had none.");
    }

    @Test
    void hasNoAdvicePositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertDoesNotThrow(() -> sut.hasNoAdvice());
    }

    @Test
    void hasNoAdviceNegative() {
        ArrayNode advice = mapper.createArrayNode();
        advice.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        var sut      = assertThatAuthorizationDecision(decision);
        assertThatThrownBy(() -> sut.hasNoAdvice()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have no advice but they were");
    }

    @Test
    void hasResourcePositive() {
        ArrayNode resource = mapper.createArrayNode();
        resource.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withResource(resource);
        var sut      = assertThatAuthorizationDecision(decision);
        assertDoesNotThrow(() -> sut.hasResource());
    }

    @Test
    void hasResourceNegative() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertThatThrownBy(() -> sut.hasResource()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have a resource but it had none.");
    }

    @Test
    void hasNoResourcePositive() {
        var sut = assertThatAuthorizationDecision(AuthorizationDecision.PERMIT);
        assertDoesNotThrow(() -> sut.hasNoResource());
    }

    @Test
    void hasNoResourceNegative() {
        ArrayNode resource = mapper.createArrayNode();
        resource.addObject().put("foo", "bar");
        var decision = AuthorizationDecision.PERMIT.withResource(resource);
        var sut      = assertThatAuthorizationDecision(decision);
        assertThatThrownBy(() -> sut.hasNoResource()).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have no resource but was");
    }

}

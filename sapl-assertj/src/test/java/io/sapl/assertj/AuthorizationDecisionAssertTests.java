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

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class AuthorizationDecisionAssertTests {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testPermit() {
        var sut = new AuthorizationDecision(Decision.PERMIT);
        assertThatAuthorizationDecision(sut).isPermit();
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.DENY));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.NOT_APPLICABLE));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.INDETERMINATE));
    }

    @Test
    void testDeny() {
        var sut = new AuthorizationDecision(Decision.DENY);
        assertThatAuthorizationDecision(sut).isDeny();
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.PERMIT));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.NOT_APPLICABLE));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.INDETERMINATE));
    }

    @Test
    void testIsNotApplicable() {
        var sut = new AuthorizationDecision(Decision.NOT_APPLICABLE);
        assertThatAuthorizationDecision(sut).isNotApplicable();
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.PERMIT));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.DENY));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.INDETERMINATE));
    }

    @Test
    void testIndeterminate() {
        var sut = new AuthorizationDecision(Decision.INDETERMINATE);
        assertThatAuthorizationDecision(sut).isIndeterminate();
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.PERMIT));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.DENY));
        assertThatAuthorizationDecision(sut).isNotEqualTo(new AuthorizationDecision(Decision.NOT_APPLICABLE));
    }

    @Test
    void testEmptyObligations() {
        ArrayNode obligation = mapper.createArrayNode();
        obligation.addObject().put("foo", "bar");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT).withObligations(obligation);
        assertThatAuthorizationDecision(dec).hasObligations();
    }

    @Test
    void testNegativeObligation() {
        ArrayNode obligation = mapper.createArrayNode();
        obligation.addObject().put("foo", "bar");
        ArrayNode expectedObligation = mapper.createArrayNode();
        expectedObligation.addObject().put("xxx", "xxx");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT).withObligations(obligation);
        assertThatThrownBy(() -> assertThatAuthorizationDecision(dec).hasNoObligations())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected AuthorizationDecision to have no obligations");
    }

    @Test
    void testDecisions() {
        ArrayNode obligation1 = mapper.createArrayNode();
        obligation1.add(mapper.createObjectNode().put("foo", "bar"));
        AuthorizationDecision dec1        = new AuthorizationDecision(Decision.PERMIT).withObligations(obligation1);
        ArrayNode             obligation2 = mapper.createArrayNode();
        obligation2.add(mapper.createObjectNode().put("XXX", "XXX"));
        AuthorizationDecision dec2 = new AuthorizationDecision(Decision.PERMIT).withObligations(obligation2);
        assertThatAuthorizationDecision(dec1).isNotEqualTo(dec2);
    }

    @Test
    void testEmptyAdvice() {
        ArrayNode Advice = mapper.createArrayNode();
        Advice.addObject().put("foo", "bar");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT).withAdvice(Advice);
        assertThatAuthorizationDecision(dec).hasAdvice();
    }

    @Test
    void testEmptyResource() {
        ArrayNode Resource = mapper.createArrayNode();
        Resource.addObject().put("foo", "bar");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT).withResource(Resource);
        assertThatAuthorizationDecision(dec).hasResource();
    }

}

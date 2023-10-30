/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.combinators;

import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateAdvice;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateDecision;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateObligations;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateResource;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.impl.FirstApplicableCombiningAlgorithmImplCustom;
import reactor.test.StepVerifier;

class FirstApplicableTests {

    private static final JsonNodeFactory           JSON                                 = JsonNodeFactory.instance;
    private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION              = new AuthorizationSubscription(
            null, null, null, null);
    private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
            null, null, JSON.booleanNode(true), null);

    @Test
    void noDecisionsIsNotApplicable() {
        var algorithm = new FirstApplicableCombiningAlgorithmImplCustom();
        StepVerifier.create(algorithm.combinePolicies(List.of())).expectNextMatches(combinedDecision -> combinedDecision
                .getAuthorizationDecision().getDecision() == Decision.NOT_APPLICABLE).verifyComplete();
    }

    @Test
    void permit() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit";
        var expected  = Decision.PERMIT;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void deny() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny";
        var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void notApplicableTarget() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny true == false";
        var expected  = Decision.NOT_APPLICABLE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void notApplicableCondition() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" deny where true == false;";
        var expected  = Decision.NOT_APPLICABLE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void indeterminateTarget() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit \"a\" < 5";
        var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void indeterminateCondition() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit where \"a\" < 5;";
        var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitDeny() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit" + " policy \"testp2\" deny";
        var expected  = Decision.PERMIT;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void notApplicableDeny() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit where false;"
                + " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";
        var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void multiplePermitTransformation() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit transform true"
                + " policy \"testp2\" permit transform false";
        var expected  = Decision.PERMIT;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitTransformationResource() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp1\" permit transform true"
                + " policy \"testp2\" permit transform false" + " policy \"testp3\" permit";
        var expected  = Optional.<JsonNode>of(JSON.booleanNode(true));
        validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void collectObligationDeny() {
        var policySet   = "set \"tests\" first-applicable" + " policy \"testp\" permit false"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\"";
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdviceDeny() {
        var policySet = "set \"tests\" first-applicable" + " policy \"testp\" permit false"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\"";
        var advice    = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

    @Test
    void collectObligationPermit() {
        var policySet   = "set \"tests\" first-applicable"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\"";
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdvicePermit() {
        var policySet = "set \"tests\" first-applicable"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\"";
        var advice    = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

}

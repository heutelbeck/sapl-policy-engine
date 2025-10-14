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
package io.sapl.interpreter.combinators;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static io.sapl.interpreter.combinators.CombinatorTestUtil.*;

class DenyOverridesTests {

    private static final JsonNodeFactory           JSON                                 = JsonNodeFactory.instance;
    private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION              = new AuthorizationSubscription(
            null, null, null, null);
    private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
            null, null, JSON.booleanNode(true), null);

    @Test
    void permit() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit";
        final var expected  = Decision.PERMIT;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void deny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void notApplicableTarget() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny true == false";
        final var expected  = Decision.NOT_APPLICABLE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void noDecisionsIsNotApplicable() {
        StepVerifier.create(DenyOverrides.denyOverrides(List.of()))
                .expectNextMatches(combinedDecision -> combinedDecision.getAuthorizationDecision()
                        .getDecision() == Decision.NOT_APPLICABLE)
                .verifyComplete();
    }

    @Test
    void notApplicableCondition() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny where true == false;";
        final var expected  = Decision.NOT_APPLICABLE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void indeterminateTarget() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit \"a\" < 5";
        final var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void indeterminateCondition() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit where \"a\" < 5;";
        final var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitDeny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit" + " policy \"testp2\" deny";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void denyIndeterminate() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" deny"
                + " policy \"testp2\" deny where \"a\" > 5;";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitNotApplicableDeny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
                + " policy \"testp2\" permit true == false" + " policy \"testp3\" deny";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitNotApplicableIndeterminateDeny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
                + " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
                + " policy \"testp4\" deny" + " policy \"testp5\" permit";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void permitIndeterminateNotApplicable() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
                + " policy \"testp2\" deny \"a\" < 5" + " policy \"testp3\" deny true == false";
        final var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void multiplePermitTransformation() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit transform false"
                + " policy \"testp2\" permit transform true";
        final var expected  = Decision.INDETERMINATE;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void multiplePermitTransformationDeny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
                + " policy \"testp2\" permit transform true" + " policy \"testp3\" deny";
        final var expected  = Decision.DENY;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void singlePermitTransformation() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit transform true";
        final var expected  = Decision.PERMIT;
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void singlePermitTransformationResource() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" permit transform true";
        validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, Optional.of(JSON.booleanNode(true)));
    }

    @Test
    void transformUncertaintyButItIsDenySoItIsJustDeny() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp\" deny transform true"
                + " policy \"testp2\" permit transform false";
        validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, Optional.of(JSON.booleanNode(true)));
    }

    @Test
    void multiplePermitNoTransformation() {
        final var policySet = "set \"tests\" deny-overrides" + " policy \"testp1\" permit"
                + " policy \"testp2\" permit";
        final var expected  = Decision.PERMIT;
        validateDecision(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, expected);
    }

    @Test
    void collectObligationDeny() {
        final var policySet = "set \"tests\" deny-overrides"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        final var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        obligations.add(JSON.textNode("obligation2"));

        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdviceDeny() {
        final var policySet = "set \"tests\" deny-overrides"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        ArrayNode advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        advice.add(JSON.textNode("advice2"));

        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

    @Test
    void collectObligationPermit() {
        final var policySet = "set \"tests\" deny-overrides"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

        ArrayNode obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        obligations.add(JSON.textNode("obligation2"));

        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdvicePermit() {
        final var policySet = "set \"tests\" deny-overrides"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

        ArrayNode advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        advice.add(JSON.textNode("advice2"));

        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

}

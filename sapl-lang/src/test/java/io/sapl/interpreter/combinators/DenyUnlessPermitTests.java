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

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.PERMIT;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateAdvice;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateDecision;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateObligations;
import static io.sapl.interpreter.combinators.CombinatorTestUtil.validateResource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import reactor.test.StepVerifier;

class DenyUnlessPermitTests {

    private static final JsonNodeFactory           JSON                                 = JsonNodeFactory.instance;
    private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION              = new AuthorizationSubscription(
            null, null, null, null);
    private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
            null, null, JSON.booleanNode(true), null);

    private static Stream<Arguments> documentTestCases() {
        // @formatter:off
		return Stream.of(
				// permit
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" permit", PERMIT),
				// deny
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" deny", DENY),
				// notApplicableTarget
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" permit true == false", DENY),
				// notApplicableCondition
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" permit where true == false;", DENY),
				// indeterminateTarget
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" permit \"a\" < 5", DENY),
				// indeterminateCondition
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp\" permit where \"a\" < 5;", DENY),
				// permitDeny
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp1\" permit policy \"testp2\" deny", PERMIT),
				// denyIndeterminate
				Arguments.of("set \"tests\" deny-unless-permit policy \"testp1\" deny policy \"testp2\" deny where \"a\" > 5;", DENY),
				// permitNotApplicableDeny
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" deny \"a\" > 5"
						   + " policy \"testp2\" permit true == false" + " policy \"testp3\" permit \"a\" > 5"
						   + " policy \"testp4\" deny"
						   + " policy \"testp5\" permit",
						     PERMIT),
				// permitNotApplicableIndeterminateDeny
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" deny \"a\" > 5"
						   + " policy \"testp2\" permit true == false"
				           + " policy \"testp3\" permit \"a\" > 5"
						   + " policy \"testp4\" deny"
				           + " policy \"testp5\" permit",
							 PERMIT),
				// permitIndeterminateNotApplicable
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" deny"
						   + " policy \"testp2\" deny \"a\" < 5"
				           + " policy \"testp3\" deny true == false",
							 DENY),
				// multiplePermitTransformationDeny
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" permit transform false"
						   + " policy \"testp2\" permit transform true"
				           + " policy \"testp3\" deny",
				             DENY),
				// singlePermitTransformation
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" permit transform true"
						   + " policy \"testp2\" deny",
						     PERMIT),
				// multiplePermitNoTransformation
				Arguments.of("set \"tests\" deny-unless-permit"
				           + " policy \"testp1\" permit"
						   + " policy \"testp2\" permit",
						     PERMIT)
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("documentTestCases")
    void validateDocumentEvaluationResult(String policyDefinition, Decision expected) {
        validateDecision(EMPTY_AUTH_SUBSCRIPTION, policyDefinition, expected);
    }

    @Test
    void singlePermitTransformationResource() {
        final var policySet = "set \"tests\" deny-unless-permit" + " policy \"testp\" permit transform true";
        final var expected  = Optional.<JsonNode>of(JSON.booleanNode(true));
        validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void noDecisionsIsDeny() {
        StepVerifier.create(DenyUnlessPermit.denyUnlessPermit(List.of()))
                .expectNextMatches(
                        combinedDecision -> combinedDecision.getAuthorizationDecision().getDecision() == Decision.DENY)
                .verifyComplete();
    }

    @Test
    void collectObligationDeny() {
        final var policySet = "set \"tests\" deny-unless-permit"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        final var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        obligations.add(JSON.textNode("obligation2"));

        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdviceDeny() {
        final var policySet = "set \"tests\" deny-unless-permit"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        final var advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        advice.add(JSON.textNode("advice2"));

        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

    @Test
    void collectObligationPermit() {
        final var policySet = "set \"tests\" deny-unless-permit"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

        final var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        obligations.add(JSON.textNode("obligation2"));

        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdvicePermit() {
        final var policySet = "set \"tests\" deny-unless-permit"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny where false; obligation \"obligation4\" advice \"advice4\"";

        final var advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        advice.add(JSON.textNode("advice2"));

        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

}

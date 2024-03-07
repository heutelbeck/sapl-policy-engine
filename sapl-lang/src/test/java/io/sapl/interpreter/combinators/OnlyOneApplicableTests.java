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
package io.sapl.interpreter.combinators;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
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

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.Document;
import io.sapl.prp.PolicyRetrievalResult;
import reactor.test.StepVerifier;

class OnlyOneApplicableTests {

    private static final DefaultSAPLInterpreter    INTERPRETER                          = new DefaultSAPLInterpreter();
    private static final JsonNodeFactory           JSON                                 = JsonNodeFactory.instance;
    private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION              = new AuthorizationSubscription(
            null, null, null, null);
    private static final AuthorizationSubscription AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE = new AuthorizationSubscription(
            null, null, JSON.booleanNode(true), null);

    @Test
    void combineDocumentsOneDeny() {
        var given    = mockPolicyRetrievalResult(false, denyPolicy(""));
        var expected = AuthorizationDecision.DENY;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void noDecisionsIsNotApplicable() {
        StepVerifier.create(OnlyOneApplicable.onlyOneApplicable(List.of()))
                .expectNextMatches(combinedDecision -> combinedDecision.getAuthorizationDecision()
                        .getDecision() == Decision.NOT_APPLICABLE)
                .verifyComplete();
    }

    @Test
    void combineDocumentsOnePermit() {
        var given    = mockPolicyRetrievalResult(false, permitPolicy(""));
        var expected = AuthorizationDecision.PERMIT;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void combineDocumentsOneIndeterminate() {
        var given    = mockPolicyRetrievalResult(false, indeterminatePolicy(""));
        var expected = AuthorizationDecision.INDETERMINATE;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void combineDocumentsOneNotApplicable() {
        var given    = mockPolicyRetrievalResult(false, notApplicablePolicy(""));
        var expected = AuthorizationDecision.NOT_APPLICABLE;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void combineDocumentsNoDocs() {
        var given    = mockPolicyRetrievalResult(false);
        var expected = AuthorizationDecision.NOT_APPLICABLE;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void combineDocumentsMoreDocsWithError() {
        var given    = mockPolicyRetrievalResult(true, denyPolicy(""), notApplicablePolicy(""), permitPolicy(""));
        var expected = AuthorizationDecision.INDETERMINATE;
        verifyDocumentsCombinator(given, expected);
    }

    @Test
    void combineDocumentsMoreDocs() {
        var given    = mockPolicyRetrievalResult(false, denyPolicy(""), notApplicablePolicy(""), permitPolicy(""));
        var expected = AuthorizationDecision.INDETERMINATE;
        verifyDocumentsCombinator(given, expected);
    }

    private String denyPolicy(String nameSuffix) {
        return "policy \"denies" + nameSuffix + "\" deny";
    }

    private String permitPolicy(String nameSuffix) {
        return "policy \"permits" + nameSuffix + "\" permit";
    }

    private String indeterminatePolicy(String nameSuffix) {
        return "policy \"indeterminate" + nameSuffix + "\" permit where (10/0);";
    }

    private String notApplicablePolicy(String nameSuffix) {
        return "policy \"notApplicable" + nameSuffix + "\" permit where false;";
    }

    private PolicyRetrievalResult mockPolicyRetrievalResult(boolean errorsInTarget, String... policies) {
        var result = new PolicyRetrievalResult();
        if (errorsInTarget)
            result = result.withError();
        for (var policy : policies) {
            var sapl = INTERPRETER.parse(policy);
            result = result.withMatch(new Document(sapl.getPolicyElement().getSaplName(), policy, sapl), Val.TRUE);
        }
        return result;
    }

    private void verifyDocumentsCombinator(PolicyRetrievalResult given, AuthorizationDecision expected) {
        StepVerifier
                .create(OnlyOneApplicable.onlyOneApplicable(given.getMatchingDocuments())
                        .map(CombinedDecision::getAuthorizationDecision)
                        .contextWrite(
                                ctx -> AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext()))
                        .contextWrite(
                                ctx -> AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext())))
                .expectNext(expected).verifyComplete();
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// collectWithError
			    Arguments.of(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" deny "
			    		   + " policy \"testp2\" permit where (10/0);",
			    		     INDETERMINATE),

				// collectWithError2
			    Arguments.of(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" deny where (10/0);"
			    	       + " policy \"testp2\" permit",
			    			 INDETERMINATE),

				// collectWithError3
			    Arguments.of(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" deny where (10/0);"
			    		   + " policy \"testp2\" permit where (10/0)",
			    			 INDETERMINATE),

				// permit
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" permit",
			                 PERMIT),

				// deny
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" deny",
			    		     DENY),

				// notApplicableTarget
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" deny true == false",
			    		     NOT_APPLICABLE),

				// notApplicableCondition
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" deny where true == false;",
			    		     NOT_APPLICABLE),

				// indeterminateTarget
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" permit \"a\" < 5",
			    		     INDETERMINATE),

				// indeterminateCondition
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" permit where \"a\" < 5;",
			    		     INDETERMINATE),

				// onePolicyMatching
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" deny false"
			    		   + " policy \"testp2\" permit true"
			               + " policy \"testp3\" permit false",
			    			 PERMIT),

				// twoPoliciesMatching1
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" permit"
			    		   + " policy \"testp2\" deny",
			    		     INDETERMINATE),

				// twoPoliciesMatching2
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" permit"
			    		   + " policy \"testp2\" permit",
			    		     INDETERMINATE),

				// twoPoliciesMatchingButOneNotApplicable
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp1\" permit"
			    		   + " policy \"testp2\" deny where false;",
			    			 PERMIT),

				// singlePermitTransformation
			    Arguments.of(EMPTY_AUTH_SUBSCRIPTION,
			    		     "set \"tests\" only-one-applicable"
			               + " policy \"testp\" permit transform true",
			    		     PERMIT)
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void validateDecisionTests(AuthorizationSubscription subscription, String document, Decision expectedDecision) {
        validateDecision(subscription, document, expectedDecision);
    }

    @Test
    void singlePermitTransformationResource() {
        var policySet = "set \"tests\" only-one-applicable" + " policy \"testp\" permit transform true";
        var expected  = Optional.<JsonNode>of(JSON.booleanNode(true));
        validateResource(EMPTY_AUTH_SUBSCRIPTION, policySet, expected);
    }

    @Test
    void collectObligationDeny() {
        var policySet = "set \"tests\" only-one-applicable"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdviceDeny() {
        var policySet = "set \"tests\" only-one-applicable"
                + " policy \"testp1\" deny obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" deny false obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" permit false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false obligation \"obligation4\" advice \"advice4\"";

        var advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

    @Test
    void collectObligationPermit() {
        var policySet = "set \"tests\" only-one-applicable"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode("obligation1"));
        validateObligations(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(obligations));
    }

    @Test
    void collectAdvicePermit() {
        var policySet = "set \"tests\" only-one-applicable"
                + " policy \"testp1\" permit obligation \"obligation1\" advice \"advice1\""
                + " policy \"testp2\" permit false obligation \"obligation2\" advice \"advice2\""
                + " policy \"testp3\" deny false obligation \"obligation3\" advice \"advice3\""
                + " policy \"testp4\" deny false where false; obligation \"obligation4\" advice \"advice4\"";

        var advice = JSON.arrayNode();
        advice.add(JSON.textNode("advice1"));
        validateAdvice(AUTH_SUBSCRIPTION_WITH_TRUE_RESOURCE, policySet, Optional.of(advice));
    }

}
